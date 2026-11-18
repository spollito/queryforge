"""Curated optimization-rule corpus with stable ids (SPEC.md §5).

Each rule has a stable id (``RULE-<TOPIC>-NNN``) that candidates cite and that the grounding
check validates. The ``summary`` text is what the lexical index scores against. This is
QueryForge's own curated, trusted reference data — distinct from database metadata, which is
untrusted.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

RULE_ID_PATTERN = re.compile(r"^RULE-[A-Z0-9]+(?:-[A-Z0-9]+)*-\d{3}$")


@dataclass(frozen=True)
class OptimizationRule:
    id: str
    title: str
    summary: str
    tags: tuple[str, ...] = field(default_factory=tuple)

    def document(self) -> str:
        """Text the lexical index scores against."""
        return f"{self.title}. {self.summary} {' '.join(self.tags)}"


RULE_CORPUS: tuple[OptimizationRule, ...] = (
    OptimizationRule(
        id="RULE-IDENTITY-000",
        title="Identity (no-op passthrough)",
        summary=(
            "Return the original query unchanged. Used as a safe baseline candidate when no "
            "transformation is warranted."
        ),
        tags=("identity", "baseline", "fallback"),
    ),
    OptimizationRule(
        id="RULE-SARGABLE-001",
        title="Make predicates sargable",
        summary=(
            "Rewrite a predicate so the planner can use an index: compare a bare indexed "
            "column to a constant instead of wrapping the column in a function or arithmetic. "
            "For example replace date(created_at) = '2024-01-01' with a half-open range "
            "created_at >= '2024-01-01' and created_at < '2024-01-02'. A function on the "
            "column forces a sequential scan even when an index exists."
        ),
        tags=("index", "sargable", "sequential scan", "filter", "predicate", "function"),
    ),
    OptimizationRule(
        id="RULE-INDEX-FK-001",
        title="Index join and foreign-key columns",
        summary=(
            "A join or correlated lookup on an unindexed column makes the inner side a "
            "repeated sequential scan inside a nested loop. Suggest a btree index on the "
            "foreign-key or join column so the planner can use an index scan or a hash join."
        ),
        tags=("index", "join", "foreign key", "nested loop", "sequential scan"),
    ),
    OptimizationRule(
        id="RULE-DECORRELATE-001",
        title="Decorrelate a subquery into a join",
        summary=(
            "A correlated subquery referencing the outer row runs once per outer row. Rewrite "
            "it as an explicit join or a grouped derived table so it executes once, letting "
            "the planner choose a hash or merge join."
        ),
        tags=("subquery", "correlated", "decorrelate", "join", "nested loop"),
    ),
    OptimizationRule(
        id="RULE-EXISTS-001",
        title="Prefer EXISTS over IN for semi-joins",
        summary=(
            "For membership tests against a large subquery, EXISTS expresses a semi-join that "
            "can stop at the first match and avoids materializing or de-duplicating the IN "
            "list. Prefer EXISTS (or NOT EXISTS) when only existence matters."
        ),
        tags=("exists", "in", "semi join", "anti join", "subquery"),
    ),
    OptimizationRule(
        id="RULE-LIMIT-INDEX-001",
        title="Use an index to satisfy ORDER BY ... LIMIT",
        summary=(
            "An ORDER BY with a small LIMIT over a large table causes a full sort that can "
            "spill to disk. An index on the sort key lets the planner read rows in order and "
            "stop after LIMIT rows, avoiding the sort entirely."
        ),
        tags=("order by", "limit", "sort", "spill", "index", "top-n"),
    ),
    OptimizationRule(
        id="RULE-PROJECTION-001",
        title="Project only the needed columns",
        summary=(
            "SELECT * fetches and returns columns the query does not use, widening rows, "
            "defeating index-only scans, and increasing I/O. List only the required columns."
        ),
        tags=("projection", "select star", "columns", "index-only scan", "width"),
    ),
    OptimizationRule(
        id="RULE-PREDICATE-PUSHDOWN-001",
        title="Push filters below joins and aggregations",
        summary=(
            "Apply the most selective filters as early as possible so joins and aggregations "
            "process fewer rows. Move WHERE predicates on a single table into that table's "
            "scan rather than filtering after the join."
        ),
        tags=("predicate pushdown", "filter", "join", "aggregation", "selectivity"),
    ),
    OptimizationRule(
        id="RULE-UNION-ALL-001",
        title="Use UNION ALL when duplicates are impossible or irrelevant",
        summary=(
            "UNION removes duplicates with a sort or hash over the combined result. When the "
            "branches cannot overlap, or duplicates do not matter, UNION ALL skips that step."
        ),
        tags=("union", "union all", "distinct", "duplicates", "sort"),
    ),
    OptimizationRule(
        id="RULE-EXTENDED-STATS-001",
        title="Add extended statistics for correlated columns",
        summary=(
            "When a row-count estimate is far off because two filtered columns are correlated, "
            "the planner assumes independence and under- or over-estimates. CREATE STATISTICS "
            "with the dependencies or ndistinct kinds, then ANALYZE, improves the estimate and "
            "the plan choice."
        ),
        tags=("statistics", "row estimate", "correlated", "analyze", "cardinality"),
    ),
)


def all_rule_ids() -> frozenset[str]:
    return frozenset(rule.id for rule in RULE_CORPUS)


def rule_by_id(rule_id: str) -> OptimizationRule | None:
    for rule in RULE_CORPUS:
        if rule.id == rule_id:
            return rule
    return None
