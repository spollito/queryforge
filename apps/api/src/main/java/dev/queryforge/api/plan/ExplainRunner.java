package dev.queryforge.api.plan;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import dev.queryforge.api.connection.TargetDatabaseException;
import dev.queryforge.api.parsing.ParsedQuery;
import org.springframework.stereotype.Component;

/** Captures the execution plan of a validated query on a target connection. */
@Component
public class ExplainRunner {

	private final ExplainJsonParser parser;

	public ExplainRunner(ExplainJsonParser parser) {
		this.parser = parser;
	}

	/**
	 * Must run inside {@code TargetDatabase.inReadOnlyTransaction}: with
	 * {@link ExplainMode#EXECUTE} the query really runs.
	 */
	public ExecutionPlan explain(Connection connection, ParsedQuery query, ExplainMode mode) {
		// EXPLAIN cannot take the query as a bind parameter. The text is safe to
		// append because QueryParser admitted exactly one read-only query.
		String prefix = "EXPLAIN (" + mode.explainOptions() + ") ";
		try (Statement statement = connection.createStatement()) {
			statement.setEscapeProcessing(false);
			try (ResultSet result = statement.executeQuery(prefix + query.sql())) {
				result.next();
				return parser.parse(result.getString(1));
			}
		}
		catch (SQLException ex) {
			throw TargetDatabaseException.from(ex, prefix.length());
		}
	}

}
