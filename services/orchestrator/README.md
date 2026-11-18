# QueryForge Orchestrator (Python)

The agentic orchestration service for QueryForge (see [SPEC.md](../../SPEC.md) §3, §10). It
owns LangGraph orchestration, retrieval, the pluggable LLM client, Z3 verification, and
XGBoost ranking. It **never** connects to a database directly: plan capture, EXPLAIN, and
benchmark execution all go back through the Java API, which keeps the single read-only path
to target databases.

> **Status:** Phase 2 foundation. Implemented so far: typed models, the mock-first pluggable
> LLM client, structured-output validation, versioned prompts, and a FastAPI health endpoint.
> The LangGraph workflow, retrieval, Z3, and ranking land in later phases (see
> [../../TASKS.md](../../TASKS.md)).

## LLM is mock-first and off by default

The default `LlmClient` is a deterministic mock — no network call, no cost, no data egress.
A real provider (Claude via the Anthropic API) is enabled only when configured:

```bash
export QUERYFORGE_LLM_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-...
pip install -e '.[llm-anthropic]'
```

Only schema metadata and SQL text are ever eligible to be sent to a provider — never result
rows (SPEC.md §8).

## Develop

```bash
cd services/orchestrator
python3 -m venv .venv && source .venv/bin/activate
pip install -e '.[dev]'
pytest                       # run the test suite
uvicorn queryforge_orchestrator.app:app --reload   # run the service (health only, for now)
```

## Layout

```text
src/queryforge_orchestrator/
  models.py            Pydantic GraphState, CandidateRewrite, VerificationResult, enums
  llm/
    client.py          LlmClient interface, MockLlmClient, default_client()
    anthropic_client.py  Real provider adapter (lazy; off unless configured)
    structured.py      Validate raw LLM output into typed candidates
  prompts/__init__.py  Versioned prompt templates
  app.py               FastAPI entry point
tests/                 pytest suite (mock LLM, deterministic)
```
