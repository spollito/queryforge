package dev.queryforge.api.analysis.detector;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.queryforge.api.analysis.AnalysisContext;
import dev.queryforge.api.analysis.TableStatistics;
import dev.queryforge.api.analysis.TableStatistics.IndexSummary;
import dev.queryforge.api.plan.PlanFixtures;

/** Catalog statistics matching the sample database at scale 1, where the fixtures were captured. */
final class DetectorTestSupport {

	static final TableStatistics ORDERS = new TableStatistics("orders", 500_000,
			List.of(new IndexSummary("orders_pkey", "id")));

	static final TableStatistics CUSTOMERS = new TableStatistics("customers", 50_000,
			List.of(new IndexSummary("customers_email_key", "email"), new IndexSummary("customers_pkey", "id")));

	static final TableStatistics ORDER_ITEMS = new TableStatistics("order_items", 1_000_001,
			List.of(new IndexSummary("order_items_pkey", "order_id")));

	private DetectorTestSupport() {
	}

	static AnalysisContext context(String fixture, TableStatistics... tables) {
		Map<String, TableStatistics> byName = Arrays.stream(tables)
			.collect(Collectors.toMap(TableStatistics::name, Function.identity()));
		return new AnalysisContext(PlanFixtures.load(fixture), byName);
	}

	static AnalysisContext context(String fixture) {
		return context(fixture, ORDERS, CUSTOMERS, ORDER_ITEMS);
	}

}
