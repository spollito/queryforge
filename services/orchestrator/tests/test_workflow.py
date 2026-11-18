import json

from queryforge_orchestrator.config import OptimizerLimits
from queryforge_orchestrator.graph import OptimizerDeps, run_optimization
from queryforge_orchestrator.integration.fakes import FakeJavaApiClient
from queryforge_orchestrator.integration.java_client import AnalyzeOutcome
from queryforge_orchestrator.llm.client import LlmClient
from queryforge_orchestrator.models import (
    OptimizationRequest,
    VerificationResult,
    VerificationStatus,
)
from queryforge_orchestrator.ranking import PlannerCostRanker
from queryforge_orchestrator.retrieval.context import retrieve_rules
from queryforge_orchestrator.retrieval.schema import StaticSchemaProvider
from queryforge_orchestrator.verification import Verifier

ORIGINAL = "SELECT id FROM orders WHERE customer_id = 42"


class ScriptedLlmClient(LlmClient):
    """Returns a scripted sequence of raw responses; the last one repeats."""

    def __init__(self, responses):
        self._responses = list(responses)
        self.calls = []

    def complete(self, prompt):
        self.calls.append(prompt)
        index = min(len(self.calls) - 1, len(self._responses) - 1)
        return self._responses[index]

    @property
    def name(self):
        return "scripted"


class AlwaysVerifier(Verifier):
    def verify(self, original_sql, candidate_sql):
        return VerificationResult(status=VerificationStatus.VERIFIED, method="fake")


class NeverVerifier(Verifier):
    def verify(self, original_sql, candidate_sql):
        return VerificationResult(status=VerificationStatus.UNKNOWN, method="none")


def _candidate_json(sql, rule_ids=("RULE-SARGABLE-001",)):
    return {
        "sql": sql,
        "strategy": "test",
        "referenced_rule_ids": list(rule_ids),
        "explanation": "test candidate",
        "assumptions": [],
        "semantic_risks": [],
    }


def _deps(llm, verifier, java_client=None, limits=None):
    return OptimizerDeps(
        llm=llm,
        java_client=java_client or FakeJavaApiClient(),
        schema_provider=StaticSchemaProvider(),
        verifier=verifier,
        ranker=PlannerCostRanker(),
        retrieve_fn=retrieve_rules,
        retrieval_k=3,
        limits=limits or OptimizerLimits(),
    )


def test_happy_path_selects_verified_candidate():
    rewrite = "SELECT id FROM orders WHERE customer_id = 42 AND status = 'paid'"
    llm = ScriptedLlmClient([json.dumps([_candidate_json(rewrite)])])
    state = run_optimization(OptimizationRequest(sql=ORIGINAL), _deps(llm, AlwaysVerifier()))

    assert state.used_fallback is False
    assert state.result_sql == rewrite
    assert len(state.candidates) == 1
    assert state.verification_results[0].status is VerificationStatus.VERIFIED
    assert len(state.ranking_predictions) == 1
    # RAG and reproducibility signals are populated.
    assert state.retrieved_refs
    assert state.prompt_version == "rewrite/v1"
    assert state.trace_id


def test_ranking_prefers_lower_cost_candidate():
    cheap = "SELECT id FROM orders WHERE customer_id = 42 /* cheap */"
    pricey = "SELECT id FROM orders WHERE customer_id = 42 /* pricey */"
    llm = ScriptedLlmClient([json.dumps([_candidate_json(pricey), _candidate_json(cheap)])])
    java = FakeJavaApiClient(
        outcomes={
            pricey: AnalyzeOutcome(ok=True, plan={"plan": {"totalCost": 900.0}}, total_cost=900.0),
            cheap: AnalyzeOutcome(ok=True, plan={"plan": {"totalCost": 100.0}}, total_cost=100.0),
        }
    )
    state = run_optimization(
        OptimizationRequest(sql=ORIGINAL, max_candidates=2), _deps(llm, AlwaysVerifier(), java)
    )
    assert state.result_sql == cheap
    assert state.used_fallback is False


def test_unknown_verification_falls_back_to_original():
    rewrite = "SELECT id FROM orders WHERE customer_id = 42 AND 1 = 1"
    llm = ScriptedLlmClient([json.dumps([_candidate_json(rewrite)])])
    limits = OptimizerLimits(max_retries=2, llm_call_budget=3)
    state = run_optimization(OptimizationRequest(sql=ORIGINAL), _deps(llm, NeverVerifier(), limits=limits))

    assert state.used_fallback is True
    assert state.result_sql == ORIGINAL
    # Bounded: initial generation + 2 retries = 3 LLM calls, never more than the budget.
    assert state.llm_calls == 3
    assert state.retry_count == 2


def test_candidate_rejected_by_java_is_dropped_then_fallback():
    bad = "SELECT nonsense FROM missing_table"
    llm = ScriptedLlmClient([json.dumps([_candidate_json(bad)])])
    java = FakeJavaApiClient(outcomes={bad: AnalyzeOutcome.rejected("unsupported-query")})
    state = run_optimization(OptimizationRequest(sql=ORIGINAL), _deps(llm, AlwaysVerifier(), java))

    assert state.used_fallback is True
    assert state.result_sql == ORIGINAL
    assert state.candidates == []  # the rejected candidate was dropped


def test_original_rejected_falls_back_without_calling_llm():
    llm = ScriptedLlmClient([json.dumps([_candidate_json("SELECT 1")])])
    java = FakeJavaApiClient(outcomes={ORIGINAL: AnalyzeOutcome.rejected("unsupported-query")})
    state = run_optimization(OptimizationRequest(sql=ORIGINAL), _deps(llm, AlwaysVerifier(), java))

    assert state.used_fallback is True
    assert state.result_sql == ORIGINAL
    assert state.llm_calls == 0  # generation never ran
    assert llm.calls == []


def test_empty_sql_short_circuits_to_fallback():
    llm = ScriptedLlmClient([json.dumps([_candidate_json("SELECT 1")])])
    state = run_optimization(OptimizationRequest(sql="   "), _deps(llm, AlwaysVerifier()))
    assert state.used_fallback is True
    assert state.llm_calls == 0
    assert any("empty" in e.lower() for e in state.errors)


def test_retry_then_success():
    good = "SELECT id FROM orders WHERE customer_id = 42 AND status = 'paid'"
    llm = ScriptedLlmClient(["not json at all", json.dumps([_candidate_json(good)])])
    state = run_optimization(OptimizationRequest(sql=ORIGINAL), _deps(llm, AlwaysVerifier()))

    assert state.used_fallback is False
    assert state.result_sql == good
    assert state.llm_calls == 2
    assert state.retry_count == 1


# --- end-to-end with the real Z3 verifier --------------------------------------------------


def test_z3_selects_a_truly_equivalent_rewrite():
    from queryforge_orchestrator.verification.z3_verifier import Z3Verifier

    equivalent = "SELECT id FROM orders WHERE 42 = customer_id"
    llm = ScriptedLlmClient([json.dumps([_candidate_json(equivalent)])])
    state = run_optimization(OptimizationRequest(sql=ORIGINAL), _deps(llm, Z3Verifier()))

    assert state.used_fallback is False
    assert state.result_sql == equivalent
    assert state.verification_results[0].status is VerificationStatus.VERIFIED


def test_z3_rejects_non_equivalent_rewrite_records_feedback_and_falls_back():
    from queryforge_orchestrator.verification.z3_verifier import Z3Verifier

    not_equivalent = "SELECT id FROM orders WHERE customer_id >= 42"
    llm = ScriptedLlmClient([json.dumps([_candidate_json(not_equivalent)])])
    state = run_optimization(OptimizationRequest(sql=ORIGINAL), _deps(llm, Z3Verifier()))

    assert state.used_fallback is True
    assert state.result_sql == ORIGINAL
    # A counterexample was captured and fed back for the bounded retries (SPEC §7).
    assert state.verification_feedback
    assert "counterexample row" in state.verification_feedback[0]
