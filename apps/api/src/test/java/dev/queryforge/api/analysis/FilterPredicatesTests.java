package dev.queryforge.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilterPredicatesTests {

	@Test
	void findsEqualityColumn() {
		FilterPredicates predicates = FilterPredicates.of("(customer_id = 42)");

		assertThat(predicates.equality()).containsExactly("customer_id");
		assertThat(predicates.range()).isEmpty();
	}

	@Test
	void handlesCastLiteralsWithMultiWordTypes() {
		FilterPredicates predicates = FilterPredicates.of(
				"((country_code = 'GB'::bpchar) AND (created_at = '2023-01-05 00:00:00+00'::timestamp with time zone))");

		assertThat(predicates.equality()).containsExactly("country_code", "created_at");
	}

	@Test
	void ordersEqualityBeforeRangeForIndexColumns() {
		FilterPredicates predicates = FilterPredicates
			.of("((created_at >= '2025-01-01'::timestamp with time zone) AND (status = 'pending'::text))");

		assertThat(predicates.indexColumns()).containsExactly("status", "created_at");
	}

	@Test
	void treatsQualifiedNamesAsOuterReferences() {
		// In a correlated subquery, the scanned table's column is unqualified and c.id belongs to the outer query.
		assertThat(FilterPredicates.of("(customer_id = c.id)").equality()).containsExactly("customer_id");
	}

	@Test
	void findsColumnOnRightHandSide() {
		assertThat(FilterPredicates.of("(42 = customer_id)").equality()).containsExactly("customer_id");
	}

	@Test
	void ignoresFunctionCallsAndCastTargets() {
		assertThat(FilterPredicates.of("(lower(email) = 'a@example.com'::text)").isEmpty()).isTrue();
		assertThat(FilterPredicates.of("((total_cents)::numeric > 100.5)").isEmpty()).isTrue();
	}

	@Test
	void ignoresStringContentsThatLookLikeComparisons() {
		assertThat(FilterPredicates.of("(note = 'a = b'::text)").equality()).containsExactly("note");
	}

	@Test
	void limitsIndexToThreeColumns() {
		FilterPredicates predicates = FilterPredicates.of("((a = 1) AND (b = 2) AND (c = 3) AND (d = 4))");

		assertThat(predicates.indexColumns()).containsExactly("a", "b", "c");
	}

}
