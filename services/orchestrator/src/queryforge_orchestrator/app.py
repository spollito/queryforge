"""FastAPI entry point for the orchestrator service (SPEC.md §3, §10).

Endpoints:
- ``GET /health`` — liveness and the active LLM provider.
- ``POST /optimize`` — run the bounded LangGraph optimization workflow for one query.

Dependencies are built from the environment (``OptimizerDeps.from_env``): a mock LLM unless a
provider is configured, and the Java API client if ``QUERYFORGE_JAVA_API_URL`` is set
(otherwise an in-memory fake, so the service runs offline for development).
"""

from __future__ import annotations

import os

from fastapi import FastAPI

from . import __version__
from .graph import run_optimization
from .models import OptimizationRequest, OptimizationResponse

app = FastAPI(title="QueryForge Orchestrator", version=__version__)


@app.get("/health")
def health() -> dict[str, str]:
    provider = os.environ.get("QUERYFORGE_LLM_PROVIDER", "mock").strip().lower() or "mock"
    return {"status": "ok", "version": __version__, "llm_provider": provider}


@app.post("/optimize", response_model=OptimizationResponse)
def optimize(request: OptimizationRequest) -> OptimizationResponse:
    state = run_optimization(request)
    return OptimizationResponse.from_state(state)
