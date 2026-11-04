package dev.queryforge.api.parsing;

import org.jspecify.annotations.Nullable;

/** The submitted SQL is not a single query QueryForge can analyze. */
public class UnsupportedQueryException extends RuntimeException {

	private final @Nullable Integer line;

	private final @Nullable Integer column;

	public UnsupportedQueryException(String message, @Nullable Integer line, @Nullable Integer column) {
		super(message);
		this.line = line;
		this.column = column;
	}

	public @Nullable Integer line() {
		return line;
	}

	public @Nullable Integer column() {
		return column;
	}

}
