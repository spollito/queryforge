package dev.queryforge.api.analysis;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analyses")
class AnalysisController {

	private final AnalysisService analysisService;

	AnalysisController(AnalysisService analysisService) {
		this.analysisService = analysisService;
	}

	/** Analyzes a query and returns its plan and findings. Nothing is stored yet, so this answers 200, not 201. */
	@PostMapping
	AnalysisResponse analyze(@Valid @RequestBody AnalysisRequest request) {
		return AnalysisResponse.from(analysisService.analyze(request.sql(), request.modeOrDefault()));
	}

}
