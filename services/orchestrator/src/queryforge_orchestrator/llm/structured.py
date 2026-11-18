"""Validate raw LLM output into typed candidates (SPEC.md §6).

Rules:
- Malformed JSON is rejected.
- The payload must be a JSON array, or an object with a ``candidates`` array.
- Each item is validated against :class:`CandidateRewrite`; items that fail validation are
  dropped (and counted), not coerced.
- Candidates whose SQL is obviously not a read-only query are dropped. This is defence in
  depth only: the **authoritative** gate is the Java Calcite parser, which every surviving
  candidate must still pass before it reaches a database (SPEC.md §8).
- If no valid candidate remains, a :class:`StructuredOutputError` is raised so the caller can
  fall back to the original query.
"""

from __future__ import annotations

import json
import re

from pydantic import ValidationError

from ..models import CandidateRewrite


class StructuredOutputError(ValueError):
    """Raised when the LLM output yields no usable candidate."""


# Leading keyword that marks a read-only query. WITH is allowed (CTEs); data-changing
# statements and DDL are not. This mirrors, loosely, the Java parse gate's intent.
_READ_ONLY_LEADING = re.compile(r"^\s*(select|with|values|table|\()", re.IGNORECASE)
# Obvious data-changing / DDL / multi-statement markers that disqualify a candidate here.
_FORBIDDEN = re.compile(
    r"(?is)\b(insert|update|delete|merge|truncate|drop|alter|create|grant|revoke|"
    r"vacuum|analyze|copy|call|do|set|reset|begin|commit|rollback)\b"
)


def _strip_sql_comments(sql: str) -> str:
    sql = re.sub(r"/\*.*?\*/", " ", sql, flags=re.DOTALL)
    sql = re.sub(r"--[^\n]*", " ", sql)
    return sql.strip()


def looks_read_only(sql: str) -> bool:
    """Conservative check that ``sql`` is a single read-only query.

    Not a parser. A ``False`` here always drops the candidate; a ``True`` still defers to the
    Java Calcite gate for the authoritative decision.
    """
    stripped = _strip_sql_comments(sql)
    if not stripped:
        return False
    # Reject stacked statements (ignoring a single trailing semicolon).
    if ";" in stripped.rstrip(";"):
        return False
    if not _READ_ONLY_LEADING.match(stripped):
        return False
    # A CTE may legitimately be followed by SELECT; forbid data-changing keywords anywhere.
    # (WITH ... INSERT is read-write and must be rejected.)
    body = stripped
    if _FORBIDDEN.search(body):
        return False
    return True


def parse_candidates(raw: str, max_candidates: int) -> list[CandidateRewrite]:
    """Parse and validate raw LLM text into at most ``max_candidates`` candidates."""
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise StructuredOutputError(f"LLM output is not valid JSON: {exc}") from exc

    if isinstance(payload, dict):
        payload = payload.get("candidates", [])
    if not isinstance(payload, list):
        raise StructuredOutputError("LLM output must be a JSON array of candidates")

    valid: list[CandidateRewrite] = []
    for item in payload:
        if not isinstance(item, dict):
            continue
        try:
            candidate = CandidateRewrite.model_validate(item)
        except ValidationError:
            continue
        if not looks_read_only(candidate.sql):
            continue
        valid.append(candidate)
        if len(valid) >= max_candidates:
            break

    if not valid:
        raise StructuredOutputError("No schema-valid, read-only candidate in LLM output")
    return valid
