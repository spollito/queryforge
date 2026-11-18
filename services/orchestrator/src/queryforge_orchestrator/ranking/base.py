"""Ranker interface and the PostgreSQL planner-cost baseline (SPEC.md §9, §11)."""

from __future__ import annotations

import abc

from ..models import RankingPrediction


class Ranker(abc.ABC):
    @abc.abstractmethod
    def rank(self, items: list[tuple[int, float | None]]) -> list[RankingPrediction]:
        """Rank eligible candidates.

        ``items`` is a list of ``(candidate_index, estimated_total_cost)``. Returns a
        :class:`RankingPrediction` per item, ordered best-first, with 1-based ``predicted_rank``.
        """
        raise NotImplementedError


class PlannerCostRanker(Ranker):
    """Baseline ranker: prefer the lowest planner-estimated total cost.

    This is the baseline the learned ranker is evaluated against (SPEC.md §11), and a sound
    default on its own. Candidates with no cost estimate rank last.
    """

    def rank(self, items: list[tuple[int, float | None]]) -> list[RankingPrediction]:
        def sort_key(item: tuple[int, float | None]) -> tuple[int, float]:
            index, cost = item
            # (has_no_cost, cost) — real costs first, ascending; ties broken by index.
            return (1, 0.0) if cost is None else (0, cost)

        ordered = sorted(items, key=lambda item: (*sort_key(item), item[0]))
        predictions: list[RankingPrediction] = []
        for rank, (index, cost) in enumerate(ordered, start=1):
            # Score: higher is better. Negative cost gives a monotone, comparable signal.
            score = -(cost if cost is not None else float("inf"))
            predictions.append(
                RankingPrediction(candidate_index=index, score=score, predicted_rank=rank)
            )
        return predictions
