package dev.queryforge.api.parsing;

import org.apache.calcite.sql.SqlNode;

/**
 * A statement accepted for analysis.
 *
 * @param sql the statement exactly as submitted, minus trailing semicolons; this
 * text, not Calcite's rendering of it, is what PostgreSQL receives
 * @param tree Calcite's parse tree, for later rewriting and verification stages
 */
public record ParsedQuery(String sql, SqlNode tree) {
}
