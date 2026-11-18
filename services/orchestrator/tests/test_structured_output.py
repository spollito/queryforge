import pytest

from queryforge_orchestrator.llm.structured import (
    StructuredOutputError,
    looks_read_only,
    parse_candidates,
)

VALID = (
    '[{"sql": "SELECT id FROM orders WHERE customer_id = 42",'
    ' "strategy": "sargable",'
    ' "referenced_rule_ids": ["RULE-SARGABLE-001"],'
    ' "explanation": "Use the indexed column directly.",'
    ' "assumptions": [], "semantic_risks": []}]'
)


def test_parse_valid_single_candidate():
    candidates = parse_candidates(VALID, max_candidates=3)
    assert len(candidates) == 1
    assert candidates[0].strategy == "sargable"
    assert candidates[0].referenced_rule_ids == ["RULE-SARGABLE-001"]


def test_malformed_json_is_rejected():
    with pytest.raises(StructuredOutputError):
        parse_candidates("not json {", max_candidates=3)


def test_non_array_payload_is_rejected():
    with pytest.raises(StructuredOutputError):
        parse_candidates('{"foo": "bar"}', max_candidates=3)


def test_object_with_candidates_key_is_accepted():
    payload = '{"candidates": ' + VALID + "}"
    assert len(parse_candidates(payload, max_candidates=3)) == 1


def test_schema_invalid_item_is_dropped_not_coerced():
    # Missing required 'explanation' -> validation fails -> dropped -> no valid candidate.
    payload = '[{"sql": "SELECT 1", "strategy": "x"}]'
    with pytest.raises(StructuredOutputError):
        parse_candidates(payload, max_candidates=3)


def test_unsupported_sql_candidate_is_dropped():
    payload = (
        '[{"sql": "DROP TABLE orders", "strategy": "oops",'
        ' "explanation": "bad", "referenced_rule_ids": [],'
        ' "assumptions": [], "semantic_risks": []}]'
    )
    with pytest.raises(StructuredOutputError):
        parse_candidates(payload, max_candidates=3)


def test_max_candidates_is_enforced():
    one = (
        '{"sql": "SELECT 1", "strategy": "s", "explanation": "e",'
        ' "referenced_rule_ids": [], "assumptions": [], "semantic_risks": []}'
    )
    payload = "[" + ",".join([one] * 5) + "]"
    assert len(parse_candidates(payload, max_candidates=2)) == 2


@pytest.mark.parametrize(
    "sql, expected",
    [
        ("SELECT 1", True),
        ("  select id from orders", True),
        ("WITH c AS (SELECT 1) SELECT * FROM c", True),
        ("VALUES (1), (2)", True),
        ("DELETE FROM orders", False),
        ("UPDATE orders SET status = 'x'", False),
        ("INSERT INTO orders VALUES (1)", False),
        ("WITH c AS (INSERT INTO t VALUES (1) RETURNING id) SELECT * FROM c", False),
        ("SELECT 1; DROP TABLE orders", False),
        ("", False),
        ("   ", False),
    ],
)
def test_looks_read_only(sql, expected):
    assert looks_read_only(sql) is expected
