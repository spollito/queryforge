package dev.queryforge.api.schema;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Structured schema metadata for the orchestrator's retrieval step (SPEC.md §5, §10).
 *
 * <p>Field names are rendered in {@code snake_case} to match the Python orchestrator's
 * {@code SchemaContext} model. This carries only metadata — table, column, key, and index
 * definitions and a row estimate — never any row data.
 */
public record SchemaResponse(List<Table> tables) {

	public record Table(String name, List<Column> columns, @JsonProperty("primary_key") List<String> primaryKey,
			@JsonProperty("foreign_keys") Map<String, String> foreignKeys, List<Index> indexes,
			@JsonProperty("estimated_rows") @Nullable Double estimatedRows) {
	}

	public record Column(String name, @JsonProperty("data_type") String dataType, boolean nullable) {
	}

	public record Index(String name, List<String> columns, boolean unique) {
	}

}
