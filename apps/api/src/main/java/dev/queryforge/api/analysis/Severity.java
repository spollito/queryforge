package dev.queryforge.api.analysis;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** Declared from most to least severe; findings sort in this order. */
public enum Severity {

	HIGH, MEDIUM, LOW;

	@JsonValue
	public String json() {
		return name().toLowerCase(Locale.ROOT);
	}

}
