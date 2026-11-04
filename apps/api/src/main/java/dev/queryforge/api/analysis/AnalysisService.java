package dev.queryforge.api.analysis;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.queryforge.api.connection.TargetDatabase;
import dev.queryforge.api.parsing.ParsedQuery;
import dev.queryforge.api.parsing.QueryParser;
import dev.queryforge.api.plan.ExecutionPlan;
import dev.queryforge.api.plan.ExplainMode;
import dev.queryforge.api.plan.ExplainRunner;
import dev.queryforge.api.plan.PlanCosts;
import org.springframework.stereotype.Service;

/** Validates a query, captures its plan on the target database, and runs every bottleneck detector. */
@Service
public class AnalysisService {

	private static final Comparator<Finding> MOST_SEVERE_FIRST = Comparator.comparing(Finding::severity)
		.thenComparingInt(Finding::nodeId);

	private final QueryParser queryParser;

	private final TargetDatabase targetDatabase;

	private final ExplainRunner explainRunner;

	private final TableStatisticsReader statisticsReader;

	private final List<BottleneckDetector> detectors;

	public AnalysisService(QueryParser queryParser, TargetDatabase targetDatabase, ExplainRunner explainRunner,
			TableStatisticsReader statisticsReader, List<BottleneckDetector> detectors) {
		this.queryParser = queryParser;
		this.targetDatabase = targetDatabase;
		this.explainRunner = explainRunner;
		this.statisticsReader = statisticsReader;
		this.detectors = List.copyOf(detectors);
	}

	public AnalysisResult analyze(String sql, ExplainMode mode) {
		ParsedQuery query = queryParser.parse(sql);
		return targetDatabase.inReadOnlyTransaction((connection) -> {
			ExecutionPlan plan = explainRunner.explain(connection, query, mode);
			Map<String, TableStatistics> tables = statisticsReader.read(connection, plan.relationNames());
			AnalysisContext context = new AnalysisContext(plan, tables);
			List<Finding> findings = detectors.stream()
				.flatMap((detector) -> detector.detect(context).stream())
				.sorted(MOST_SEVERE_FIRST)
				.toList();
			return new AnalysisResult(mode, plan, PlanCosts.selfShares(plan), findings);
		});
	}

}
