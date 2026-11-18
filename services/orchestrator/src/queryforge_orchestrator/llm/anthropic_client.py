"""Real LLM provider adapter: Claude via the Anthropic API (decision D-B).

Only imported and constructed when ``QUERYFORGE_LLM_PROVIDER=anthropic`` is set. Requires the
``llm-anthropic`` extra (``pip install -e '.[llm-anthropic]'``) and ``ANTHROPIC_API_KEY``.
Nothing here runs by default; the service is offline unless explicitly enabled.

Safety (SPEC.md §8): the caller is responsible for building prompts from schema metadata and
SQL text only — never result rows. This adapter does not read the database.
"""

from __future__ import annotations

import os

from .client import LlmClient, LlmUnavailableError

#: Default to the latest capable Claude model (see claude-api guidance).
DEFAULT_MODEL = os.environ.get("QUERYFORGE_LLM_MODEL", "claude-opus-4-8")


class AnthropicLlmClient(LlmClient):
    def __init__(self, model: str = DEFAULT_MODEL) -> None:
        api_key = os.environ.get("ANTHROPIC_API_KEY")
        if not api_key:
            raise LlmUnavailableError(
                "QUERYFORGE_LLM_PROVIDER=anthropic but ANTHROPIC_API_KEY is not set"
            )
        try:
            import anthropic
        except ImportError as exc:  # pragma: no cover - exercised only when extra missing
            raise LlmUnavailableError(
                "anthropic SDK not installed; run pip install -e '.[llm-anthropic]'"
            ) from exc
        self._model = model
        self._client = anthropic.Anthropic(api_key=api_key)

    def complete(self, prompt: str) -> str:  # pragma: no cover - requires live key
        message = self._client.messages.create(
            model=self._model,
            max_tokens=2048,
            messages=[{"role": "user", "content": prompt}],
        )
        return "".join(block.text for block in message.content if block.type == "text")

    @property
    def name(self) -> str:
        return f"anthropic:{self._model}"
