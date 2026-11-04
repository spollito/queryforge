package dev.queryforge.api.analysis;

import org.jspecify.annotations.Nullable;

/**
 * A bottleneck found in an execution plan.
 *
 * @param rule stable identifier of the detector that produced the finding
 * @param nodeId the plan node the finding is about
 * @param title one-line summary
 * @param detail what the plan shows and why it is slow
 * @param suggestion what to try, often SQL; {@code null} when there is no specific advice
 */
public record Finding(String rule, Severity severity, int nodeId, String title, String detail,
		@Nullable String suggestion) {
}
