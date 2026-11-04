package dev.queryforge.api.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the columns a scan's Filter compares against constants or outer values,
 * to suggest indexes. This reads PostgreSQL's deparsed filter text, so it is a
 * heuristic: in plain EXPLAIN output a scan's own columns are unqualified, while
 * outer references (such as {@code c.id} in a correlated subquery) are qualified.
 * Comparisons on expressions, such as {@code lower(email) = 'x'}, are ignored.
 *
 * @param equality columns compared with {@code =}, in order of appearance
 * @param range columns compared with {@code <}, {@code <=}, {@code >}, or {@code >=}
 */
public record FilterPredicates(List<String> equality, List<String> range) {

	private static final String LITERAL = "'(?:[^']|'')*'(?:::[a-z][a-z0-9_ ]*(?:\\[\\])?)?";

	private static final String NAME = "(?:\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_$]*)";

	private static final String OPERAND = LITERAL + "|-?\\d+(?:\\.\\d+)?|\\$\\d+|" + NAME + "(?:\\." + NAME + ")?";

	// The lookbehind keeps a cast target such as "::numeric" from reading as a column.
	private static final Pattern COMPARISON = Pattern
		.compile("(?<![\\w$.:\"'])(?<left>" + OPERAND + ")\\s*(?<op><=|>=|=|<|>)\\s*(?<right>" + OPERAND + ")");

	private static final Pattern COLUMN = Pattern.compile(NAME);

	private static final Set<String> NOT_COLUMNS = Set.of("true", "false", "null", "any", "all");

	public static FilterPredicates of(String filter) {
		Set<String> equality = new LinkedHashSet<>();
		Set<String> range = new LinkedHashSet<>();
		Matcher matcher = COMPARISON.matcher(filter);
		while (matcher.find()) {
			String column = column(matcher.group("left"));
			if (column == null) {
				column = column(matcher.group("right"));
			}
			if (column != null) {
				("=".equals(matcher.group("op")) ? equality : range).add(column);
			}
		}
		range.removeAll(equality);
		return new FilterPredicates(List.copyOf(equality), List.copyOf(range));
	}

	private static String column(String operand) {
		if (!COLUMN.matcher(operand).matches() || NOT_COLUMNS.contains(operand.toLowerCase(Locale.ROOT))) {
			return null;
		}
		return operand;
	}

	public boolean isEmpty() {
		return equality.isEmpty() && range.isEmpty();
	}

	/**
	 * Columns for a B-tree index serving this filter: equality columns first, then
	 * one range column, at most three in total.
	 */
	public List<String> indexColumns() {
		List<String> columns = new ArrayList<>(equality);
		if (!range.isEmpty()) {
			columns.add(range.getFirst());
		}
		return columns.subList(0, Math.min(3, columns.size()));
	}

	/** All distinct columns the filter compares. */
	public List<String> allColumns() {
		List<String> columns = new ArrayList<>(equality);
		columns.addAll(range);
		return columns;
	}

}
