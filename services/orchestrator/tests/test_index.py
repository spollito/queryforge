from queryforge_orchestrator.retrieval.index import LexicalIndex

index = LexicalIndex()


def test_sequential_scan_query_retrieves_sargable_rule():
    refs = index.search("sequential scan on a selective filter using a function on the column")
    assert refs, "expected at least one rule"
    assert refs[0].rule_id == "RULE-SARGABLE-001"


def test_order_by_limit_query_retrieves_limit_index_rule():
    refs = index.search("order by created_at limit 10 causing a sort spill to disk")
    ids = [ref.rule_id for ref in refs]
    assert "RULE-LIMIT-INDEX-001" in ids


def test_correlated_subquery_query_retrieves_decorrelate_rule():
    refs = index.search("correlated subquery runs once per outer row nested loop")
    ids = [ref.rule_id for ref in refs]
    assert "RULE-DECORRELATE-001" in ids


def test_results_are_sorted_by_descending_score():
    refs = index.search("index join foreign key sequential scan")
    scores = [ref.score for ref in refs]
    assert scores == sorted(scores, reverse=True)


def test_k_limits_result_count():
    assert len(index.search("index sort join filter subquery", k=2)) <= 2


def test_empty_or_irrelevant_query_returns_nothing():
    assert index.search("") == []
    assert index.search("   ") == []
    assert index.search("zzzxxxqqq nonexistentterm") == []
