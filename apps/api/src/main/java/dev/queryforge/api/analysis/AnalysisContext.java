package dev.queryforge.api.analysis;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import dev.queryforge.api.plan.ExecutionPlan;
import dev.queryforge.api.plan.PlanNode;

/** What detectors see: the plan plus catalog statistics for the tables it reads. */
public record AnalysisContext(ExecutionPlan plan, Map<String, TableStatistics> tables) {

	public AnalysisContext {
		tables = Map.copyOf(tables);
	}

	public Optional<TableStatistics> table(PlanNode node) {
		return Optional.ofNullable(node.relationName()).map(tables::get);
	}

	/**
	 * How many times the planner expects {@code node} to run, judging by plan
	 * structure alone: once, unless it sits on the inner side of a Nested Loop or
	 * inside a SubPlan. A Materialize or Memoize node in between stores rows, so the
	 * node below it runs once. Empty when the node runs once.
	 */
	public OptionalDouble estimatedRepetitions(PlanNode node) {
		PlanNode child = node;
		for (PlanNode parent = plan.parent(node).orElse(null); parent != null;
				child = parent, parent = plan.parent(parent).orElse(null)) {
			if (child.is("Materialize") || child.is("Memoize")) {
				return OptionalDouble.empty();
			}
			if ("SubPlan".equals(child.parentRelationship())) {
				return OptionalDouble.of(plan.estimatedRows(parent));
			}
			if (parent.is("Nested Loop") && parent.children().size() == 2 && parent.children().get(1) == child) {
				return OptionalDouble.of(plan.estimatedRows(parent.children().getFirst()));
			}
		}
		return OptionalDouble.empty();
	}

}
