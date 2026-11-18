"""FastAPI entry point for the orchestrator service (SPEC.md §3, §10).

Phase 2 exposes only a health endpoint and reports the active LLM provider. The ``/optimize``
endpoint and the LangGraph workflow land in Phase 3 (see TASKS.md).
"""

from __future__ import annotations

import os

from fastapi import FastAPI

from . import __version__

app = FastAPI(title="QueryForge Orchestrator", version=__version__)


@app.get("/health")
def health() -> dict[str, str]:
    provider = os.environ.get("QUERYFORGE_LLM_PROVIDER", "mock").strip().lower() or "mock"
    return {"status": "ok", "version": __version__, "llm_provider": provider}
