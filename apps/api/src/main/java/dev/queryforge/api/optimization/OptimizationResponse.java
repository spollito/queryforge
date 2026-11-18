package dev.queryforge.api.optimization;

import dev.queryforge.api.analysis.AnalysisResponse;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Envelope for {@code POST /api/v1/optimizations}. Either the orchestrator's optimization
 * result is present, or — when the orchestrator is disabled or unreachable — a Phase-1
 * analysis is returned as a safe fallback, so the endpoint always responds usefully.
 */
public record OptimizationResponse(boolean orchestratorAvailable, @Nullable JsonNode optimization,
		@Nullable String fallbackReason, @Nullable AnalysisResponse analysisFallback) {

	static OptimizationResponse fromOrchestrator(JsonNode optimization) {
		return new OptimizationResponse(true, optimization, null, null);
	}

	static OptimizationResponse fallback(String reason, AnalysisResponse analysis) {
		return new OptimizationResponse(false, null, reason, analysis);
	}

}
