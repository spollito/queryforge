from queryforge_orchestrator.retrieval.rules import (
    RULE_CORPUS,
    RULE_ID_PATTERN,
    all_rule_ids,
    rule_by_id,
)


def test_rule_ids_are_unique():
    ids = [rule.id for rule in RULE_CORPUS]
    assert len(ids) == len(set(ids))


def test_rule_ids_follow_stable_pattern():
    for rule in RULE_CORPUS:
        assert RULE_ID_PATTERN.match(rule.id), rule.id


def test_rule_by_id_round_trips():
    for rule in RULE_CORPUS:
        assert rule_by_id(rule.id) is rule
    assert rule_by_id("RULE-DOES-NOT-EXIST-999") is None


def test_all_rule_ids_matches_corpus():
    assert all_rule_ids() == frozenset(rule.id for rule in RULE_CORPUS)


def test_mock_default_cites_a_real_rule():
    # The MockLlmClient default response cites RULE-IDENTITY-000; it must exist so grounding
    # checks pass in offline mode.
    assert rule_by_id("RULE-IDENTITY-000") is not None
