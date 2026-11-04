package dev.queryforge.api.connection;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the database QueryForge analyzes.
 *
 * @param timeZone session time zone for analysis. The driver would otherwise use
 * the API host's zone, so the meaning of timestamp literals, and therefore plans
 * and row counts, would depend on where the API runs.
 */
@Validated
@ConfigurationProperties("queryforge.target")
public record TargetDatabaseProperties(
		@NotBlank String url,
		@NotBlank String username,
		@DefaultValue("") String password,
		@NotNull @DefaultValue("10s") Duration statementTimeout,
		@NotBlank @DefaultValue("UTC") String timeZone,
		@Min(1) @DefaultValue("4") int maxPoolSize) {
}
