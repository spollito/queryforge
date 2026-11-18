"""In-memory fakes for deterministic tests and offline demos (no Java API, no database)."""

from __future__ import annotations

from ..models import ExplainMode
from .java_client import AnalyzeOutcome, JavaApiClient, JavaApiError


class FakeJavaApiClient(JavaApiClient):
    """Configurable fake.

    - ``outcomes`` maps a SQL string to a fixed :class:`AnalyzeOutcome`.
    - ``raise_for`` is a set of SQL strings that simulate a Java API outage (raise).
    - Any other SQL returns ``ok=True`` with ``default_cost``.
    """

    def __init__(
        self,
        outcomes: dict[str, AnalyzeOutcome] | None = None,
        raise_for: set[str] | None = None,
        default_cost: float = 100.0,
    ) -> None:
        self._outcomes = dict(outcomes or {})
        self._raise_for = set(raise_for or set())
        self._default_cost = default_cost
        self.calls: list[tuple[str, ExplainMode]] = []

    def analyze(self, sql: str, mode: ExplainMode = ExplainMode.ESTIMATE) -> AnalyzeOutcome:
        self.calls.append((sql, mode))
        if sql in self._raise_for:
            raise JavaApiError("simulated Java API outage")
        if sql in self._outcomes:
            return self._outcomes[sql]
        plan = {"plan": {"nodeType": "Seq Scan", "totalCost": self._default_cost}}
        return AnalyzeOutcome(ok=True, plan=plan, total_cost=self._default_cost)
