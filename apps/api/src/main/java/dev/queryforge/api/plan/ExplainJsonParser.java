package dev.queryforge.api.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Parses the output of {@code EXPLAIN (FORMAT JSON)} into an {@link ExecutionPlan}. */
@Component
public class ExplainJsonParser {

	// Fields mapped to typed PlanNode components rather than kept as properties.
	private static final Set<String> TYPED_FIELDS = Set.of("Node Type", "Relation Name", "Alias", "Parent Relationship",
			"Startup Cost", "Total Cost", "Plan Rows", "Actual Rows", "Actual Loops", "Actual Startup Time",
			"Actual Total Time", "Plans");

	private final JsonMapper jsonMapper;

	public ExplainJsonParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public ExecutionPlan parse(String json) {
		JsonNode document = jsonMapper.readTree(json).path(0);
		JsonNode plan = document.path("Plan");
		if (!plan.isObject()) {
			throw new IllegalArgumentException("EXPLAIN output does not contain a plan");
		}
		PlanNode root = toNode(plan, new int[] { 0 });
		return new ExecutionPlan(root, optionalNumber(document, "Planning Time"),
				optionalNumber(document, "Execution Time"));
	}

	private PlanNode toNode(JsonNode json, int[] nextId) {
		int id = nextId[0]++;
		List<PlanNode> children = new ArrayList<>();
		for (JsonNode child : json.path("Plans").values()) {
			children.add(toNode(child, nextId));
		}
		PlanNode.Actuals actuals = json.has("Actual Loops")
				? new PlanNode.Actuals(json.path("Actual Rows").asDouble(), json.path("Actual Loops").asDouble(),
						json.path("Actual Startup Time").asDouble(), json.path("Actual Total Time").asDouble())
				: null;
		return new PlanNode(id, json.path("Node Type").asString(), optionalText(json, "Relation Name"),
				optionalText(json, "Alias"), optionalText(json, "Parent Relationship"),
				json.path("Startup Cost").asDouble(), json.path("Total Cost").asDouble(),
				json.path("Plan Rows").asDouble(), actuals, properties(json), children);
	}

	private static Map<String, Object> properties(JsonNode json) {
		Map<String, Object> properties = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> field : json.properties()) {
			if (TYPED_FIELDS.contains(field.getKey())) {
				continue;
			}
			Object value = scalarOrTextList(field.getValue());
			if (value != null) {
				properties.put(field.getKey(), value);
			}
		}
		return properties;
	}

	// Nested objects (per-worker details, for example) are not needed yet and are skipped.
	private static @Nullable Object scalarOrTextList(JsonNode value) {
		if (value.isNumber()) {
			return value.asDouble();
		}
		if (value.isBoolean()) {
			return value.asBoolean();
		}
		if (value.isString()) {
			return value.asString();
		}
		if (value.isArray() && value.values().stream().allMatch(JsonNode::isString)) {
			return value.values().stream().map(JsonNode::asString).toList();
		}
		return null;
	}

	private static @Nullable String optionalText(JsonNode json, String field) {
		JsonNode value = json.path(field);
		return value.isString() ? value.asString() : null;
	}

	private static @Nullable Double optionalNumber(JsonNode json, String field) {
		JsonNode value = json.path(field);
		return value.isNumber() ? value.asDouble() : null;
	}

}
