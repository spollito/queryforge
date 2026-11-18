# QueryForge — Enhancement Task Tracker

Living checklist for the agentic enhancement. Kept in sync with the code (brief §17).
Specification: [SPEC.md](SPEC.md). Baseline: [docs/baseline.md](docs/baseline.md).

Status key: `[x]` done · `[~]` in progress · `[ ]` not started.

Phases follow the enhancement brief §15. They are **incremental**: each lands working,
tested code and keeps the Phase-1 baseline green before the next begins.

---

## Phase 1 — Assessment, baseline, specification

- [x] Full repository inspection; classify each brief requirement (reuse / modify / net-new) — [SPEC §1](SPEC.md)
- [x] Validate and preserve the Phase-1 baseline (API `mvnw verify`, web lint/test/build) — [docs/baseline.md](docs/baseline.md)
- [x] Write grounded `SPEC.md` and `TASKS.md`
- [x] Record architecture decisions D-A (spec-faithful agentic path) and D-B (mock-first LLM) — [SPEC §2](SPEC.md)
- [x] Add `services/` to repo layout docs and `.gitignore` for Python artifacts

## Phase 2 — Context retrieval + structured LLM output (Python service foundation)

- [x] Scaffold `services/orchestrator` (FastAPI, Pydantic, pytest, pinned deps) — 27 tests green
- [x] Pydantic domain models: `GraphState`, `CandidateRewrite`, `VerificationResult`, enums — `models.py`
- [x] `LlmClient` interface + deterministic `MockLlmClient` (D-B); provider adapter disabled unless key set — `llm/`
- [x] Structured-output validation: parse/validate LLM response → `list[CandidateRewrite]`; reject malformed/unsupported — `llm/structured.py`
- [x] Versioned prompt templates (`prompt_version` recorded per run) — `prompts/`
- [x] Retrieval: local rule/example corpus with stable ids (`RULE-*`); dependency-free TF-IDF retriever — `retrieval/rules.py`, `retrieval/index.py`
- [~] Structured schema retrieval: Python side done (`retrieval/schema.py`: typed `SchemaContext`, static + HTTP providers); **Java `/api/v1/schema` endpoint pending Phase 3**
- [x] Treat retrieved text + metadata as untrusted data, never instructions — prompt + `retrieval/__init__.py`
- [x] Unit tests: retrieval relevance, structured-output validation, rule-id citation grounding — `tests/test_index.py`, `test_retrieval_context.py`, `test_schema.py`
- [ ] Evaluate retrieval-enabled vs. non-retrieval rewriting (harness hook; real numbers later)

## Phase 3 — LangGraph orchestration + bounded retries

- [x] `GraphState` wired into a LangGraph `StateGraph` with the 10 nodes (SPEC §4) — `graph/workflow.py`
- [x] Deterministic nodes call back to Java for parse / plan capture (typed `JavaApiClient`: HTTP + fake) — `integration/`
- [x] Conditional edges: reject invalid, retry generation, fall back to original
- [x] Hard limits: recursion limit, `MAX_RETRIES`, LLM-call budget, overall wall-clock deadline — `config.py` (per-node timeouts deferred)
- [x] Verifier + Ranker interfaces with safe defaults (UnknownVerifier, PlannerCostRanker) so Phase 4/5 slot in — `verification/`, `ranking/`
- [x] `FastAPI` endpoint `POST /optimize` — `app.py`
- [x] Java `/api/v1/optimizations` proxy (RestClient, timeout, fallback to Phase-1 analysis) — `api/.../optimization/`
- [x] Java `/api/v1/schema` endpoint (catalog metadata: columns, PK/FK, indexes, reltuples) — `api/.../schema/`
- [x] Java↔Python typed schemas; Python `HttpJavaApiClient` + `HttpSchemaProvider` wired via `QUERYFORGE_JAVA_API_URL`
- [x] Tests: graph transitions, retry limits, fallback, candidate/original rejection, budget bounds — `tests/test_workflow.py`; Java `SchemaApiTests`, `OptimizationApiTests`

## Phase 4 — Semantic verification + deterministic guardrails

- [x] Z3 (`z3-solver`) verifier over the supported fragment (single-table select-project, integer conjunctive predicates) — `verification/z3_verifier.py`
- [x] Status model `VERIFIED | INVALID | UNKNOWN | UNSUPPORTED`; UNKNOWN/UNSUPPORTED never treated as verified (enforced by `is_trustworthy_equivalence`)
- [x] Three-valued (NULL-aware) encoding so `a = a` ≠ `TRUE`; bag-semantics argument documented in the module
- [x] Counterexample generation → fed back into bounded retries via `verification_feedback`
- [x] `default_verifier()` wired into `from_env` (falls back to `UnknownVerifier` if z3/sqlglot absent)
- [x] Guardrail: every candidate re-validated through the Java Calcite gate before DB (`validate_sql` node)
- [x] Row-level data never eligible for LLM egress: prompt uses schema metadata + plan (costs/estimates, no rows) + rules + SQL only
- [x] Tests: Z3 VERIFIED/INVALID/UNSUPPORTED, boundary + NULL counterexamples, end-to-end select + reject — `tests/test_z3_verifier.py`, `test_workflow.py`
- [ ] Differential testing on controlled fixtures (supplementary evidence; deferred — Z3 covers the fragment, differential extends beyond it)
- [ ] Extend fragment: joins, DISTINCT/GROUP BY, non-integer types (follow-on)

## Phase 5 — ML ranking + full integration

- [ ] Plan-feature extraction (cost, rows, joins, scans, index use, depth, agg/sort, deltas)
- [ ] Controlled offline benchmark run to build training targets (no latency as a predict-time feature)
- [ ] XGBoost ranker; train/eval split by query family/template
- [ ] Baselines: PostgreSQL planner-cost selection
- [ ] Integrate ranking into the graph `rank_candidates` node
- [ ] Metrics: top-1/top-k, ranking quality, selected latency, regret, inference duration; feature importance
- [ ] Tests: feature extraction, ranking predictions

## Phase 6 — Benchmarks, ablation, observability, docs

- [ ] Reproducible benchmark suite: configs A–E on fixed queries/snapshot/env (SPEC §11)
- [ ] Control caching/warm-up/ordering/repeats; report speedup separate from overhead
- [ ] Ablation study (which components help correctness/perf/reliability)
- [ ] Optional TPC-H-derived queries, labeled as personal experiments
- [ ] Observability: `trace_id` across Java + Python; per-query optimization trace; no secrets/row data in logs
- [ ] Update `README.md`, `docs/architecture.md`, `docs/roadmap.md`; reproducible demo walkthrough
- [ ] Record measured results (run fresh — never reuse/fabricate)

---

## Guardrails on this effort (standing)

- Preserve all Phase-1 functionality, APIs, tests, and architecture decisions; don't rewrite
  working components just to adopt a framework.
- Keep the baseline green at every step.
- No fabricated improvements; no historical results reused as evidence (brief §16).
- Keep `SPEC.md` and `TASKS.md` synchronized with the actual code.
