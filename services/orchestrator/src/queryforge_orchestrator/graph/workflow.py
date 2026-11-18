"""The bounded LangGraph state graph (SPEC.md §4).

An explicit state machine, not an autonomous agent. Deterministic nodes (parse, validate,
verify, rank, DB access via the Java API) do the load-bearing work; the LLM is used only in
``generate_candidates``. Conditional edges reject invalid transforms, retry generation within
a bounded budget, and fall back to the original query when no candidate is verified.

Node flow:

    START → validate_input → parse_sql → retrieve_context → inspect_plan
          → generate_candidates → validate_sql → verify_semantics
          → (verified?) rank_candidates → finalize → END
          → (retry?)    bump_retry → generate_candidates
          → (give up)   finalize (fallback to original)

Any node that records an error short-circuits to ``finalize``, which always returns a
result — the selected candidate, or the original query as a safe fallback.
"""

from __future__ import annotations

import json
import os
import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass

from langgraph.graph import END, START, StateGraph

from ..config import OptimizerLimits
from ..integration.fakes import FakeJavaApiClient
from ..integration.java_client import HttpJavaApiClient, JavaApiClient, JavaApiError
from ..llm.client import LlmClient, default_client
from ..llm.structured import StructuredOutputError, looks_read_only, parse_candidates
from ..models import ExplainMode, GraphState, OptimizationRequest, VerificationStatus
from ..prompts import ACTIVE_REWRITE_PROMPT
from ..ranking import PlannerCostRanker, Ranker
from ..retrieval.context import build_rules_block_from_ids, retrieve_rules
from ..retrieval.index import RetrievedReference
from ..retrieval.schema import HttpSchemaProvider, SchemaProvider, StaticSchemaProvider
from ..verification import Verifier, default_verifier

RetrieveFn = Callable[[str, int], list[RetrievedReference]]


@dataclass
class OptimizerDeps:
    """Injected collaborators for the workflow. Defaults are offline and deterministic."""

    llm: LlmClient
    java_client: JavaApiClient
    schema_provider: SchemaProvider
    verifier: Verifier
    ranker: Ranker
    retrieve_fn: RetrieveFn
    retrieval_k: int
    limits: OptimizerLimits

    @classmethod
    def from_env(cls) -> "OptimizerDeps":
        java_url = os.environ.get("QUERYFORGE_JAVA_API_URL")
        java_client: JavaApiClient = (
            HttpJavaApiClient(java_url) if java_url else FakeJavaApiClient()
        )
        schema_provider: SchemaProvider = (
            HttpSchemaProvider(java_url) if java_url else StaticSchemaProvider()
        )
        return cls(
            llm=default_client(),
            java_client=java_client,
            schema_provider=schema_provider,
            verifier=default_verifier(),
            ranker=PlannerCostRanker(),
            retrieve_fn=retrieve_rules,
            retrieval_k=3,
            limits=OptimizerLimits.from_env(),
        )


def _over_deadline(state: GraphState, limits: OptimizerLimits) -> bool:
    start = state.timings_ms.get("start")
    return start is not None and (time.monotonic() - start) > limits.deadline_s


def build_graph(deps: OptimizerDeps):
    """Build and compile the LangGraph workflow for the given dependencies."""
    limits = deps.limits

    def validate_input(state: GraphState) -> dict:
        timings = {**state.timings_ms, "start": time.monotonic()}
        if not state.original_sql.strip():
            return {"timings_ms": timings, "errors": [*state.errors, "empty SQL"]}
        if len(state.original_sql) > limits.max_sql_length:
            return {"timings_ms": timings, "errors": [*state.errors, "SQL exceeds max length"]}
        return {"timings_ms": timings}

    def parse_sql(state: GraphState) -> dict:
        if not looks_read_only(state.original_sql):
            return {"errors": [*state.errors, "original query is not a read-only statement"]}
        return {}

    def retrieve_context(state: GraphState) -> dict:
        schema = deps.schema_provider.fetch()
        refs = deps.retrieve_fn(state.original_sql, deps.retrieval_k)
        return {
            "schema_context": {"text": schema.to_prompt_text()},
            "retrieved_refs": [ref.rule_id for ref in refs],
        }

    def inspect_plan(state: GraphState) -> dict:
        try:
            outcome = deps.java_client.analyze(state.original_sql, ExplainMode.ESTIMATE)
        except JavaApiError as exc:
            return {"errors": [*state.errors, f"plan capture failed: {exc}"]}
        if not outcome.ok:
            return {"errors": [*state.errors, f"original query rejected: {outcome.error_code}"]}
        return {"original_plan": outcome.plan}

    def generate_candidates(state: GraphState) -> dict:
        if state.llm_calls >= limits.llm_call_budget:
            return {"errors": [*state.errors, "LLM call budget exhausted"]}
        plan_text = json.dumps(state.original_plan)[:4000] if state.original_plan else ""
        prompt = ACTIVE_REWRITE_PROMPT.render(
            max_candidates=str(state.max_candidates),
            original_sql=state.original_sql,
            schema=state.schema_context.get("text", ""),
            plan=plan_text,
            rules=build_rules_block_from_ids(state.retrieved_refs),
        )
        if state.verification_feedback:
            # Counterexample feedback from a previous attempt (SPEC.md §7): rows where a
            # rejected candidate disagreed with the original. Treated as data, not instructions.
            prompt += "\n\nPREVIOUS CANDIDATES WERE REJECTED. Avoid rewrites that differ on:\n"
            prompt += "\n".join(f"- {item}" for item in state.verification_feedback)
        raw = deps.llm.complete(prompt)
        base = {
            "llm_calls": state.llm_calls + 1,
            "prompt_version": ACTIVE_REWRITE_PROMPT.version,
            # Reset per-generation working set (matters on retries).
            "candidate_plans": [],
            "verification_results": [],
            "ranking_predictions": [],
        }
        try:
            candidates = parse_candidates(raw, state.max_candidates)
        except StructuredOutputError as exc:
            return {**base, "candidates": [], "errors": [*state.errors, f"generation: {exc}"]}
        return {**base, "candidates": candidates}

    def validate_sql(state: GraphState) -> dict:
        valid = []
        plans = []
        for candidate in state.candidates:
            if not looks_read_only(candidate.sql):
                continue
            try:
                outcome = deps.java_client.analyze(candidate.sql, ExplainMode.ESTIMATE)
            except JavaApiError:
                # Java/DB unavailable: cannot validate this candidate, so drop it (safe).
                continue
            if outcome.ok:
                valid.append(candidate)
                plans.append({"plan": outcome.plan, "total_cost": outcome.total_cost})
        return {"candidates": valid, "candidate_plans": plans}

    def verify_semantics(state: GraphState) -> dict:
        results = [
            deps.verifier.verify(state.original_sql, candidate.sql)
            for candidate in state.candidates
        ]
        # Collect counterexamples from rejected candidates as feedback for a possible retry.
        feedback = [
            f"{candidate.sql} -> counterexample row {result.counterexample}"
            for candidate, result in zip(state.candidates, results)
            if result.status is VerificationStatus.INVALID and result.counterexample is not None
        ]
        return {"verification_results": results, "verification_feedback": feedback}

    def bump_retry(state: GraphState) -> dict:
        return {"retry_count": state.retry_count + 1}

    def rank_candidates(state: GraphState) -> dict:
        items: list[tuple[int, float | None]] = []
        for index, result in enumerate(state.verification_results):
            if not result.status.is_trustworthy_equivalence:
                continue
            cost = None
            if index < len(state.candidate_plans):
                cost = state.candidate_plans[index].get("total_cost")
            items.append((index, cost))
        return {"ranking_predictions": deps.ranker.rank(items)}

    def finalize(state: GraphState) -> dict:
        timings = {**state.timings_ms}
        start = timings.get("start")
        if start is not None:
            timings["total_ms"] = (time.monotonic() - start) * 1000.0
        if state.ranking_predictions:
            best = min(state.ranking_predictions, key=lambda pred: pred.predicted_rank)
            return {
                "result_sql": state.candidates[best.candidate_index].sql,
                "used_fallback": False,
                "timings_ms": timings,
            }
        errors = state.errors if state.errors else ["no verified candidate available"]
        return {
            "result_sql": state.original_sql,
            "used_fallback": True,
            "errors": errors,
            "timings_ms": timings,
        }

    def route_after_validate(state: GraphState) -> str:
        return "finalize" if state.errors else "parse_sql"

    def route_after_parse(state: GraphState) -> str:
        return "finalize" if state.errors else "retrieve_context"

    def route_after_inspect(state: GraphState) -> str:
        return "finalize" if state.errors else "generate_candidates"

    def route_after_verify(state: GraphState) -> str:
        if any(r.status.is_trustworthy_equivalence for r in state.verification_results):
            return "rank_candidates"
        can_retry = (
            state.retry_count < limits.max_retries
            and state.llm_calls < limits.llm_call_budget
            and not _over_deadline(state, limits)
        )
        return "regenerate" if can_retry else "finalize"

    graph = StateGraph(GraphState)
    graph.add_node("validate_input", validate_input)
    graph.add_node("parse_sql", parse_sql)
    graph.add_node("retrieve_context", retrieve_context)
    graph.add_node("inspect_plan", inspect_plan)
    graph.add_node("generate_candidates", generate_candidates)
    graph.add_node("validate_sql", validate_sql)
    graph.add_node("verify_semantics", verify_semantics)
    graph.add_node("bump_retry", bump_retry)
    graph.add_node("rank_candidates", rank_candidates)
    graph.add_node("finalize", finalize)

    graph.add_edge(START, "validate_input")
    graph.add_conditional_edges(
        "validate_input", route_after_validate,
        {"finalize": "finalize", "parse_sql": "parse_sql"},
    )
    graph.add_conditional_edges(
        "parse_sql", route_after_parse,
        {"finalize": "finalize", "retrieve_context": "retrieve_context"},
    )
    graph.add_edge("retrieve_context", "inspect_plan")
    graph.add_conditional_edges(
        "inspect_plan", route_after_inspect,
        {"finalize": "finalize", "generate_candidates": "generate_candidates"},
    )
    graph.add_edge("generate_candidates", "validate_sql")
    graph.add_edge("validate_sql", "verify_semantics")
    graph.add_conditional_edges(
        "verify_semantics", route_after_verify,
        {"rank_candidates": "rank_candidates", "regenerate": "bump_retry", "finalize": "finalize"},
    )
    graph.add_edge("bump_retry", "generate_candidates")
    graph.add_edge("rank_candidates", "finalize")
    graph.add_edge("finalize", END)
    return graph.compile()


def run_optimization(request: OptimizationRequest, deps: OptimizerDeps | None = None) -> GraphState:
    """Run the workflow for one request and return the final typed state."""
    deps = deps or OptimizerDeps.from_env()
    app = build_graph(deps)
    initial = GraphState(
        original_sql=request.sql,
        mode=request.mode,
        max_candidates=min(request.max_candidates, deps.limits.max_candidates),
        trace_id=str(uuid.uuid4()),
    )
    raw = app.invoke(initial, config={"recursion_limit": deps.limits.recursion_limit})
    return GraphState.model_validate(raw)
