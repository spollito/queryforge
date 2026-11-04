package dev.queryforge.api.plan;

import java.util.HashMap;
import java.util.Map;

/**
 * Each node's own share of the plan's total: time with EXPLAIN ANALYZE, estimated
 * cost otherwise. PostgreSQL reports inclusive figures, so a node's own share is
 * its inclusive figure minus its children's. Shares are approximate: PostgreSQL's
 * cost accounting for subplans and parallel sections does not add up exactly.
 */
public final class PlanCosts {

	private PlanCosts() {
	}

	/** Node id to self share in the range [0, 1]. */
	public static Map<Integer, Double> selfShares(ExecutionPlan plan) {
		double total = inclusive(plan, plan.root());
		Map<Integer, Double> shares = new HashMap<>();
		for (PlanNode node : plan.nodes()) {
			double self = inclusive(plan, node);
			for (PlanNode child : node.children()) {
				self -= inclusive(plan, child);
			}
			shares.put(node.id(), (total > 0) ? Math.clamp(self / total, 0.0, 1.0) : 0.0);
		}
		return shares;
	}

	private static double inclusive(ExecutionPlan plan, PlanNode node) {
		PlanNode.Actuals actuals = node.actuals();
		return (actuals != null) ? actuals.totalTimeMs() * plan.executions(node) : node.totalCost();
	}

}
