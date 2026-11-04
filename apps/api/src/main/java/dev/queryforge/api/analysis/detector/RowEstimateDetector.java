package dev.queryforge.api.analysis.detector;

import java.util.ArrayList;
import java.util.List;

import dev.queryforge.api.analysis.AnalysisContext;
import dev.queryforge.api.analysis.BottleneckDetector;
import dev.queryforge.api.analysis.FilterPredicates;
import dev.queryforge.api.analysis.Finding;
import dev.queryforge.api.analysis.Formats;
import dev.queryforge.api.analysis.Severity;
import dev.queryforge.api.plan.ExecutionPlan;
import dev.queryforge.api.plan.PlanNode;
import org.springframework.stereotype.Component;

/**
 * Nodes whose row estimate is far from the measured count (EXPLAIN ANALYZE only).
 * A wrong estimate propagates upward, so only the node where it originates (no
 * misestimated child) is reported.
 */
@Component
public class RowEstimateDetector implements BottleneckDetector {

	public static final String RULE = "row-estimate-mismatch";

	static final double MIN_RATIO = 10;

	static final double MIN_ROWS = 50;

	@Override
	public List<Finding> detect(AnalysisContext context) {
		ExecutionPlan plan = context.plan();
		if (!plan.analyzed()) {
			return List.of();
		}
		List<Finding> findings = new ArrayList<>();
		for (PlanNode node : plan.nodes()) {
			if (isMisestimated(node) && node.children().stream().noneMatch(RowEstimateDetector::isMisestimated)) {
				findings.add(finding(node));
			}
		}
		return findings;
	}

	private static boolean isMisestimated(PlanNode node) {
		PlanNode.Actuals actuals = node.actuals();
		if (actuals == null || actuals.loops() == 0) {
			return false;
		}
		return Math.max(node.planRows(), actuals.rows()) >= MIN_ROWS && ratio(node) >= MIN_RATIO;
	}

	// Both figures are per loop, so they compare directly. PostgreSQL never estimates fewer than one row.
	private static double ratio(PlanNode node) {
		double estimated = Math.max(node.planRows(), 1);
		double actual = Math.max(node.actuals().rows(), 1);
		return Math.max(estimated, actual) / Math.min(estimated, actual);
	}

	private static Finding finding(PlanNode node) {
		double ratio = ratio(node);
		double estimated = node.planRows();
		double actual = node.actuals().rows();
		Severity severity = (ratio >= 100) ? Severity.HIGH : Severity.MEDIUM;
		String where = node.nodeType() + (node.relationName() != null ? " on " + node.relationName() : "");
		String title = "Row estimate is " + Formats.times(ratio) + " too " + (actual > estimated ? "low" : "high")
				+ " at " + where;
		String detail = "The planner expected " + Formats.rows(estimated) + " rows and got " + Formats.rows(actual)
				+ ". Plans built on wrong estimates pick the wrong join methods, join order, and memory sizes.";

		List<String> columns = node.text("Filter")
			.map(FilterPredicates::of)
			.map(FilterPredicates::allColumns)
			.orElse(List.of());
		String relation = node.relationName();
		if (relation != null && columns.size() >= 2) {
			return new Finding(RULE, severity, node.id(), title,
					detail + " The filter combines conditions on " + Formats.identifiers(columns)
							+ ", and PostgreSQL assumes such conditions are independent. Extended statistics "
							+ "teach it how the columns correlate.",
					"CREATE STATISTICS ON " + Formats.identifiers(columns) + " FROM " + Formats.identifier(relation)
							+ ";\nANALYZE " + Formats.identifier(relation) + ";");
		}
		if (relation != null) {
			return new Finding(RULE, severity, node.id(), title,
					detail + " Statistics for " + relation + " may be stale.",
					"ANALYZE " + Formats.identifier(relation) + ";");
		}
		return new Finding(RULE, severity, node.id(), title,
				detail + " Check the join and filter conditions at this node; correlated join columns may need "
						+ "extended statistics.",
				null);
	}

}
