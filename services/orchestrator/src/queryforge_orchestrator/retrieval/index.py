"""A small, dependency-free lexical retriever (SPEC.md §5).

TF-IDF with cosine similarity over the rule corpus. This is the "lightweight local index"
the spec calls sufficient initially; embeddings/pgvector are deferred until measurements
justify them. No external dependency, so retrieval is deterministic and test-friendly.
"""

from __future__ import annotations

import math
import re
from collections import Counter
from dataclasses import dataclass

from .rules import RULE_CORPUS, OptimizationRule

_TOKEN = re.compile(r"[a-z0-9_]+")


def _tokenize(text: str) -> list[str]:
    return _TOKEN.findall(text.lower())


@dataclass(frozen=True)
class RetrievedReference:
    """One retrieved rule with its relevance score (SPEC.md §5)."""

    rule_id: str
    title: str
    text: str
    score: float


class LexicalIndex:
    """TF-IDF cosine index over a fixed set of optimization rules."""

    def __init__(self, rules: tuple[OptimizationRule, ...] = RULE_CORPUS) -> None:
        self._rules = rules
        docs = [_tokenize(rule.document()) for rule in rules]
        n = len(docs) or 1
        df: Counter[str] = Counter()
        for tokens in docs:
            df.update(set(tokens))
        # Smoothed idf so a term in every doc still contributes a little.
        self._idf = {term: math.log((n + 1) / (count + 1)) + 1.0 for term, count in df.items()}
        self._doc_vectors = [self._vectorize(tokens) for tokens in docs]

    def _vectorize(self, tokens: list[str]) -> dict[str, float]:
        if not tokens:
            return {}
        tf = Counter(tokens)
        vector = {term: count * self._idf.get(term, 0.0) for term, count in tf.items()}
        norm = math.sqrt(sum(weight * weight for weight in vector.values()))
        if norm == 0.0:
            return {}
        return {term: weight / norm for term, weight in vector.items()}

    def search(self, query: str, k: int = 3) -> list[RetrievedReference]:
        """Return up to ``k`` rules most relevant to ``query``, most relevant first.

        Rules with a zero score (no shared terms) are omitted, so an empty or irrelevant
        query returns an empty list rather than noise.
        """
        query_vector = self._vectorize(_tokenize(query))
        if not query_vector:
            return []
        scored: list[RetrievedReference] = []
        for rule, doc_vector in zip(self._rules, self._doc_vectors):
            score = sum(weight * doc_vector.get(term, 0.0) for term, weight in query_vector.items())
            if score > 0.0:
                scored.append(
                    RetrievedReference(
                        rule_id=rule.id, title=rule.title, text=rule.summary, score=score
                    )
                )
        scored.sort(key=lambda ref: (-ref.score, ref.rule_id))
        return scored[:k]
