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
- [ ] Retrieval: local rule/example corpus with stable ids (`RULE-*`); lexical (BM25/TF-IDF) retriever
- [ ] Structured schema retrieval via Java API (tables/columns/types/PK-FK/indexes/stats)
- [ ] Treat retrieved text + metadata as untrusted data, never instructions
- [ ] Unit tests: retrieval relevance, structured-output validation, rule-id citation
- [ ] Evaluate retrieval-enabled vs. non-retrieval rewriting (harness hook; real numbers later)

## Phase 3 — LangGraph orchestration + bounded retries

- [ ] `GraphState` wired into a LangGraph `StateGraph` with the 10 nodes (SPEC §4)
- [ ] Deterministic nodes call back to Java for parse / EXPLAIN (typed HTTP client)
- [ ] Conditional edges: reject invalid, retry generation, fall back to original
- [ ] Hard limits: `MAX_ITERATIONS`, `MAX_RETRIES`, per-node timeouts, LLM-call budget
- [ ] `FastAPI` endpoint `POST /optimize`; Java `/api/v1/optimizations` proxy with timeout + fallback
- [ ] Java↔Python typed request/response schemas; fallback to Phase-1 analysis if Python is down
- [ ] Tests: graph transitions, retry limits, timeout handling, fallback behavior

## Phase 4 — Semantic verification + deterministic guardrails

- [ ] Z3 (`z3-solver`) verifier over the supported fragment (SPJ + conjunctive predicates)
- [ ] Status model `VERIFIED | INVALID | UNKNOWN | UNSUPPORTED`; never report UNKNOWN/UNSUPPORTED as verified
- [ ] Counterexample generation → feedback into bounded retries
- [ ] Differential testing on controlled fixtures (supplementary evidence only)
- [ ] Document supported constructs + limits (NULL, bag semantics, joins, agg, order, coercion, constraints)
- [ ] Guardrails: every candidate re-validated through Java parse gate before DB; allowed schemas/tables; restricted tool surface
- [ ] Confirm row-level data never eligible for LLM egress (assert in code + test)
- [ ] Tests: Z3 counterexamples, NULL/duplicate semantics, read-only restriction, unsupported rejection

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
