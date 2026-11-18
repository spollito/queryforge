package dev.queryforge.api.schema;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Serves structured schema metadata for the orchestrator's retrieval step. */
@RestController
@RequestMapping("/api/v1/schema")
class SchemaController {

	private final SchemaService schemaService;

	SchemaController(SchemaService schemaService) {
		this.schemaService = schemaService;
	}

	/**
	 * @param tables optional comma-separated table names; all base tables when omitted
	 */
	@GetMapping
	SchemaResponse schema(@RequestParam(required = false) @Nullable String tables) {
		return schemaService.describe(parseTables(tables));
	}

	private static List<String> parseTables(@Nullable String tables) {
		if (tables == null || tables.isBlank()) {
			return List.of();
		}
		return Arrays.stream(tables.split(",")).map(String::trim).filter((name) -> !name.isEmpty()).toList();
	}

}
