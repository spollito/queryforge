package dev.queryforge.api.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Row accounting for parallel and repeated nodes, checked against real plans. */
class ExecutionPlanTests {

	@Test
	void parallelAwareScanSumsRowsAcrossProcesses() {
		// Gather with 2 workers; the scan reports 3.67 rows per process over 3 processes.
		ExecutionPlan plan = PlanFixtures.load("seq-scan-selective-filter-analyze");
		PlanNode scan = PlanFixtures.node(plan, 1);

		assertThat(plan.processes(scan)).isEqualTo(3);
		assertThat(plan.executions(scan)).isEqualTo(1);
		assertThat(plan.actualRowsTotal(scan)).isCloseTo(11, within(0.1));
		assertThat(plan.actualRowsTotal(plan.root())).isEqualTo(11);
	}

	@Test
	void parallelEstimateIsScaledByParallelDivisor() {
		// Estimate of 5 rows per process; with 2 workers the planner's divisor is 2.4.
		ExecutionPlan plan = PlanFixtures.load("seq-scan-selective-filter");

		assertThat(plan.estimatedRows(PlanFixtures.node(plan, 1))).isCloseTo(12, within(0.001));
	}

	@Test
	void nonParallelNodeInParallelSectionRepeatsWorkPerProcess() {
		// Every process hashes all 50,000 customers, so the distinct total is 50,000, not 150,000.
		ExecutionPlan plan = PlanFixtures.load("hash-join");
		PlanNode customersScan = plan.nodes()
			.stream()
			.filter((node) -> "customers".equals(node.relationName()))
			.findFirst()
			.orElseThrow();

		assertThat(customersScan.actuals().loops()).isEqualTo(3);
		assertThat(plan.actualRowsTotal(customersScan)).isEqualTo(50_000);
	}

	@Test
	void subPlanScanRunsOncePerOuterRow() {
		// SELECT c.id, (SELECT max(o.created_at) FROM orders o WHERE o.customer_id = c.id)
		// FROM customers c WHERE c.id <= 20
		ExecutionPlan plan = PlanFixtures.load("correlated-subquery");
		PlanNode ordersScan = PlanFixtures.node(plan, 2);

		assertThat(ordersScan.relationName()).isEqualTo("orders");
		assertThat(plan.executions(ordersScan)).isEqualTo(20);
	}

	@Test
	void selfSharesAreFractionsAndFindTheHotNode() {
		ExecutionPlan plan = PlanFixtures.load("sort-spill");

		var shares = PlanCosts.selfShares(plan);

		assertThat(shares.values()).allSatisfy((share) -> assertThat(share).isBetween(0.0, 1.0));
		assertThat(shares.get(0)).as("sort spilling to disk").isGreaterThan(shares.get(1));
	}

}
