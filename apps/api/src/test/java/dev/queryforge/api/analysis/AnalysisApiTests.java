package dev.queryforge.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.queryforge.api.SampleDatabaseConfiguration;
import dev.queryforge.api.TestcontainersConfiguration;
import dev.queryforge.api.analysis.detector.RepeatedSeqScanDetector;
import dev.queryforge.api.analysis.detector.SelectiveSeqScanDetector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** End to end: HTTP request, Calcite validation, EXPLAIN on a real seeded PostgreSQL, detectors, JSON. */
@SpringBootTest(properties = "queryforge.target.statement-timeout=2s")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, SampleDatabaseConfiguration.class })
class AnalysisApiTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JsonMapper jsonMapper;

	private MvcTestResult analyze(String sql, String mode) {
		record Request(String sql, String mode) {
		}
		return mvc.post()
			.uri("/api/v1/analyses")
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonMapper.writeValueAsString(new Request(sql, mode)))
			.exchange();
	}

	@Test
	void estimatesPlanAndSuggestsIndexForSelectiveScan() {
		MvcTestResult result = analyze("SELECT id, status FROM orders WHERE customer_id = 42", "estimate");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.mode").isEqualTo("estimate");
		assertThat(result).bodyJson().extractingPath("$.executionTimeMs").isNull();
		assertThat(result).bodyJson().extractingPath("$.plan.nodeType").isEqualTo("Seq Scan");
		assertThat(result).bodyJson().extractingPath("$.plan.relationName").isEqualTo("orders");
		assertThat(result).bodyJson().extractingPath("$.plan.details.Filter").isEqualTo("(customer_id = 42)");
		assertThat(result).bodyJson().extractingPath("$.findings[0].rule").isEqualTo(SelectiveSeqScanDetector.RULE);
		assertThat(result).bodyJson()
			.extractingPath("$.findings[0].suggestion")
			.isEqualTo("CREATE INDEX CONCURRENTLY ON orders (customer_id);");
	}

	@Test
	void executeModeReturnsMeasurements() {
		MvcTestResult result = analyze(
				"SELECT c.id, (SELECT max(o.created_at) FROM orders o WHERE o.customer_id = c.id) FROM customers c "
						+ "WHERE c.id <= 20;",
				"execute");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.mode").isEqualTo("execute");
		assertThat(result).bodyJson().extractingPath("$.executionTimeMs").asNumber().isNotNull();
		assertThat(result).bodyJson().extractingPath("$.plan.actualLoops").isEqualTo(1.0);
		assertThat(result).bodyJson().extractingPath("$.findings[0].rule").isEqualTo(RepeatedSeqScanDetector.RULE);
	}

	@Test
	void defaultsToEstimateMode() {
		assertThat(mvc.post().uri("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON).content("""
				{"sql": "SELECT 1"}
				""")).hasStatusOk().bodyJson().extractingPath("$.mode").isEqualTo("estimate");
	}

	@Test
	void rejectsDataChangingStatementBeforeReachingDatabase() {
		MvcTestResult result = analyze("DELETE FROM orders", "execute");

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("unsupported-query");
	}

	@Test
	void reportsPostgresErrorsWithPositionInSubmittedQuery() {
		MvcTestResult result = analyze("SELECT nope FROM orders", "estimate");

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("query-error");
		assertThat(result).bodyJson().extractingPath("$.sqlState").isEqualTo("42703");
		assertThat(result).bodyJson().extractingPath("$.position").isEqualTo(8);
	}

	@Test
	void stopsQueriesAtStatementTimeout() {
		MvcTestResult result = analyze("SELECT pg_sleep(5)", "execute");

		assertThat(result).hasStatus(HttpStatus.GATEWAY_TIMEOUT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("statement-timeout");
	}

	@Test
	void validatesRequestFields() {
		MvcTestResult result = analyze(" ", "estimate");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("invalid-request");
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("sql");
	}

}
