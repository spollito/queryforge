package dev.queryforge.api.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import dev.queryforge.api.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The transaction guarantees hold on their own, independent of the read-only
 * role: these tests connect as the database owner, who could otherwise write.
 */
class TargetDatabaseTests {

	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			TestcontainersConfiguration.POSTGRES_IMAGE);

	private static TargetDatabase target;

	@BeforeAll
	static void start() {
		postgres.start();
		target = new TargetDatabase(new TargetDatabaseProperties(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword(), Duration.ofSeconds(3), "UTC", 2));
	}

	@AfterAll
	static void stop() {
		target.close();
		postgres.stop();
	}

	@Test
	void refusesWritesEvenForOwner() {
		assertThatExceptionOfType(TargetDatabaseException.class)
			.isThrownBy(() -> target.inReadOnlyTransaction((connection) -> {
				try (Statement statement = connection.createStatement()) {
					return statement.execute("CREATE TABLE should_not_exist (id int)");
				}
			}))
			.satisfies((ex) -> {
				assertThat(ex.reason()).isEqualTo(TargetDatabaseException.Reason.QUERY_ERROR);
				assertThat(ex.sqlState()).as("read_only_sql_transaction").isEqualTo("25006");
			});
	}

	@Test
	void runsWorkReadOnlyWithStatementTimeoutAndTimeZone() {
		String settings = target.inReadOnlyTransaction((connection) -> {
			try (Statement statement = connection.createStatement();
					ResultSet result = statement.executeQuery(
							"SELECT concat_ws(' ', current_setting('transaction_read_only'), current_setting('statement_timeout'), current_setting('TimeZone'))")) {
				result.next();
				return result.getString(1);
			}
		});

		assertThat(settings).isEqualTo("on 3s UTC");
	}

	@Test
	void classifiesTimeout() {
		assertThatExceptionOfType(TargetDatabaseException.class)
			.isThrownBy(() -> target.inReadOnlyTransaction((connection) -> {
				try (Statement statement = connection.createStatement()) {
					return statement.execute("SELECT pg_sleep(10)");
				}
			}))
			.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(TargetDatabaseException.Reason.TIMEOUT));
	}

	@Test
	void classifiesUnreachableDatabase() {
		try (TargetDatabase unreachable = new TargetDatabase(new TargetDatabaseProperties(
				"jdbc:postgresql://localhost:1/none", "nobody", "", Duration.ofSeconds(1), "UTC", 1))) {
			assertThatExceptionOfType(TargetDatabaseException.class)
				.isThrownBy(() -> unreachable.inReadOnlyTransaction((connection) -> null))
				.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(TargetDatabaseException.Reason.UNAVAILABLE));
		}
	}

}
