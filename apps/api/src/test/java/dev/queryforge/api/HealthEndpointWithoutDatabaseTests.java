package dev.queryforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The API must keep running when PostgreSQL is unavailable and say so through
 * health, rather than failing to start. Port 1 refuses connections immediately.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:postgresql://localhost:1/unreachable")
@AutoConfigureMockMvc
class HealthEndpointWithoutDatabaseTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void reportsDatabaseDownWithServiceUnavailable() {
		MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

		assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("DOWN");
		assertThat(result).bodyJson().extractingPath("$.components.db.status").isEqualTo("DOWN");
	}

	@Test
	void staysLiveButNotReady() {
		assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatusOk();
		assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
	}

}
