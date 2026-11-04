package dev.queryforge.api.plan;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.json.JsonMapper;

/**
 * Loads EXPLAIN (FORMAT JSON) output captured from PostgreSQL 18 against the
 * sample database (infrastructure/sample-db, scale 1). The query for each fixture
 * is recorded next to the test that uses it.
 */
public final class PlanFixtures {

	private static final ExplainJsonParser PARSER = new ExplainJsonParser(JsonMapper.builder().build());

	private PlanFixtures() {
	}

	public static ExecutionPlan load(String name) {
		return PARSER.parse(read(name));
	}

	public static String read(String name) {
		try (InputStream in = PlanFixtures.class.getResourceAsStream("/plans/" + name + ".json")) {
			if (in == null) {
				throw new IllegalArgumentException("No plan fixture named " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static PlanNode node(ExecutionPlan plan, int id) {
		return plan.nodes().get(id);
	}

}
