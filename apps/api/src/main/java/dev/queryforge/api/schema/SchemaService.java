package dev.queryforge.api.schema;

import java.util.List;

import dev.queryforge.api.connection.TargetDatabase;
import org.springframework.stereotype.Service;

/** Reads target-database schema metadata in a read-only, rolled-back transaction. */
@Service
public class SchemaService {

	private final TargetDatabase targetDatabase;

	private final SchemaReader schemaReader;

	public SchemaService(TargetDatabase targetDatabase, SchemaReader schemaReader) {
		this.targetDatabase = targetDatabase;
		this.schemaReader = schemaReader;
	}

	public SchemaResponse describe(List<String> tableNames) {
		return targetDatabase.inReadOnlyTransaction((connection) -> schemaReader.read(connection, tableNames));
	}

}
