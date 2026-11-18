"""Client for the Java analysis API (SPEC.md §10).

The orchestrator uses this to validate SQL and capture execution plans. The Java API is the
authoritative Calcite parse gate and the single read-only, timeout-bounded path to the target
database — the Python service never connects to a database itself.

``analyze`` maps the Java ``POST /api/v1/analyses`` contract to an :class:`AnalyzeOutcome`:
- HTTP 200 → ``ok=True`` with the captured plan and its root total cost.
- HTTP 422 (``unsupported-query`` / ``query-error``) → ``ok=False`` with the error code; the
  SQL was rejected, which is a normal outcome for an invalid candidate, not an exception.
- Any other failure (503 target-unavailable, 504 timeout, transport errors) raises
  :class:`JavaApiError`, so the workflow can fall back safely.
"""

from __future__ import annotations

import abc
from dataclasses import dataclass

from ..models import ExplainMode


class JavaApiError(RuntimeError):
    """Raised when the Java API is unreachable or returns an unexpected error."""


@dataclass(frozen=True)
class AnalyzeOutcome:
    ok: bool
    plan: dict | None = None
    total_cost: float | None = None
    error_code: str | None = None

    @staticmethod
    def rejected(error_code: str) -> "AnalyzeOutcome":
        return AnalyzeOutcome(ok=False, error_code=error_code)


class JavaApiClient(abc.ABC):
    @abc.abstractmethod
    def analyze(self, sql: str, mode: ExplainMode = ExplainMode.ESTIMATE) -> AnalyzeOutcome:
        """Validate ``sql`` and capture its plan via the Java API."""
        raise NotImplementedError


def _root_total_cost(plan: dict | None) -> float | None:
    if not plan:
        return None
    root = plan.get("plan") if "plan" in plan else plan
    if isinstance(root, dict):
        cost = root.get("totalCost")
        if isinstance(cost, (int, float)):
            return float(cost)
    return None


class HttpJavaApiClient(JavaApiClient):
    def __init__(self, base_url: str, timeout_s: float = 15.0) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout_s = timeout_s

    def analyze(
        self, sql: str, mode: ExplainMode = ExplainMode.ESTIMATE
    ) -> AnalyzeOutcome:  # pragma: no cover - exercised against a live Java API
        import httpx

        try:
            response = httpx.post(
                f"{self._base_url}/api/v1/analyses",
                json={"sql": sql, "mode": mode.value},
                timeout=self._timeout_s,
            )
        except httpx.HTTPError as exc:
            raise JavaApiError(f"Java API request failed: {exc}") from exc

        if response.status_code == 200:
            body = response.json()
            return AnalyzeOutcome(
                ok=True, plan=body, total_cost=_root_total_cost(body)
            )
        if response.status_code == 422:
            code = _problem_code(response)
            return AnalyzeOutcome.rejected(code)
        raise JavaApiError(
            f"Java API returned {response.status_code}: {_problem_code(response)}"
        )


def _problem_code(response) -> str:  # pragma: no cover - thin helper
    try:
        return str(response.json().get("code", f"http-{response.status_code}"))
    except Exception:
        return f"http-{response.status_code}"
