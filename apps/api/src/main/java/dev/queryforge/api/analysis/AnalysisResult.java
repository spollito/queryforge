package dev.queryforge.api.analysis;

import java.util.List;
import java.util.Map;

import dev.queryforge.api.plan.ExecutionPlan;
import dev.queryforge.api.plan.ExplainMode;

/**
 * @param selfShares node id to the node's own share of total time or cost, see
 * {@link dev.queryforge.api.plan.PlanCosts}
 */
public record AnalysisResult(ExplainMode mode, ExecutionPlan plan, Map<Integer, Double> selfShares,
		List<Finding> findings) {
}
