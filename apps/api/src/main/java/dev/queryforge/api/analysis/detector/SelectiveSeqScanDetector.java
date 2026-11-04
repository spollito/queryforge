package dev.queryforge.api.analysis.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.queryforge.api.analysis.AnalysisContext;
import dev.queryforge.api.analysis.BottleneckDetector;
import dev.queryforge.api.analysis.FilterPredicates;
import dev.queryforge.api.analysis.Finding;
import dev.queryforge.api.analysis.Formats;
import dev.queryforge.api.analysis.Severity;
import dev.queryforge.api.analysis.TableStatistics;
import dev.queryforge.api.plan.ExecutionPlan;
import dev.queryforge.api.plan.PlanNode;
import org.springframework.stereotype.Component;

/**
 * A sequential scan that reads a large table to keep a small fraction of it.
 * Scans that run repeatedly are left to {@link RepeatedSeqScanDetector}.
 */
@Component
public class SelectiveSeqScanDetector implements BottleneckDetector {

	public static final String RULE = "seq-scan-selective-filter";

	static final double MIN_ROWS_SCANNED = 10_000;

	static final double MAX_SELECTIVITY = 0.05;

	@Override
	public List<Finding> detect(AnalysisContext context) {
		ExecutionPlan plan = context.plan();
		List<Finding> findings = new ArrayList<>();
		for (PlanNode node : plan.nodes()) {
			Optional<String> filter = node.text("Filter");
			if (!node.is("Seq Scan") || filter.isEmpty() || node.relationName() == null) {
				continue;
			}
			if (plan.analyzed() ? plan.executions(node) > 1 : context.estimatedRepetitions(node).isPresent()) {
				continue;
			}
			Optional<TableStatistics> table = context.table(node);
			double kept;
			double scanned;
			if (plan.analyzed()) {
				kept = plan.actualRowsTotal(node);
				scanned = kept + plan.perLoopPropertyTotal(node, "Rows Removed by Filter");
			}
			else if (table.isPresent() && table.get().rowCountKnown()) {
				kept = plan.estimatedRows(node);
				scanned = table.get().estimatedRows();
			}
			else {
				continue;
			}
			double selectivity = kept / Math.max(scanned, 1);
			if (scanned < MIN_ROWS_SCANNED || selectivity > MAX_SELECTIVITY) {
				continue;
			}
			findings.add(finding(node, filter.get(), table, kept, scanned, selectivity, plan.analyzed()));
		}
		return findings;
	}

	private static Finding finding(PlanNode node, String filter, Optional<TableStatistics> table, double kept,
			double scanned, double selectivity, boolean measured) {
		String relation = node.relationName();
		Severity severity = (scanned >= 100_000 && selectivity <= 0.01) ? Severity.HIGH : Severity.MEDIUM;
		String title = "Sequential scan on " + relation + " keeps " + Formats.percent(selectivity) + " of its rows";
		String detail = "The scan reads " + (measured ? "" : "about ") + Formats.rows(scanned) + " rows and keeps "
				+ Formats.rows(kept) + " because of the filter " + filter + ".";

		FilterPredicates predicates = FilterPredicates.of(filter);
		if (predicates.isEmpty()) {
			return new Finding(RULE, severity, node.id(), title,
					detail + " The filter compares expressions rather than plain columns, so an expression index "
							+ "matching it, or rewriting the condition to compare a column directly, would let "
							+ "PostgreSQL avoid reading every row.",
					null);
		}
		List<String> columns = predicates.indexColumns();
		Optional<TableStatistics.IndexSummary> existing = table
			.flatMap((statistics) -> statistics.indexLeadingWith(columns.getFirst()));
		if (existing.isPresent()) {
			return new Finding(RULE, severity, node.id(), title,
					detail + " Index " + existing.get().name() + " starts with " + columns.getFirst()
							+ ", but the planner did not use it. Check that statistics are current and that the "
							+ "condition compares the column without casts or functions.",
					"ANALYZE " + Formats.identifier(relation) + ";");
		}
		return new Finding(RULE, severity, node.id(), title,
				detail + " An index on the filtered " + (columns.size() == 1 ? "column" : "columns")
						+ " lets PostgreSQL read only the matching rows.",
				"CREATE INDEX CONCURRENTLY ON " + Formats.identifier(relation) + " (" + Formats.identifiers(columns)
						+ ");");
	}

}
