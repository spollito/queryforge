package dev.queryforge.api.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.queryforge.api.plan.ExecutionPlan;
import dev.queryforge.api.plan.ExplainMode;
import dev.queryforge.api.plan.PlanNode;
import org.jspecify.annotations.Nullable;

/** JSON shape of an analysis. Plan nodes carry the details a reader needs, not every EXPLAIN property. */
public record AnalysisResponse(ExplainMode mode, @Nullable Double planningTimeMs, @Nullable Double executionTimeMs,
		Node plan, List<Finding> findings) {

	// Shown in this order when present. Values PostgreSQL reports in kB get a unit.
	private static final List<String> DETAIL_PROPERTIES = List.of("Join Type", "Strategy", "Index Name",
			"Scan Direction", "Index Cond", "Recheck Cond", "Hash Cond", "Merge Cond", "Join Filter", "Filter",
			"Rows Removed by Filter", "Rows Removed by Join Filter", "Sort Key", "Sort Method", "Sort Space Used",
			"Group Key", "Hash Batches", "Original Hash Batches", "Peak Memory Usage", "HashAgg Batches",
			"Disk Usage", "Workers Planned", "Workers Launched", "Subplan Name", "CTE Name", "Function Name");

	private static final List<String> KILOBYTE_PROPERTIES = List.of("Sort Space Used", "Peak Memory Usage",
			"Disk Usage");

	/**
	 * @param selfShare the node's own share of total time (execute mode) or cost (estimate mode), 0 to 1
	 */
	public record Node(int id, String nodeType, boolean parallelAware, @Nullable String relationName,
			@Nullable String alias, @Nullable String parentRelationship, double startupCost, double totalCost,
			double planRows, @Nullable Double actualRows, @Nullable Double actualLoops,
			@Nullable Double actualTotalTimeMs, double selfShare, Map<String, String> details, List<Node> children) {
	}

	public static AnalysisResponse from(AnalysisResult result) {
		ExecutionPlan plan = result.plan();
		return new AnalysisResponse(result.mode(), plan.planningTimeMs(), plan.executionTimeMs(),
				node(plan.root(), result.selfShares()), result.findings());
	}

	private static Node node(PlanNode node, Map<Integer, Double> selfShares) {
		PlanNode.Actuals actuals = node.actuals();
		List<Node> children = node.children().stream().map((child) -> node(child, selfShares)).toList();
		return new Node(node.id(), node.nodeType(), node.parallelAware(), node.relationName(), node.alias(),
				node.parentRelationship(), node.startupCost(), node.totalCost(), node.planRows(),
				(actuals != null) ? actuals.rows() : null, (actuals != null) ? actuals.loops() : null,
				(actuals != null) ? actuals.totalTimeMs() : null, selfShares.getOrDefault(node.id(), 0.0),
				details(node), children);
	}

	private static Map<String, String> details(PlanNode node) {
		Map<String, String> details = new LinkedHashMap<>();
		for (String property : DETAIL_PROPERTIES) {
			Object value = node.properties().get(property);
			if (value == null) {
				continue;
			}
			String text = switch (value) {
				case List<?> list -> String.join(", ", list.stream().map(String::valueOf).toList());
				case Double number -> number(number);
				default -> value.toString();
			};
			details.put(property, KILOBYTE_PROPERTIES.contains(property) ? text + " kB" : text);
		}
		return details;
	}

	private static String number(double value) {
		return (value == Math.rint(value)) ? String.format(Locale.ROOT, "%,d", (long) value)
				: String.format(Locale.ROOT, "%,.2f", value);
	}

}
