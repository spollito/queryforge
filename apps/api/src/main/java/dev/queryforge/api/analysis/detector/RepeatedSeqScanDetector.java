package dev.queryforge.api.analysis.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

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
 * A sequential scan that runs once per outer row: on the inner side of a Nested
 * Loop, or inside a correlated subquery (SubPlan). Its cost grows with the product
 * of both inputs.
 */
@Component
public class RepeatedSeqScanDetector implements BottleneckDetector {

	public static final String RULE = "repeated-seq-scan";

	static final double MIN_TABLE_ROWS = 1_000;

	static final double MIN_ROWS_READ = 10_000;

	@Override
	public List<Finding> detect(AnalysisContext context) {
		ExecutionPlan plan = context.plan();
		List<Finding> findings = new ArrayList<>();
		for (PlanNode node : plan.nodes()) {
			if (!node.is("Seq Scan") || node.relationName() == null) {
				continue;
			}
			Optional<TableStatistics> table = context.table(node);
			if (table.isEmpty() || !table.get().rowCountKnown() || table.get().estimatedRows() < MIN_TABLE_ROWS) {
				continue;
			}
			OptionalDouble repetitions = plan.analyzed() ? OptionalDouble.of(plan.executions(node))
					: context.estimatedRepetitions(node);
			if (repetitions.isEmpty() || repetitions.getAsDouble() < 2) {
				continue;
			}
			double rowsRead = repetitions.getAsDouble() * table.get().estimatedRows();
			if (rowsRead < MIN_ROWS_READ) {
				continue;
			}
			findings.add(finding(context, node, repetitions.getAsDouble(), table.get(), rowsRead, plan.analyzed()));
		}
		return findings;
	}

	private static Finding finding(AnalysisContext context, PlanNode node, double repetitions, TableStatistics table,
			double rowsRead, boolean measured) {
		String relation = node.relationName();
		boolean inSubPlan = isInSubPlan(context, node);
		Severity severity = (rowsRead >= 1_000_000) ? Severity.HIGH : Severity.MEDIUM;
		String title = relation + " is scanned sequentially " + (measured ? "" : "about ") + Formats.rows(repetitions)
				+ " times";
		String detail = "The scan runs once for every row of "
				+ (inSubPlan ? "the outer query, because it is inside a correlated subquery"
						: "the outer side of a nested loop")
				+ ". Each run reads the whole table (about " + Formats.rows(table.estimatedRows())
				+ " rows), so the query reads roughly " + Formats.rows(rowsRead) + " rows in total.";

		List<String> columns = node.text("Filter")
			.map(FilterPredicates::of)
			.map(FilterPredicates::indexColumns)
			.orElse(List.of());
		String rewrite = inSubPlan
				? " Rewriting the subquery as a join, or as a LATERAL join with aggregation, also lets the planner "
						+ "choose a hash or merge join."
				: "";
		if (columns.isEmpty() || table.indexLeadingWith(columns.getFirst()).isPresent()) {
			return new Finding(RULE, severity, node.id(), title, detail + rewrite, null);
		}
		return new Finding(RULE, severity, node.id(), title,
				detail + " An index on " + Formats.identifiers(columns) + " turns each run into an index lookup."
						+ rewrite,
				"CREATE INDEX CONCURRENTLY ON " + Formats.identifier(relation) + " (" + Formats.identifiers(columns)
						+ ");");
	}

	private static boolean isInSubPlan(AnalysisContext context, PlanNode node) {
		for (PlanNode current = node; current != null; current = context.plan().parent(current).orElse(null)) {
			if ("SubPlan".equals(current.parentRelationship())) {
				return true;
			}
		}
		return false;
	}

}
