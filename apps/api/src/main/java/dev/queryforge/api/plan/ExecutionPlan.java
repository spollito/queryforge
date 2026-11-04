package dev.queryforge.api.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * A parsed execution plan with navigation helpers and the row-accounting rules
 * PostgreSQL applies to parallel and repeated nodes.
 */
public final class ExecutionPlan {

	private final PlanNode root;

	private final @Nullable Double planningTimeMs;

	private final @Nullable Double executionTimeMs;

	private final List<PlanNode> nodes = new ArrayList<>();

	private final Map<Integer, PlanNode> parents = new HashMap<>();

	public ExecutionPlan(PlanNode root, @Nullable Double planningTimeMs, @Nullable Double executionTimeMs) {
		this.root = root;
		this.planningTimeMs = planningTimeMs;
		this.executionTimeMs = executionTimeMs;
		index(root);
	}

	private void index(PlanNode node) {
		nodes.add(node);
		for (PlanNode child : node.children()) {
			parents.put(child.id(), node);
			index(child);
		}
	}

	public PlanNode root() {
		return root;
	}

	public @Nullable Double planningTimeMs() {
		return planningTimeMs;
	}

	public @Nullable Double executionTimeMs() {
		return executionTimeMs;
	}

	/** Whether the plan came from EXPLAIN ANALYZE and carries actual measurements. */
	public boolean analyzed() {
		return root.actuals() != null;
	}

	/** All nodes in pre-order (root first). */
	public List<PlanNode> nodes() {
		return List.copyOf(nodes);
	}

	public Optional<PlanNode> parent(PlanNode node) {
		return Optional.ofNullable(parents.get(node.id()));
	}

	public Set<String> relationNames() {
		Set<String> names = new LinkedHashSet<>();
		for (PlanNode node : nodes) {
			if (node.relationName() != null) {
				names.add(node.relationName());
			}
		}
		return names;
	}

	/**
	 * Number of processes running the node: 1 outside a parallel section, or the
	 * workers of the enclosing Gather plus the participating leader.
	 */
	public int processes(PlanNode node) {
		for (PlanNode ancestor = parents.get(node.id()); ancestor != null; ancestor = parents.get(ancestor.id())) {
			if (ancestor.is("Gather") || ancestor.is("Gather Merge")) {
				double workers = ancestor.number("Workers Launched")
					.orElse(ancestor.number("Workers Planned").orElse(0));
				return (int) workers + 1;
			}
		}
		return 1;
	}

	/**
	 * How many times the node ran (EXPLAIN ANALYZE only). PostgreSQL counts each
	 * parallel process as a loop, so loops are divided by the process count.
	 */
	public double executions(PlanNode node) {
		PlanNode.Actuals actuals = node.actuals();
		return (actuals != null) ? actuals.loops() / processes(node) : 1;
	}

	/**
	 * Distinct rows the node produced across all its executions (EXPLAIN ANALYZE
	 * only). Parallel-aware nodes split their input between processes; other nodes
	 * in a parallel section repeat the same work in every process.
	 */
	public double actualRowsTotal(PlanNode node) {
		PlanNode.Actuals actuals = node.actuals();
		if (actuals == null) {
			return 0;
		}
		return node.parallelAware() ? actuals.rows() * actuals.loops() : actuals.rows() * executions(node);
	}

	/** Same accounting as {@link #actualRowsTotal} for a per-loop average property such as "Rows Removed by Filter". */
	public double perLoopPropertyTotal(PlanNode node, String property) {
		double perLoop = node.number(property).orElse(0);
		PlanNode.Actuals actuals = node.actuals();
		if (actuals == null) {
			return 0;
		}
		return node.parallelAware() ? perLoop * actuals.loops() : perLoop * executions(node);
	}

	/**
	 * Estimated rows per execution. For parallel-aware nodes PostgreSQL reports the
	 * estimate per process, so it is scaled by the planner's parallel divisor.
	 */
	public double estimatedRows(PlanNode node) {
		return node.parallelAware() ? node.planRows() * parallelDivisor(processes(node) - 1) : node.planRows();
	}

	// Mirrors get_parallel_divisor() in PostgreSQL's costsize.c, assuming the leader participates.
	private static double parallelDivisor(int workers) {
		double leaderContribution = 1.0 - 0.3 * workers;
		return workers + Math.max(0, leaderContribution);
	}

}
