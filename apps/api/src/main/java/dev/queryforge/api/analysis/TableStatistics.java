package dev.queryforge.api.analysis;

import java.util.List;
import java.util.Optional;

/**
 * Catalog facts about a table referenced by a plan.
 *
 * @param estimatedRows {@code pg_class.reltuples}; negative when the table has never been analyzed
 */
public record TableStatistics(String name, double estimatedRows, List<IndexSummary> indexes) {

	public TableStatistics {
		indexes = List.copyOf(indexes);
	}

	/** An index and the first column of its key ({@code null} for expression indexes). */
	public record IndexSummary(String name, String leadingColumn) {
	}

	public boolean rowCountKnown() {
		return estimatedRows >= 0;
	}

	public Optional<IndexSummary> indexLeadingWith(String column) {
		return indexes.stream().filter((index) -> column.equals(index.leadingColumn())).findFirst();
	}

}
