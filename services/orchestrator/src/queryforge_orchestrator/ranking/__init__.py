"""Ranking of verified candidates (SPEC.md §9).

Defines the :class:`Ranker` interface and the planner-cost baseline. The XGBoost ranker is
added in Phase 5 behind the same interface; the planner-cost ranker remains the baseline it
is evaluated against (SPEC.md §11).
"""

from __future__ import annotations

import abc

from .base import PlannerCostRanker, Ranker

__all__ = ["Ranker", "PlannerCostRanker", "abc"]
