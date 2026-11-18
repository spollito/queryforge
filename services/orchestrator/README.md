# QueryForge Orchestrator (Python)

The agentic orchestration service for QueryForge (see [SPEC.md](../../SPEC.md) §3, §10). It
owns LangGraph orchestration, retrieval, the pluggable LLM client, Z3 verification, and
XGBoost ranking. It **never** connects to a database directly: plan capture, EXPLAIN, and
benchmark execution all go back through the Java API, which keeps the single read-only path
to target databases.

> **Status:** Phases 2–3. Implemented: typed models, the mock-first pluggable LLM client,
> structured-output validation, versioned prompts, retrieval (rule corpus + TF-IDF index +
> schema context), and the **bounded LangGraph workflow** with retries, fallback, and a
> `POST /optimize` endpoint. Verification (Z3) and ranking (XGBoost) land behind their
> interfaces in Phases 4–5; the current defaults are a conservative `UnknownVerifier` and the
> planner-cost ranker. See [../../TASKS.md](../../TASKS.md).

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
  config.py            OptimizerLimits (retries, LLM budget, deadline, recursion limit)
  llm/
    client.py          LlmClient interface, MockLlmClient, default_client()
    anthropic_client.py  Real provider adapter (lazy; off unless configured)
    structured.py      Validate raw LLM output into typed candidates
  prompts/__init__.py  Versioned prompt templates
  retrieval/
    rules.py           Optimization-rule corpus with stable RULE-* ids
    index.py           Dependency-free TF-IDF lexical retriever
    schema.py          Typed SchemaContext; static + HTTP schema providers
    context.py         Prompt-context builder + rule-citation grounding check
  integration/
    java_client.py     JavaApiClient (HTTP) — the only path to the database
    fakes.py           FakeJavaApiClient for offline/tests
  verification/__init__.py  Verifier interface + UnknownVerifier default (Z3 in Phase 4)
  ranking/             Ranker interface + PlannerCostRanker baseline (XGBoost in Phase 5)
  graph/workflow.py    The bounded LangGraph StateGraph + run_optimization()
  app.py               FastAPI entry point (/health, /optimize)
tests/                 pytest suite (mock LLM, deterministic) — 55 tests
```

## Run the workflow offline

```python
from queryforge_orchestrator.graph import run_optimization
from queryforge_orchestrator.models import OptimizationRequest

state = run_optimization(OptimizationRequest(sql="SELECT id FROM orders WHERE customer_id = 42"))
print(state.result_sql, state.used_fallback, state.trace_id)
```

With no `QUERYFORGE_JAVA_API_URL` set, a fake Java client is used so the graph runs without a
database. Point it at a running Java API (`export QUERYFORGE_JAVA_API_URL=http://localhost:8080`)
to validate candidates and capture real plans.
