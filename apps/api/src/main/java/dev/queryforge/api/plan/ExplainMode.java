package dev.queryforge.api.plan;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ExplainMode {

	/** Plain EXPLAIN: the planner's estimates. The query is not run. */
	ESTIMATE("FORMAT JSON"),

	/** EXPLAIN ANALYZE: runs the query to measure actual rows, time, and buffers. */
	EXECUTE("ANALYZE, BUFFERS, FORMAT JSON");

	private final String explainOptions;

	ExplainMode(String explainOptions) {
		this.explainOptions = explainOptions;
	}

	public String explainOptions() {
		return explainOptions;
	}

	@JsonValue
	public String json() {
		return name().toLowerCase(Locale.ROOT);
	}

}
