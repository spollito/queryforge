package dev.queryforge.api.analysis;

import java.util.List;

/**
 * Looks for one kind of bottleneck in an execution plan. Implementations are
 * stateless Spring components; each reports at most one finding per node.
 */
public interface BottleneckDetector {

	List<Finding> detect(AnalysisContext context);

}
