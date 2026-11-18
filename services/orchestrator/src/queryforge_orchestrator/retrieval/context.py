"""Assemble retrieved context for the prompt and check citation grounding (SPEC.md §5, §11).

- :func:`build_rules_block` renders retrieved rules as cite-able reference text.
- :func:`retrieve_rules` runs the lexical index for a query.
- :func:`ungrounded_rule_ids` reports cited rule ids that do not exist in the corpus — the
  signal behind the "explanation grounding accuracy" metric.
"""

from __future__ import annotations

from ..models import CandidateRewrite
from .index import LexicalIndex, RetrievedReference
from .rules import all_rule_ids, rule_by_id

_SHARED_INDEX = LexicalIndex()


def retrieve_rules(query: str, k: int = 3) -> list[RetrievedReference]:
    """Lexically retrieve up to ``k`` optimization rules relevant to ``query``."""
    return _SHARED_INDEX.search(query, k=k)


def build_rules_block(refs: list[RetrievedReference]) -> str:
    """Render retrieved rules for the prompt, each with its stable id to cite.

    Rendered as inert reference data; the prompt instructs the model to treat it as data,
    never instructions (SPEC.md §8).
    """
    if not refs:
        return "(no optimization rules retrieved)"
    return "\n".join(f"[{ref.rule_id}] {ref.title}: {ref.text}" for ref in refs)


def build_rules_block_from_ids(rule_ids: list[str]) -> str:
    """Render a rules block from stable ids (used when only ids are carried in graph state)."""
    refs = [
        RetrievedReference(rule_id=rule.id, title=rule.title, text=rule.summary, score=0.0)
        for rule_id in rule_ids
        if (rule := rule_by_id(rule_id)) is not None
    ]
    return build_rules_block(refs)


def ungrounded_rule_ids(candidate: CandidateRewrite, known_ids: frozenset[str] | None = None) -> list[str]:
    """Return cited rule ids that are not in the corpus (grounding failures).

    An empty list means every cited id is real. Order and duplicates from the candidate are
    preserved so callers can report exactly what was cited.
    """
    known = known_ids if known_ids is not None else all_rule_ids()
    return [rule_id for rule_id in candidate.referenced_rule_ids if rule_id not in known]
