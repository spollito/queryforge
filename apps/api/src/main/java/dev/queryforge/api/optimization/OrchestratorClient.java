package dev.queryforge.api.optimization;

import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.queryforge.api.plan.ExplainMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Calls the Python orchestrator's {@code POST /optimize}. On any transport error, timeout, or
 * non-2xx response it returns an empty result so the caller can fall back to Phase-1 analysis
 * (SPEC.md §10). The orchestrator's response is passed through verbatim as a {@link JsonNode}.
 */
@Component
class OrchestratorClient {

	private static final Logger log = LoggerFactory.getLogger(OrchestratorClient.class);

	private final RestClient restClient;

	OrchestratorClient(OrchestratorProperties properties) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
		factory.setReadTimeout((int) properties.timeout().toMillis());
		this.restClient = RestClient.builder().baseUrl(properties.url()).requestFactory(factory).build();
	}

	Optional<JsonNode> optimize(String sql, ExplainMode mode, int maxCandidates) {
		try {
			JsonNode body = restClient.post()
				.uri("/optimize")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new OptimizeRequest(sql, mode.name().toLowerCase(Locale.ROOT), maxCandidates))
				.retrieve()
				.body(JsonNode.class);
			return Optional.ofNullable(body);
		}
		catch (RestClientException ex) {
			// Includes connection refused, timeouts, and 4xx/5xx. Message may name a host, so
			// log it here and return a generic fallback to the client.
			log.warn("Orchestrator call failed, falling back to analysis: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private record OptimizeRequest(String sql, String mode, @JsonProperty("max_candidates") int maxCandidates) {
	}

}
