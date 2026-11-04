package dev.queryforge.api.analysis.detector;

import java.util.ArrayList;
import java.util.List;

import dev.queryforge.api.analysis.AnalysisContext;
import dev.queryforge.api.analysis.BottleneckDetector;
import dev.queryforge.api.analysis.Finding;
import dev.queryforge.api.analysis.Formats;
import dev.queryforge.api.analysis.Severity;
import dev.queryforge.api.plan.PlanNode;
import org.springframework.stereotype.Component;

/** A sort that did not fit in {@code work_mem} and used temporary files (EXPLAIN ANALYZE only). */
@Component
public class SortSpillDetector implements BottleneckDetector {

	public static final String RULE = "sort-spill";

	@Override
	public List<Finding> detect(AnalysisContext context) {
		List<Finding> findings = new ArrayList<>();
		for (PlanNode node : context.plan().nodes()) {
			if (node.is("Sort") && "Disk".equals(node.text("Sort Space Type").orElse(null))) {
				findings.add(finding(node, context.plan().actualRowsTotal(node)));
			}
		}
		return findings;
	}

	private static Finding finding(PlanNode node, double rows) {
		double spilledKb = node.number("Sort Space Used").orElse(0);
		String sortKey = String.join(", ", node.textList("Sort Key"));
		String detail = "Sorting " + Formats.rows(rows) + " rows" + (sortKey.isEmpty() ? "" : " by " + sortKey)
				+ " needed more memory than work_mem allows, so PostgreSQL wrote " + Formats.kilobytes(spilledKb)
				+ " to temporary files (" + node.text("Sort Method").orElse("external sort")
				+ "). An in-memory sort usually needs a few times the on-disk size. An index matching the sort key "
				+ "would return rows already in order and avoid the sort entirely.";
		return new Finding(RULE, Severity.MEDIUM, node.id(), "Sort spilled " + Formats.kilobytes(spilledKb) + " to disk",
				detail, "SET LOCAL work_mem = '" + suggestedWorkMemMb(spilledKb) + "MB';  -- in the query's transaction");
	}

	// Three times the spilled size, rounded up to a power of two, as a starting point to measure from.
	static long suggestedWorkMemMb(double spilledKb) {
		double neededMb = Math.max(16, spilledKb * 3 / 1024);
		return Long.highestOneBit((long) Math.ceil(neededMb) - 1) << 1;
	}

}
