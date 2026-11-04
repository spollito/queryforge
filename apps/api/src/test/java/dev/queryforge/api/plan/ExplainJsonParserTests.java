package dev.queryforge.api.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExplainJsonParserTests {

	@Test
	void parsesEstimatedPlanTreeInPreOrder() {
		// SELECT id, status, total_cents FROM orders WHERE customer_id = 42
		ExecutionPlan plan = PlanFixtures.load("seq-scan-selective-filter");

		assertThat(plan.analyzed()).isFalse();
		assertThat(plan.executionTimeMs()).isNull();
		assertThat(plan.nodes()).extracting(PlanNode::id).containsExactly(0, 1);
		assertThat(plan.root().nodeType()).isEqualTo("Gather");
		assertThat(plan.root().number("Workers Planned")).hasValue(2);

		PlanNode scan = plan.root().children().getFirst();
		assertThat(scan.nodeType()).isEqualTo("Seq Scan");
		assertThat(scan.relationName()).isEqualTo("orders");
		assertThat(scan.parentRelationship()).isEqualTo("Outer");
		assertThat(scan.parallelAware()).isTrue();
		assertThat(scan.text("Filter")).hasValue("(customer_id = 42)");
		assertThat(scan.actuals()).isNull();
		assertThat(plan.parent(scan)).contains(plan.root());
	}

	@Test
	void parsesMeasurementsFromExplainAnalyze() {
		// SELECT id, customer_id, created_at FROM orders ORDER BY created_at DESC
		ExecutionPlan plan = PlanFixtures.load("sort-spill");

		assertThat(plan.analyzed()).isTrue();
		assertThat(plan.planningTimeMs()).isPositive();
		assertThat(plan.executionTimeMs()).isPositive();

		PlanNode sort = plan.root();
		assertThat(sort.actuals().rows()).isEqualTo(500_000);
		assertThat(sort.actuals().loops()).isEqualTo(1);
		assertThat(sort.textList("Sort Key")).containsExactly("created_at DESC");
		assertThat(sort.text("Sort Space Type")).hasValue("Disk");
		assertThat(sort.number("Sort Space Used")).hasValue(16_656);
	}

	@Test
	void keepsFractionalPerLoopRows() {
		// PostgreSQL 18 reports average rows per loop with two decimals.
		PlanNode scan = PlanFixtures.load("seq-scan-selective-filter-analyze").root().children().getFirst();

		assertThat(scan.actuals().rows()).isCloseTo(3.67, within(0.001));
		assertThat(scan.actuals().loops()).isEqualTo(3);
	}

	@Test
	void collectsRelationNames() {
		assertThat(PlanFixtures.load("hash-join").relationNames()).containsExactly("orders", "customers");
	}

	@Test
	void rejectsOutputWithoutPlan() {
		ExplainJsonParser parser = new ExplainJsonParser(JsonMapper.builder().build());

		assertThatIllegalArgumentException().isThrownBy(() -> parser.parse("[{}]"));
	}

}
