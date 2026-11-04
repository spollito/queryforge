package dev.queryforge.api.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class QueryParserTests {

	private final QueryParser parser = new QueryParser();

	@ParameterizedTest
	@ValueSource(strings = { "SELECT id FROM orders WHERE customer_id = 42",
			"select o.id, c.email from orders o join customers c on c.id = o.customer_id order by o.created_at desc limit 10",
			"WITH recent AS (SELECT * FROM orders WHERE created_at > '2025-01-01') SELECT count(*) FROM recent",
			"SELECT id FROM orders UNION ALL SELECT id FROM customers", "VALUES (1, 'a'), (2, 'b')",
			"SELECT id::text FROM customers WHERE email ILIKE 'customer4%'",
			"SELECT status, count(*) FILTER (WHERE total_cents > 1000) FROM orders GROUP BY status",
			"SELECT c.id, (SELECT max(o.created_at) FROM orders o WHERE o.customer_id = c.id) FROM customers c" })
	void acceptsReadOnlyQueries(String sql) {
		assertThat(parser.parse(sql).sql()).isEqualTo(sql);
	}

	@Test
	void stripsTrailingSemicolonsAndWhitespace() {
		assertThat(parser.parse("  SELECT 1 ;; \n").sql()).isEqualTo("SELECT 1");
	}

	@ParameterizedTest
	@ValueSource(strings = { "DELETE FROM orders WHERE id = 1", "UPDATE orders SET status = 'x'",
			"INSERT INTO orders (id) VALUES (1)" })
	void rejectsDataChangingStatements(String sql) {
		assertThatExceptionOfType(UnsupportedQueryException.class).isThrownBy(() -> parser.parse(sql))
			.withMessageStartingWith("Only read-only queries");
	}

	@ParameterizedTest
	@ValueSource(strings = { "DROP TABLE orders", "CREATE TABLE t (id int)", "TRUNCATE orders" })
	void rejectsSchemaChangingStatements(String sql) {
		assertThatExceptionOfType(UnsupportedQueryException.class).isThrownBy(() -> parser.parse(sql));
	}

	@Test
	void rejectsStackedStatements() {
		assertThatExceptionOfType(UnsupportedQueryException.class)
			.isThrownBy(() -> parser.parse("SELECT 1; DROP TABLE orders"))
			.satisfies((ex) -> {
				assertThat(ex.line()).isEqualTo(1);
				assertThat(ex.column()).isEqualTo(9);
			});
	}

	@Test
	void rejectsExplainWithClearMessage() {
		assertThatExceptionOfType(UnsupportedQueryException.class)
			.isThrownBy(() -> parser.parse("explain (analyze) SELECT 1"))
			.withMessage("Submit the query without EXPLAIN; QueryForge adds it.");
	}

	@Test
	void reportsSyntaxErrorPositionWithoutExpectedTokenList() {
		assertThatExceptionOfType(UnsupportedQueryException.class)
			.isThrownBy(() -> parser.parse("SELECT id FROM orders WHERE"))
			.satisfies((ex) -> {
				assertThat(ex.getMessage()).doesNotContain("Was expecting");
				assertThat(ex.line()).isEqualTo(1);
			});
	}

}
