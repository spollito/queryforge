package dev.queryforge.api.connection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

/**
 * Connection pool for the database QueryForge analyzes.
 *
 * <p>Every unit of work runs in a read-only transaction with a statement timeout
 * and is always rolled back, so analysis cannot change the target's data even if
 * the connecting role has write privileges. The pool is deliberately not a Spring
 * {@code DataSource} bean: it must never be mistaken for QueryForge's own database.
 */
@Component
public class TargetDatabase implements AutoCloseable {

	/** Work to run against the target. Must not commit; the transaction is always rolled back. */
	@FunctionalInterface
	public interface Work<T> {

		T execute(Connection connection) throws SQLException;

	}

	private final HikariDataSource dataSource;

	private final long statementTimeoutMs;

	private final String timeZone;

	public TargetDatabase(TargetDatabaseProperties properties) {
		HikariConfig config = new HikariConfig();
		config.setPoolName("target");
		config.setJdbcUrl(properties.url());
		config.setUsername(properties.username());
		config.setPassword(properties.password());
		config.setMaximumPoolSize(properties.maxPoolSize());
		config.setReadOnly(true);
		config.setAutoCommit(false);
		config.setConnectionTimeout(5_000);
		// Start even when the target is down; failures are reported per request.
		config.setInitializationFailTimeout(-1);
		this.dataSource = new HikariDataSource(config);
		this.statementTimeoutMs = properties.statementTimeout().toMillis();
		this.timeZone = properties.timeZone();
	}

	/**
	 * Runs {@code work} in a read-only transaction that is rolled back afterwards.
	 * Driver failures are translated to {@link TargetDatabaseException}.
	 */
	public <T> T inReadOnlyTransaction(Work<T> work) {
		try (Connection connection = dataSource.getConnection()) {
			try {
				beginReadOnly(connection);
				T result = work.execute(connection);
				connection.rollback();
				return result;
			}
			catch (SQLException | RuntimeException ex) {
				rollbackQuietly(connection, ex);
				throw ex;
			}
		}
		catch (SQLException ex) {
			throw TargetDatabaseException.from(ex, 0);
		}
	}

	private void beginReadOnly(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			// Explicit, so safety does not depend on driver or role settings.
			statement.execute("SET TRANSACTION READ ONLY");
		}
		// Transaction-local, like SET LOCAL, so pooled connections keep no state.
		try (PreparedStatement statement = connection
			.prepareStatement("SELECT set_config('statement_timeout', ?, true), set_config('TimeZone', ?, true)")) {
			statement.setString(1, statementTimeoutMs + "ms");
			statement.setString(2, timeZone);
			statement.execute();
		}
	}

	private static void rollbackQuietly(Connection connection, Exception original) {
		try {
			connection.rollback();
		}
		catch (SQLException rollbackFailure) {
			original.addSuppressed(rollbackFailure);
		}
	}

	@Override
	public void close() {
		dataSource.close();
	}

}
