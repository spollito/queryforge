package dev.queryforge.api.analysis.detector;

import static dev.queryforge.api.analysis.detector.DetectorTestSupport.ORDERS;
import static dev.queryforge.api.analysis.detector.DetectorTestSupport.context;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.queryforge.api.analysis.BottleneckDetector;
import dev.queryforge.api.analysis.Finding;
import dev.queryforge.api.analysis.Severity;
import dev.queryforge.api.analysis.TableStatistics;
import dev.queryforge.api.analysis.TableStatistics.IndexSummary;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Detectors run against real plans captured from the sample database. The query
 * behind each fixture is in the comment above its first use.
 */
class DetectorTests {

	private static final List<BottleneckDetector> ALL = List.of(new SelectiveSeqScanDetector(),
			new RepeatedSeqScanDetector(), new RowEstimateDetector(), new SortSpillDetector(), new HashSpillDetector());

	@ParameterizedTest
	@ValueSource(strings = { "hash-join" })
	void healthyPlanHasNoFindings(String fixture) {
		// SELECT c.country_code, o.status, sum(o.total_cents) FROM orders o
		// JOIN customers c ON c.id = o.customer_id GROUP BY c.country_code, o.status
		assertThat(ALL).flatExtracting((detector) -> detector.detect(context(fixture))).isEmpty();
	}

	@Nested
	class SelectiveSeqScan {

		private final SelectiveSeqScanDetector detector = new SelectiveSeqScanDetector();

		@Test
		void flagsEstimatedSelectiveParallelScanWithIndexSuggestion() {
			// SELECT id, status, total_cents FROM orders WHERE customer_id = 42
			List<Finding> findings = detector.detect(context("seq-scan-selective-filter"));

			assertThat(findings).singleElement().satisfies((finding) -> {
				assertThat(finding.rule()).isEqualTo(SelectiveSeqScanDetector.RULE);
				assertThat(finding.severity()).isEqualTo(Severity.HIGH);
				assertThat(finding.nodeId()).isEqualTo(1);
				assertThat(finding.title()).isEqualTo("Sequential scan on orders keeps 0.0024% of its rows");
				assertThat(finding.detail()).contains("about 500,000 rows and keeps 12");
				assertThat(finding.suggestion()).isEqualTo("CREATE INDEX CONCURRENTLY ON orders (customer_id);");
			});
		}

		@Test
		void usesMeasuredRowsWithExplainAnalyze() {
			List<Finding> findings = detector.detect(context("seq-scan-selective-filter-analyze"));

			assertThat(findings).singleElement()
				.satisfies((finding) -> assertThat(finding.detail()).contains("reads 500,000 rows and keeps 11"));
		}

		@Test
		void pointsToExistingIndexInsteadOfSuggestingDuplicate() {
			TableStatistics indexed = new TableStatistics("orders", 500_000,
					List.of(new IndexSummary("orders_customer_id_idx", "customer_id")));

			List<Finding> findings = detector.detect(context("seq-scan-selective-filter", indexed));

			assertThat(findings).singleElement().satisfies((finding) -> {
				assertThat(finding.detail()).contains("orders_customer_id_idx", "did not use it");
				assertThat(finding.suggestion()).isEqualTo("ANALYZE orders;");
			});
		}

		@Test
		void suggestsCompositeIndexForMultiColumnFilter() {
			// SELECT id, email FROM customers WHERE country_code = 'GB' AND created_at = '2023-01-05'
			List<Finding> findings = detector.detect(context("row-estimate-mismatch"));

			assertThat(findings).singleElement()
				.satisfies((finding) -> assertThat(finding.suggestion())
					.isEqualTo("CREATE INDEX CONCURRENTLY ON customers (country_code, created_at);"));
		}

		@Test
		void ignoresScansThatKeepMostRows() {
			// SELECT o.id, o.status, i.product_id, i.quantity FROM order_items i
			// JOIN orders o ON o.id = i.order_id WHERE i.quantity > 1   (keeps 75%)
			assertThat(detector.detect(context("hash-spill"))).isEmpty();
		}

		@Test
		void leavesRepeatedScansToRepeatedScanDetector() {
			assertThat(detector.detect(context("correlated-subquery"))).isEmpty();
		}

		@Test
		void skipsTablesWithUnknownRowCountInEstimateMode() {
			TableStatistics neverAnalyzed = new TableStatistics("orders", -1, List.of());

			assertThat(detector.detect(context("seq-scan-selective-filter", neverAnalyzed))).isEmpty();
		}

	}

	@Nested
	class RepeatedSeqScan {

		private final RepeatedSeqScanDetector detector = new RepeatedSeqScanDetector();

		@Test
		void flagsCorrelatedSubqueryScanningPerOuterRow() {
			// SELECT c.id, (SELECT max(o.created_at) FROM orders o WHERE o.customer_id = c.id) AS last_order
			// FROM customers c WHERE c.id <= 20
			List<Finding> findings = detector.detect(context("correlated-subquery"));

			assertThat(findings).singleElement().satisfies((finding) -> {
				assertThat(finding.rule()).isEqualTo(RepeatedSeqScanDetector.RULE);
				assertThat(finding.severity()).isEqualTo(Severity.HIGH);
				assertThat(finding.nodeId()).isEqualTo(2);
				assertThat(finding.title()).isEqualTo("orders is scanned sequentially 20 times");
				assertThat(finding.detail()).contains("correlated subquery", "10,000,000 rows", "LATERAL");
				assertThat(finding.suggestion()).isEqualTo("CREATE INDEX CONCURRENTLY ON orders (customer_id);");
			});
		}

		@Test
		void ignoresScansThatRunOnce() {
			assertThat(detector.detect(context("seq-scan-selective-filter-analyze"))).isEmpty();
			assertThat(detector.detect(context("hash-join"))).isEmpty();
		}

		@Test
		void ignoresSmallTables() {
			TableStatistics tiny = new TableStatistics("orders", 500, List.of());

			assertThat(detector.detect(context("correlated-subquery", tiny))).isEmpty();
		}

	}

	@Nested
	class RowEstimate {

		private final RowEstimateDetector detector = new RowEstimateDetector();

		@Test
		void flagsCorrelatedColumnsAndSuggestsExtendedStatistics() {
			List<Finding> findings = detector.detect(context("row-estimate-mismatch"));

			assertThat(findings).singleElement().satisfies((finding) -> {
				assertThat(finding.rule()).isEqualTo(RowEstimateDetector.RULE);
				assertThat(finding.severity()).isEqualTo(Severity.MEDIUM);
				assertThat(finding.title()).isEqualTo("Row estimate is 10× too low at Seq Scan on customers");
				assertThat(finding.detail()).contains("expected 5 rows and got 50");
				assertThat(finding.suggestion())
					.isEqualTo("CREATE STATISTICS ON country_code, created_at FROM customers;\nANALYZE customers;");
			});
		}

		@Test
		void needsMeasurements() {
			assertThat(detector.detect(context("seq-scan-selective-filter"))).isEmpty();
		}

		@Test
		void toleratesSmallDeviations() {
			// Gather Merge expected 67 rows and got 84.
			assertThat(detector.detect(context("hash-join"))).isEmpty();
		}

	}

	@Nested
	class SortSpill {

		@Test
		void flagsSortWrittenToDisk() {
			// SELECT id, customer_id, created_at FROM orders ORDER BY created_at DESC
			List<Finding> findings = new SortSpillDetector().detect(context("sort-spill", ORDERS));

			assertThat(findings).singleElement().satisfies((finding) -> {
				assertThat(finding.nodeId()).isZero();
				assertThat(finding.title()).isEqualTo("Sort spilled 16 MB to disk");
				assertThat(finding.detail()).contains("500,000 rows by created_at DESC", "external merge");
				assertThat(finding.suggestion()).startsWith("SET LOCAL work_mem = '64MB';");
			});
		}

		@Test
		void ignoresInMemorySorts() {
			assertThat(new SortSpillDetector().detect(context("hash-join"))).isEmpty();
		}

		@Test
		void suggestsPowerOfTwoWorkMemWithFloor() {
			assertThat(SortSpillDetector.suggestedWorkMemMb(100)).isEqualTo(16);
			assertThat(SortSpillDetector.suggestedWorkMemMb(16_656)).isEqualTo(64);
		}

	}

	@Nested
	class HashSpill {

		@Test
		void flagsHashJoinProcessedInBatches() {
			List<Finding> findings = new HashSpillDetector().detect(context("hash-spill"));

			assertThat(findings).singleElement().satisfies((finding) -> {
				assertThat(finding.nodeId()).isEqualTo(2);
				assertThat(finding.title()).isEqualTo("Hash table spilled to disk in 4 batches");
			});
		}

		@Test
		void ignoresSingleBatchHashes() {
			assertThat(new HashSpillDetector().detect(context("hash-join"))).isEmpty();
		}

	}

}
