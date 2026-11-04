package dev.queryforge.api.parsing;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.dialect.PostgresqlSqlDialect;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.babel.SqlBabelParserImpl;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.springframework.stereotype.Component;

/**
 * Admits exactly one read-only query (SELECT, WITH, VALUES, set operations).
 *
 * <p>This is the first of several safety layers: it rejects data-changing
 * statements and stacked statements before anything reaches the target database.
 * Calcite's Babel parser covers common PostgreSQL syntax; queries it cannot parse
 * are rejected with the parser's position rather than passed through unchecked.
 */
@Component
public class QueryParser {

	private static final SqlParser.Config PARSER_CONFIG = PostgresqlSqlDialect.DEFAULT
		.configureParser(SqlParser.config())
		.withParserFactory(SqlBabelParserImpl.FACTORY)
		.withConformance(SqlConformanceEnum.BABEL);

	private static final Pattern LEADING_EXPLAIN = Pattern.compile("(?i)^explain\\b");

	public ParsedQuery parse(String sql) {
		String statement = stripTrailingSemicolons(sql);
		// The Babel parser does not accept PostgreSQL's EXPLAIN options, so check before parsing.
		if (LEADING_EXPLAIN.matcher(statement).find()) {
			throw new UnsupportedQueryException("Submit the query without EXPLAIN; QueryForge adds it.", null, null);
		}
		SqlNode tree;
		try {
			tree = SqlParser.create(statement, PARSER_CONFIG).parseStmt();
		}
		catch (SqlParseException ex) {
			throw new UnsupportedQueryException(firstSentence(ex.getMessage()), ex.getPos().getLineNum(),
					ex.getPos().getColumnNum());
		}
		if (!tree.isA(SqlKind.QUERY)) {
			throw new UnsupportedQueryException("Only read-only queries (SELECT, WITH, VALUES) can be analyzed, not "
					+ tree.getKind().lowerName.toUpperCase(Locale.ROOT).replace('_', ' ') + ".", null, null);
		}
		return new ParsedQuery(statement, tree);
	}

	private static String stripTrailingSemicolons(String sql) {
		String statement = sql.strip();
		while (statement.endsWith(";")) {
			statement = statement.substring(0, statement.length() - 1).strip();
		}
		return statement;
	}

	// Calcite appends a long "Was expecting one of" list; the first sentence says what went wrong and where.
	private static String firstSentence(String message) {
		int expecting = message.indexOf("\nWas expecting");
		String summary = (expecting >= 0) ? message.substring(0, expecting) : message;
		return summary.strip();
	}

}
