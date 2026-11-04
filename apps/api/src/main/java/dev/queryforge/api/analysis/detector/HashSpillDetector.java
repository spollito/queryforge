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

/**
 * A hash join or hash aggregate whose hash table did not fit in memory and was
 * processed in batches through temporary files (EXPLAIN ANALYZE only).
 */
@Component
public class HashSpillDetector implements BottleneckDetector {

	public static final String RULE = "hash-spill";

	private static final String ADVICE = " Raising work_mem (or hash_mem_multiplier) lets the hash table fit in "
			+ "memory. Sending fewer rows or narrower rows into the hash, by filtering earlier or selecting fewer "
			+ "columns, also shrinks it.";

	@Override
	public List<Finding> detect(AnalysisContext context) {
		List<Finding> findings = new ArrayList<>();
		for (PlanNode node : context.plan().nodes()) {
			double batches = node.number(node.is("Hash") ? "Hash Batches" : "HashAgg Batches").orElse(1);
			if (batches > 1 && (node.is("Hash") || node.is("Aggregate"))) {
				findings.add(node.is("Hash") ? hashJoin(node, batches) : hashAggregate(node, batches));
			}
		}
		return findings;
	}

	private static Finding hashJoin(PlanNode node, double batches) {
		double originalBatches = node.number("Original Hash Batches").orElse(batches);
		String growth = (originalBatches < batches)
				? " The planner planned " + Formats.rows(originalBatches)
						+ ", so the input was larger than estimated."
				: "";
		String detail = "The hash table for the join was split into " + Formats.rows(batches)
				+ " batches because it did not fit in memory. Batches other than the first are written to temporary "
				+ "files and read back." + growth + ADVICE;
		return new Finding(RULE, Severity.MEDIUM, node.id(),
				"Hash table spilled to disk in " + Formats.rows(batches) + " batches", detail, null);
	}

	private static Finding hashAggregate(PlanNode node, double batches) {
		double diskKb = node.number("Disk Usage").orElse(0);
		String detail = "The hash aggregate used " + Formats.rows(batches) + " batches and wrote "
				+ Formats.kilobytes(diskKb) + " to temporary files because its groups did not fit in memory."
				+ ADVICE;
		return new Finding(RULE, Severity.MEDIUM, node.id(), "Hash aggregate spilled " + Formats.kilobytes(diskKb)
				+ " to disk", detail, null);
	}

}
