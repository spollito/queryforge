package dev.queryforge.api.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import dev.queryforge.api.SampleDatabaseConfiguration;
import dev.queryforge.api.TestcontainersConfiguration;
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

/**
 * The optimization endpoint with the orchestrator disabled: it falls back to the Phase-1
 * analysis, so the endpoint is useful before the Python service is deployed.
 */
@SpringBootTest(properties = { "queryforge.target.statement-timeout=2s",
		"queryforge.orchestrator.enabled=false" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, SampleDatabaseConfiguration.class })
class OptimizationApiTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JsonMapper jsonMapper;

	private MvcTestResult optimize(String sql) {
		record Request(String sql, String mode) {
		}
		return mvc.post()
			.uri("/api/v1/optimizations")
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonMapper.writeValueAsString(new Request(sql, "estimate")))
			.exchange();
	}

	@Test
	void fallsBackToAnalysisWhenOrchestratorDisabled() {
		MvcTestResult result = optimize("SELECT id, status FROM orders WHERE customer_id = 42");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.orchestratorAvailable").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$.fallbackReason").isEqualTo("orchestrator-disabled");
		assertThat(result).bodyJson().extractingPath("$.optimization").isNull();
		assertThat(result).bodyJson().extractingPath("$.analysisFallback.plan.relationName").isEqualTo("orders");
	}

	@Test
	void rejectsUnsupportedQueryThroughFallback() {
		MvcTestResult result = optimize("DELETE FROM orders");

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("unsupported-query");
	}

}
