"""Provider-agnostic LLM client (SPEC.md §6, decision D-B).

The orchestrator depends only on the :class:`LlmClient` interface. Tests and the default
configuration use :class:`MockLlmClient`, which makes no network call. A real provider
(Claude via the Anthropic API) is added as a separate adapter and is only constructed when a
key is explicitly configured, so there is no external call, cost, or data egress by default.
"""

from __future__ import annotations

import abc
import os


class LlmUnavailableError(RuntimeError):
    """Raised when a real provider is requested but not configured."""


class LlmClient(abc.ABC):
    """Minimal interface: given a prompt, return the raw text completion.

    Callers pass the raw text to the structured-output parser, which validates it into
    typed candidates. The client never interprets the model's output as instructions.
    """

    @abc.abstractmethod
    def complete(self, prompt: str) -> str:
        """Return the model's raw text response to ``prompt``."""
        raise NotImplementedError

    @property
    @abc.abstractmethod
    def name(self) -> str:
        """Stable identifier for logging/observability (SPEC.md §12)."""
        raise NotImplementedError


class MockLlmClient(LlmClient):
    """Deterministic client for tests and the default (offline) configuration.

    Returns a fixed raw response, so a workflow run is fully reproducible without a provider.
    Supply ``raw_response`` to drive edge cases (malformed JSON, unsupported SQL, too many
    candidates) in tests.
    """

    #: A well-formed, schema-valid default response with one candidate.
    DEFAULT_RESPONSE = (
        '[{"sql": "SELECT id FROM orders WHERE customer_id = 42",'
        ' "strategy": "noop-passthrough",'
        ' "referenced_rule_ids": ["RULE-IDENTITY-000"],'
        ' "explanation": "Placeholder candidate returned by the mock client.",'
        ' "assumptions": [],'
        ' "semantic_risks": []}]'
    )

    def __init__(self, raw_response: str | None = None) -> None:
        self._raw = raw_response if raw_response is not None else self.DEFAULT_RESPONSE
        self.calls: list[str] = []

    def complete(self, prompt: str) -> str:
        self.calls.append(prompt)
        return self._raw

    @property
    def name(self) -> str:
        return "mock"


def default_client() -> LlmClient:
    """Return the configured client.

    Returns :class:`MockLlmClient` unless ``QUERYFORGE_LLM_PROVIDER`` names a real provider
    *and* its key is set. This keeps the service offline and free by default (decision D-B).
    """
    provider = os.environ.get("QUERYFORGE_LLM_PROVIDER", "mock").strip().lower()
    if provider in ("", "mock"):
        return MockLlmClient()
    if provider == "anthropic":
        # Imported lazily so the base install needs no provider SDK.
        from .anthropic_client import AnthropicLlmClient

        return AnthropicLlmClient()
    raise LlmUnavailableError(f"Unknown LLM provider: {provider!r}")
