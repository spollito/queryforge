from queryforge_orchestrator.models import (
    ExplainMode,
    GraphState,
    VerificationStatus,
)


def test_verification_status_trust():
    assert VerificationStatus.VERIFIED.is_trustworthy_equivalence is True
    for status in (
        VerificationStatus.INVALID,
        VerificationStatus.UNKNOWN,
        VerificationStatus.UNSUPPORTED,
    ):
        assert status.is_trustworthy_equivalence is False


def test_graph_state_defaults_are_empty():
    state = GraphState(original_sql="SELECT 1")
    assert state.mode is ExplainMode.ESTIMATE
    assert state.candidates == []
    assert state.verification_results == []
    assert state.retry_count == 0
    assert state.used_fallback is False
    assert state.result_sql is None


def test_fall_back_to_original_selects_original_and_records_reason():
    state = GraphState(original_sql="SELECT 1")
    state.fall_back_to_original("no verified candidate")
    assert state.result_sql == "SELECT 1"
    assert state.used_fallback is True
    assert "no verified candidate" in state.errors
