package dev.queryforge.api.analysis;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Formatting for numbers and identifiers in finding text. */
public final class Formats {

	private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_$]*");

	private Formats() {
	}

	/** Whole number with thousands separators, for example {@code 500,000}. */
	public static String rows(double value) {
		return String.format(Locale.ROOT, "%,d", Math.round(value));
	}

	/** A fraction as a percentage with precision that suits its size, for example {@code 0.0022%}. */
	public static String percent(double fraction) {
		double percent = fraction * 100;
		if (percent == 0) {
			return "0%";
		}
		if (percent >= 10) {
			return String.format(Locale.ROOT, "%.0f%%", percent);
		}
		if (percent >= 1) {
			return String.format(Locale.ROOT, "%.1f%%", percent);
		}
		if (percent >= 0.0001) {
			return String.format(Locale.ROOT, "%.2g%%", percent);
		}
		return "less than 0.0001%";
	}

	/** Kilobytes as a human-readable size, for example {@code 16 MB}. */
	public static String kilobytes(double kilobytes) {
		if (kilobytes >= 1024) {
			return String.format(Locale.ROOT, "%.0f MB", kilobytes / 1024);
		}
		return String.format(Locale.ROOT, "%.0f kB", kilobytes);
	}

	/** A ratio such as {@code 10×}. */
	public static String times(double ratio) {
		return (ratio >= 10) ? String.format(Locale.ROOT, "%,d×", Math.round(ratio))
				: String.format(Locale.ROOT, "%.1f×", ratio);
	}

	/** Quotes an identifier for SQL only when PostgreSQL requires it. */
	public static String identifier(String name) {
		if (name.startsWith("\"") || PLAIN_IDENTIFIER.matcher(name).matches()) {
			return name;
		}
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	public static String identifiers(List<String> names) {
		return names.stream().map(Formats::identifier).collect(Collectors.joining(", "));
	}

}
