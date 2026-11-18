package dev.queryforge.api.optimization;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the Python orchestrator service (SPEC.md §10).
 *
 * @param enabled when false, {@code /api/v1/optimizations} skips the orchestrator and always
 * serves the Phase-1 analysis fallback — useful before the service is deployed
 */
@Validated
@ConfigurationProperties("queryforge.orchestrator")
public record OrchestratorProperties(
		@NotBlank @DefaultValue("http://localhost:8000") String url,
		@NotNull @DefaultValue("30s") Duration timeout,
		@NotNull @DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("true") boolean enabled,
		@Min(1) @DefaultValue("3") int maxCandidates) {
}
