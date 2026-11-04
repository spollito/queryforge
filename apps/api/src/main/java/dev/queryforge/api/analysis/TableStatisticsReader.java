package dev.queryforge.api.analysis;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.queryforge.api.connection.TargetDatabaseException;
import org.springframework.stereotype.Component;

/** Reads row estimates and index definitions for the tables in a plan from the target's catalog. */
@Component
public class TableStatisticsReader {

	// Plain EXPLAIN output does not include schemas, so names resolve through the
	// connecting role's search_path, as they did when the query was planned.
	private static final String SQL = """
			SELECT r.name, c.reltuples, i.indexrelid::regclass::text AS index_name, a.attname AS leading_column
			FROM unnest(?::text[]) AS r(name)
			JOIN pg_class c ON c.oid = to_regclass(quote_ident(r.name))
			LEFT JOIN pg_index i ON i.indrelid = c.oid
			LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = i.indkey[0]
			ORDER BY r.name, index_name
			""";

	public Map<String, TableStatistics> read(Connection connection, Collection<String> tableNames) {
		if (tableNames.isEmpty()) {
			return Map.of();
		}
		Map<String, Double> rows = new HashMap<>();
		Map<String, List<TableStatistics.IndexSummary>> indexes = new HashMap<>();
		try (PreparedStatement statement = connection.prepareStatement(SQL)) {
			Array names = connection.createArrayOf("text", tableNames.toArray());
			statement.setArray(1, names);
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					String table = result.getString("name");
					rows.put(table, result.getDouble("reltuples"));
					List<TableStatistics.IndexSummary> tableIndexes = indexes.computeIfAbsent(table,
							(key) -> new ArrayList<>());
					String indexName = result.getString("index_name");
					if (indexName != null) {
						tableIndexes.add(new TableStatistics.IndexSummary(indexName, result.getString("leading_column")));
					}
				}
			}
		}
		catch (SQLException ex) {
			throw TargetDatabaseException.from(ex, 0);
		}
		Map<String, TableStatistics> statistics = new HashMap<>();
		rows.forEach((table, estimatedRows) -> statistics.put(table,
				new TableStatistics(table, estimatedRows, indexes.getOrDefault(table, List.of()))));
		return statistics;
	}

}
