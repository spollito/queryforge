"""Bounded-execution limits for the optimizer (SPEC.md §4).

Explicit maximum iteration counts, an LLM-call budget, and an overall wall-clock deadline.
All overridable by environment variable so a deployment can tighten them.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


def _int_env(name: str, default: int) -> int:
    try:
        return int(os.environ[name])
    except (KeyError, ValueError):
        return default


def _float_env(name: str, default: float) -> float:
    try:
        return float(os.environ[name])
    except (KeyError, ValueError):
        return default


@dataclass(frozen=True)
class OptimizerLimits:
    #: Max candidate rewrites requested from the LLM per generation.
    max_candidates: int = 3
    #: Max times generation may be retried after producing no verified candidate.
    max_retries: int = 2
    #: Hard cap on LLM completions per optimization (defence against retry loops).
    llm_call_budget: int = 3
    #: Reject input SQL longer than this many characters.
    max_sql_length: int = 20_000
    #: Overall wall-clock budget for one optimization.
    deadline_s: float = 30.0
    #: LangGraph recursion limit (total node executions) — a backstop against loops.
    recursion_limit: int = 50

    @classmethod
    def from_env(cls) -> "OptimizerLimits":
        return cls(
            max_candidates=_int_env("QUERYFORGE_MAX_CANDIDATES", 3),
            max_retries=_int_env("QUERYFORGE_MAX_RETRIES", 2),
            llm_call_budget=_int_env("QUERYFORGE_LLM_CALL_BUDGET", 3),
            max_sql_length=_int_env("QUERYFORGE_MAX_SQL_LENGTH", 20_000),
            deadline_s=_float_env("QUERYFORGE_DEADLINE_S", 30.0),
            recursion_limit=_int_env("QUERYFORGE_RECURSION_LIMIT", 50),
        )
