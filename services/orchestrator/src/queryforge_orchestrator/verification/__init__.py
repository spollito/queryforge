"""Semantic verification of candidate rewrites (SPEC.md §7).

Defines the :class:`Verifier` interface and the conservative default. The Z3-based verifier
and differential testing are added in Phase 4; they slot in behind this interface without
changing the workflow.
"""

from __future__ import annotations

import abc

from ..models import VerificationResult, VerificationStatus


class Verifier(abc.ABC):
    @abc.abstractmethod
    def verify(self, original_sql: str, candidate_sql: str) -> VerificationResult:
        """Decide whether ``candidate_sql`` is equivalent to ``original_sql``."""
        raise NotImplementedError


class UnknownVerifier(Verifier):
    """Default until Z3 lands: declines to decide.

    Returns ``UNKNOWN`` for every pair. Because ``UNKNOWN`` is not a trustworthy equivalence
    (SPEC.md §7), the workflow treats it as "not verified" and falls back to the original
    query — the safe behaviour before real verification exists.
    """

    def verify(self, original_sql: str, candidate_sql: str) -> VerificationResult:
        if candidate_sql.strip() == original_sql.strip():
            return VerificationResult(
                status=VerificationStatus.VERIFIED,
                method="identity",
                detail="Candidate is textually identical to the original query.",
            )
        return VerificationResult(
            status=VerificationStatus.UNKNOWN,
            method="none",
            detail="No verifier configured; equivalence is undecided.",
        )
