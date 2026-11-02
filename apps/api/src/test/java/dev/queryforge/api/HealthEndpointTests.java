package dev.queryforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Verifies the health contract the web client and future deployment probes rely on,
 * against a real PostgreSQL instance.
 */
@SpringBootTest(properties = "management.endpoints.web.cors.allowed-origins=" + HealthEndpointTests.WEB_ORIGIN)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthEndpointTests {

	static final String WEB_ORIGIN = "http://localhost:5173";

	@Autowired
	private MockMvcTester mvc;

	@Test
	void reportsUpWhenPostgresIsReachable() {
		MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UP");
		assertThat(result).bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");
	}

	@Test
	void hidesComponentDetails() {
		assertThat(mvc.get().uri("/actuator/health")).bodyJson()
			.hasPath("$.components.db")
			.doesNotHavePath("$.components.db.details");
	}

	@Test
	void readinessProbeIncludesDatabase() {
		MvcTestResult result = mvc.get().uri("/actuator/health/readiness").exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");
	}

	@Test
	void livenessProbeDoesNotDependOnDatabase() {
		MvcTestResult result = mvc.get().uri("/actuator/health/liveness").exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().doesNotHavePath("$.components.db");
	}

	@Test
	void allowsCrossOriginHealthChecksFromWebClient() {
		assertThat(mvc.options()
			.uri("/actuator/health")
			.header(HttpHeaders.ORIGIN, WEB_ORIGIN)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.hasStatusOk()
			.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN);
	}

	@Test
	void rejectsCrossOriginRequestsFromUnknownOrigins() {
		assertThat(mvc.options()
			.uri("/actuator/health")
			.header(HttpHeaders.ORIGIN, "https://unknown.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.hasStatus(HttpStatus.FORBIDDEN)
			.doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
	}

}
