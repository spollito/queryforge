package dev.queryforge.api.analysis;

import dev.queryforge.api.plan.ExplainMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * @param mode {@code estimate} (default) plans the query without running it;
 * {@code execute} runs it with EXPLAIN ANALYZE
 */
public record AnalysisRequest(@NotBlank @Size(max = 20_000) String sql, @Nullable ExplainMode mode) {

	public ExplainMode modeOrDefault() {
		return (mode != null) ? mode : ExplainMode.ESTIMATE;
	}

}
