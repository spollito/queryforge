"""Typed domain models for the orchestrator (SPEC.md §4, §6, §7).

Pydantic v2 models. ``GraphState`` is the typed LangGraph state carried through the whole
workflow; the fields for later phases (candidate plans, verification, ranking) are declared
now so the state shape is stable, but they are only populated by the nodes that exist.
"""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field


class ExplainMode(str, Enum):
    """Mirrors the Java API's explain modes."""

    ESTIMATE = "estimate"
    EXECUTE = "execute"


class VerificationStatus(str, Enum):
    """Semantic-verification outcome (SPEC.md §7).

    ``UNKNOWN`` and ``UNSUPPORTED`` are explicitly *not* success: a candidate with either
    status must never be presented as verified.
    """

    VERIFIED = "VERIFIED"
    INVALID = "INVALID"
    UNKNOWN = "UNKNOWN"
    UNSUPPORTED = "UNSUPPORTED"

    @property
    def is_trustworthy_equivalence(self) -> bool:
        """True only when the candidate is proven equivalent to the original."""
        return self is VerificationStatus.VERIFIED


class CandidateRewrite(BaseModel):
    """A single structured rewrite proposed by the LLM (SPEC.md §6).

    The LLM's assertions here are *claims*, not guarantees. ``sql`` is re-validated through
    the Java parse gate and, where in scope, checked by Z3 before it is ever trusted.
    """

    model_config = {"extra": "forbid"}

    sql: str = Field(min_length=1, description="The candidate SQL rewrite.")
    strategy: str = Field(min_length=1, description="Short name of the optimization strategy.")
    referenced_rule_ids: list[str] = Field(
        default_factory=list,
        description="Stable rule ids (e.g. RULE-SARGABLE-001) the rewrite relies on.",
    )
    explanation: str = Field(min_length=1, description="Why this rewrite should help.")
    assumptions: list[str] = Field(
        default_factory=list, description="Assumptions the rewrite depends on."
    )
    semantic_risks: list[str] = Field(
        default_factory=list, description="Ways the rewrite could change results."
    )


class VerificationResult(BaseModel):
    """Outcome of verifying one candidate against the original (SPEC.md §7)."""

    model_config = {"extra": "forbid"}

    status: VerificationStatus
    method: str = Field(description="How the status was reached, e.g. 'z3' or 'differential'.")
    counterexample: dict | None = Field(
        default=None, description="A row/binding witnessing non-equivalence, when available."
    )
    detail: str | None = None


class RankingPrediction(BaseModel):
    """Model-predicted ranking signal for one candidate (SPEC.md §9)."""

    model_config = {"extra": "forbid"}

    candidate_index: int
    score: float
    predicted_rank: int


class OptimizationRequest(BaseModel):
    """Public input to the orchestrator."""

    model_config = {"extra": "forbid"}

    sql: str = Field(min_length=1)
    mode: ExplainMode = ExplainMode.ESTIMATE
    max_candidates: int = Field(default=3, ge=1, le=10)


class OptimizationResponse(BaseModel):
    """Public output of the orchestrator (a trimmed view of the final graph state)."""

    model_config = {"extra": "forbid"}

    trace_id: str | None
    result_sql: str | None
    used_fallback: bool
    candidates: list["CandidateRewrite"]
    verification_results: list["VerificationResult"]
    ranking_predictions: list["RankingPrediction"]
    retrieved_refs: list[str]
    prompt_version: str | None
    retry_count: int
    llm_calls: int
    timings_ms: dict[str, float]
    errors: list[str]

    @classmethod
    def from_state(cls, state: "GraphState") -> "OptimizationResponse":
        return cls(
            trace_id=state.trace_id,
            result_sql=state.result_sql,
            used_fallback=state.used_fallback,
            candidates=state.candidates,
            verification_results=state.verification_results,
            ranking_predictions=state.ranking_predictions,
            retrieved_refs=state.retrieved_refs,
            prompt_version=state.prompt_version,
            retry_count=state.retry_count,
            llm_calls=state.llm_calls,
            timings_ms=state.timings_ms,
            errors=state.errors,
        )


class GraphState(BaseModel):
    """Typed state threaded through the LangGraph workflow (SPEC.md §4).

    Later-phase fields default to empty so the shape is stable before those nodes exist.
    """

    model_config = {"extra": "forbid"}

    # Input
    original_sql: str
    mode: ExplainMode = ExplainMode.ESTIMATE
    max_candidates: int = 3

    # Retrieval + context
    schema_context: dict = Field(default_factory=dict)
    original_plan: dict | None = None
    retrieved_refs: list[str] = Field(default_factory=list)

    # Generation + verification + ranking
    candidates: list[CandidateRewrite] = Field(default_factory=list)
    candidate_plans: list[dict] = Field(default_factory=list)
    verification_results: list[VerificationResult] = Field(default_factory=list)
    verification_feedback: list[str] = Field(default_factory=list)
    ranking_predictions: list[RankingPrediction] = Field(default_factory=list)

    # Control + bookkeeping
    retry_count: int = 0
    llm_calls: int = 0
    prompt_version: str | None = None
    timings_ms: dict[str, float] = Field(default_factory=dict)
    trace_id: str | None = None

    # Output
    result_sql: str | None = None
    used_fallback: bool = False
    errors: list[str] = Field(default_factory=list)

    def fall_back_to_original(self, reason: str) -> None:
        """Select the original query as the safe result (SPEC.md §7, §8)."""
        self.result_sql = self.original_sql
        self.used_fallback = True
        self.errors.append(reason)
