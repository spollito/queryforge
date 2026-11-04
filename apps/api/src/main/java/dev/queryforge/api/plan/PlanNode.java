package dev.queryforge.api.plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import org.jspecify.annotations.Nullable;

/**
 * One node of a PostgreSQL execution plan. Fields every node has are typed;
 * everything else PostgreSQL reports for the node type (Filter, Sort Key,
 * Hash Batches, ...) is kept in {@code properties} under its EXPLAIN name, as a
 * {@link String}, {@link Double}, {@link Boolean}, or list of strings.
 *
 * @param id position of the node in pre-order, starting at 0 for the root
 */
public record PlanNode(
		int id,
		String nodeType,
		@Nullable String relationName,
		@Nullable String alias,
		@Nullable String parentRelationship,
		double startupCost,
		double totalCost,
		double planRows,
		@Nullable Actuals actuals,
		Map<String, Object> properties,
		List<PlanNode> children) {

	public PlanNode {
		properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
		children = List.copyOf(children);
	}

	/**
	 * Measurements from EXPLAIN ANALYZE. As in PostgreSQL's output, rows and
	 * times are averages per loop.
	 */
	public record Actuals(double rows, double loops, double startupTimeMs, double totalTimeMs) {
	}

	public boolean is(String type) {
		return nodeType.equals(type);
	}

	public boolean parallelAware() {
		return Boolean.TRUE.equals(properties.get("Parallel Aware"));
	}

	public Optional<String> text(String property) {
		return (properties.get(property) instanceof String value) ? Optional.of(value) : Optional.empty();
	}

	public OptionalDouble number(String property) {
		return (properties.get(property) instanceof Double value) ? OptionalDouble.of(value) : OptionalDouble.empty();
	}

	@SuppressWarnings("unchecked")
	public List<String> textList(String property) {
		return (properties.get(property) instanceof List<?> values) ? (List<String>) values : List.of();
	}

}
