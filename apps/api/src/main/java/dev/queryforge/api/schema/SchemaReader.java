package dev.queryforge.api.schema;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.queryforge.api.connection.TargetDatabaseException;
import org.springframework.stereotype.Component;

/**
 * Reads structured schema metadata from the target's catalog: columns and types, primary and
 * foreign keys, secondary indexes, and a row estimate. Runs inside the read-only transaction,
 * like plan capture, so the orchestrator never connects to a database itself (SPEC.md §10).
 *
 * <p>Tables resolve through the connecting role's {@code search_path}. With no names given,
 * all base tables in the current schemas are returned.
 */
@Component
public class SchemaReader {

	private static final String TABLES_ALL = """
			SELECT c.oid, c.relname, c.reltuples
			FROM pg_class c
			JOIN pg_namespace n ON n.oid = c.relnamespace
			WHERE c.relkind IN ('r', 'p') AND n.nspname = ANY (current_schemas(false))
			ORDER BY c.relname
			""";

	private static final String TABLES_FILTERED = """
			SELECT c.oid, c.relname, c.reltuples
			FROM pg_class c
			JOIN pg_namespace n ON n.oid = c.relnamespace
			WHERE c.relkind IN ('r', 'p') AND n.nspname = ANY (current_schemas(false))
			  AND c.relname = ANY (?::text[])
			ORDER BY c.relname
			""";

	private static final String COLUMNS = """
			SELECT a.attrelid, a.attname, format_type(a.atttypid, a.atttypmod) AS data_type, a.attnotnull
			FROM pg_attribute a
			WHERE a.attrelid = ANY (?::oid[]) AND a.attnum > 0 AND NOT a.attisdropped
			ORDER BY a.attrelid, a.attnum
			""";

	private static final String PRIMARY_KEYS = """
			SELECT i.indrelid, a.attname
			FROM pg_index i
			JOIN LATERAL unnest(i.indkey) WITH ORDINALITY AS k(attnum, ord) ON true
			JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum
			WHERE i.indisprimary AND i.indrelid = ANY (?::oid[])
			ORDER BY i.indrelid, k.ord
			""";

	private static final String FOREIGN_KEYS = """
			SELECT con.conrelid, att.attname AS column_name, cl.relname AS ref_table, ratt.attname AS ref_column
			FROM pg_constraint con
			JOIN LATERAL unnest(con.conkey, con.confkey) WITH ORDINALITY AS k(conattnum, confattnum, ord) ON true
			JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = k.conattnum
			JOIN pg_class cl ON cl.oid = con.confrelid
			JOIN pg_attribute ratt ON ratt.attrelid = con.confrelid AND ratt.attnum = k.confattnum
			WHERE con.contype = 'f' AND con.conrelid = ANY (?::oid[])
			ORDER BY con.conrelid, k.ord
			""";

	private static final String INDEXES = """
			SELECT i.indrelid, ic.relname AS index_name, i.indisunique,
			  (SELECT array_agg(a.attname ORDER BY k.ord)
			   FROM unnest(i.indkey) WITH ORDINALITY AS k(attnum, ord)
			   JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum
			   WHERE k.attnum <> 0) AS columns
			FROM pg_index i
			JOIN pg_class ic ON ic.oid = i.indexrelid
			WHERE i.indrelid = ANY (?::oid[]) AND NOT i.indisprimary
			ORDER BY i.indrelid, index_name
			""";

	/** Mutable per-table accumulator while the five queries are stitched together. */
	private static final class Accumulator {

		private final String name;

		private final Double estimatedRows;

		private final List<SchemaResponse.Column> columns = new ArrayList<>();

		private final List<String> primaryKey = new ArrayList<>();

		private final Map<String, String> foreignKeys = new LinkedHashMap<>();

		private final List<SchemaResponse.Index> indexes = new ArrayList<>();

		private Accumulator(String name, double reltuples) {
			this.name = name;
			// pg_class.reltuples is negative when the table has never been analyzed.
			this.estimatedRows = (reltuples >= 0) ? reltuples : null;
		}

		private SchemaResponse.Table toTable() {
			return new SchemaResponse.Table(name, columns, primaryKey, foreignKeys, indexes, estimatedRows);
		}

	}

	public SchemaResponse read(Connection connection, Collection<String> tableNames) {
		try {
			Map<Long, Accumulator> byOid = resolveTables(connection, tableNames);
			if (byOid.isEmpty()) {
				return new SchemaResponse(List.of());
			}
			readColumns(connection, byOid);
			readPrimaryKeys(connection, byOid);
			readForeignKeys(connection, byOid);
			readIndexes(connection, byOid);
			return new SchemaResponse(byOid.values().stream().map(Accumulator::toTable).toList());
		}
		catch (SQLException ex) {
			throw TargetDatabaseException.from(ex, 0);
		}
	}

	private Map<Long, Accumulator> resolveTables(Connection connection, Collection<String> tableNames)
			throws SQLException {
		Map<Long, Accumulator> byOid = new LinkedHashMap<>();
		boolean filtered = !tableNames.isEmpty();
		try (PreparedStatement statement = connection.prepareStatement(filtered ? TABLES_FILTERED : TABLES_ALL)) {
			if (filtered) {
				statement.setArray(1, connection.createArrayOf("text", tableNames.toArray()));
			}
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					byOid.put(result.getLong("oid"),
							new Accumulator(result.getString("relname"), result.getDouble("reltuples")));
				}
			}
		}
		return byOid;
	}

	private void readColumns(Connection connection, Map<Long, Accumulator> byOid) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(COLUMNS)) {
			statement.setArray(1, oidArray(connection, byOid.keySet()));
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					Accumulator table = byOid.get(result.getLong("attrelid"));
					if (table != null) {
						table.columns.add(new SchemaResponse.Column(result.getString("attname"),
								result.getString("data_type"), !result.getBoolean("attnotnull")));
					}
				}
			}
		}
	}

	private void readPrimaryKeys(Connection connection, Map<Long, Accumulator> byOid) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(PRIMARY_KEYS)) {
			statement.setArray(1, oidArray(connection, byOid.keySet()));
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					Accumulator table = byOid.get(result.getLong("indrelid"));
					if (table != null) {
						table.primaryKey.add(result.getString("attname"));
					}
				}
			}
		}
	}

	private void readForeignKeys(Connection connection, Map<Long, Accumulator> byOid) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(FOREIGN_KEYS)) {
			statement.setArray(1, oidArray(connection, byOid.keySet()));
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					Accumulator table = byOid.get(result.getLong("conrelid"));
					if (table != null) {
						table.foreignKeys.put(result.getString("column_name"),
								result.getString("ref_table") + "." + result.getString("ref_column"));
					}
				}
			}
		}
	}

	private void readIndexes(Connection connection, Map<Long, Accumulator> byOid) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INDEXES)) {
			statement.setArray(1, oidArray(connection, byOid.keySet()));
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					Accumulator table = byOid.get(result.getLong("indrelid"));
					if (table == null) {
						continue;
					}
					Array columns = result.getArray("columns");
					List<String> columnNames = (columns != null) ? List.of((String[]) columns.getArray()) : List.of();
					table.indexes.add(new SchemaResponse.Index(result.getString("index_name"), columnNames,
							result.getBoolean("indisunique")));
				}
			}
		}
	}

	// oids are read back from the catalog as bigint and cast to oid[] in each query.
	private static Array oidArray(Connection connection, Collection<Long> oids) throws SQLException {
		return connection.createArrayOf("bigint", oids.toArray());
	}

}
