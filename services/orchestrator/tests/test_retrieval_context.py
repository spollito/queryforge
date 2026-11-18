from queryforge_orchestrator.models import CandidateRewrite
from queryforge_orchestrator.retrieval.context import (
    build_rules_block,
    retrieve_rules,
    ungrounded_rule_ids,
)


def _candidate(rule_ids):
    return CandidateRewrite(
        sql="SELECT 1",
        strategy="s",
        referenced_rule_ids=rule_ids,
        explanation="e",
    )


def test_retrieve_rules_returns_references():
    refs = retrieve_rules("sequential scan selective filter", k=3)
    assert refs
    assert all(ref.rule_id.startswith("RULE-") for ref in refs)


def test_build_rules_block_includes_ids_and_text():
    refs = retrieve_rules("order by limit sort", k=2)
    block = build_rules_block(refs)
    for ref in refs:
        assert f"[{ref.rule_id}]" in block
        assert ref.title in block


def test_build_rules_block_handles_empty():
    assert build_rules_block([]) == "(no optimization rules retrieved)"


def test_ungrounded_rule_ids_flags_unknown_citations():
    candidate = _candidate(["RULE-SARGABLE-001", "RULE-MADE-UP-999"])
    assert ungrounded_rule_ids(candidate) == ["RULE-MADE-UP-999"]


def test_ungrounded_rule_ids_empty_when_all_real():
    candidate = _candidate(["RULE-SARGABLE-001", "RULE-INDEX-FK-001"])
    assert ungrounded_rule_ids(candidate) == []
