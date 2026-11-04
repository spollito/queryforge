package dev.queryforge.api.connection;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/** A failure while working with the target database, classified by what the caller should do about it. */
public class TargetDatabaseException extends RuntimeException {

	public enum Reason {

		/** The database cannot be reached or rejected the credentials. */
		UNAVAILABLE,

		/** The statement exceeded the configured statement timeout. */
		TIMEOUT,

		/** PostgreSQL rejected the statement, for example an unknown column. */
		QUERY_ERROR

	}

	private static final String QUERY_CANCELED = "57014";

	private final Reason reason;

	private final @Nullable String sqlState;

	private final @Nullable Integer position;

	private TargetDatabaseException(Reason reason, String message, @Nullable String sqlState,
			@Nullable Integer position, Throwable cause) {
		super(message, cause);
		this.reason = reason;
		this.sqlState = sqlState;
		this.position = position;
	}

	/**
	 * Classifies a driver exception. {@code positionOffset} is subtracted from the
	 * error position PostgreSQL reports, for statements the caller prefixed.
	 */
	public static TargetDatabaseException from(SQLException ex, int positionOffset) {
		String sqlState = ex.getSQLState();
		if (ex instanceof SQLTransientConnectionException || isConnectionFailure(sqlState)) {
			return new TargetDatabaseException(Reason.UNAVAILABLE, "The target database is unavailable", sqlState,
					null, ex);
		}
		if (QUERY_CANCELED.equals(sqlState)) {
			return new TargetDatabaseException(Reason.TIMEOUT, "The statement exceeded the statement timeout",
					sqlState, null, ex);
		}
		return new TargetDatabaseException(Reason.QUERY_ERROR, serverMessage(ex), sqlState,
				position(ex, positionOffset), ex);
	}

	// Class 08: connection exception; 28: invalid authorization; 3D000: database does not exist.
	private static boolean isConnectionFailure(@Nullable String sqlState) {
		return sqlState != null && (sqlState.startsWith("08") || sqlState.startsWith("28") || sqlState.equals("3D000"));
	}

	private static String serverMessage(SQLException ex) {
		if (ex instanceof PSQLException psql && psql.getServerErrorMessage() != null
				&& psql.getServerErrorMessage().getMessage() != null) {
			return psql.getServerErrorMessage().getMessage();
		}
		return ex.getMessage();
	}

	private static @Nullable Integer position(SQLException ex, int offset) {
		if (ex instanceof PSQLException psql) {
			ServerErrorMessage server = psql.getServerErrorMessage();
			if (server != null && server.getPosition() - offset > 0) {
				return server.getPosition() - offset;
			}
		}
		return null;
	}

	public Reason reason() {
		return reason;
	}

	public @Nullable String sqlState() {
		return sqlState;
	}

	/** 1-based character position of the error in the submitted statement, if PostgreSQL reported one. */
	public @Nullable Integer position() {
		return position;
	}

}
