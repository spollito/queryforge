package dev.queryforge.api.optimization;

import java.util.Optional;

import dev.queryforge.api.analysis.AnalysisResponse;
import dev.queryforge.api.analysis.AnalysisRequest;
import dev.queryforge.api.analysis.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Optimizes a query through the Python orchestrator, falling back to Phase-1 analysis when the
 * orchestrator is disabled or unreachable (SPEC.md §10). The response always carries one of the
 * two, so a client gets a useful answer whether or not the orchestrator is available.
 */
@RestController
@RequestMapping("/api/v1/optimizations")
class OptimizationController {

	private final OrchestratorClient orchestratorClient;

	private final AnalysisService analysisService;

	private final OrchestratorProperties properties;

	OptimizationController(OrchestratorClient orchestratorClient, AnalysisService analysisService,
			OrchestratorProperties properties) {
		this.orchestratorClient = orchestratorClient;
		this.analysisService = analysisService;
		this.properties = properties;
	}

	@PostMapping
	OptimizationResponse optimize(@Valid @RequestBody AnalysisRequest request) {
		if (properties.enabled()) {
			Optional<JsonNode> result = orchestratorClient.optimize(request.sql(), request.modeOrDefault(),
					properties.maxCandidates());
			if (result.isPresent()) {
				return OptimizationResponse.fromOrchestrator(result.get());
			}
		}
		// Fallback: the Phase-1 analysis still parses, plans, and detects bottlenecks. If the
		// query itself is invalid this throws, mapped to the same problem detail as /analyses.
		AnalysisResponse analysis = AnalysisResponse.from(analysisService.analyze(request.sql(),
				request.modeOrDefault()));
		String reason = properties.enabled() ? "orchestrator-unavailable" : "orchestrator-disabled";
		return OptimizationResponse.fallback(reason, analysis);
	}

}
