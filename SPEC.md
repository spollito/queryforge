# QueryForge — Enhancement Specification

> **Audience:** QueryForge contributors. This is the implementation-facing specification
> for enhancing QueryForge into an agentic, retrieval-augmented, formally-checked SQL
> optimization engine. It is grounded in the **actual** repository state as of
> 2026-11-18, not an idealized one. Progress is tracked in [TASKS.md](TASKS.md); the
> phased plan and exit criteria live in [docs/roadmap.md](docs/roadmap.md).

## 1. Scope and premise correction

The enhancement brief describes QueryForge as a mature optimizer that already has Z3
verification, an XGBoost model, LLM rewriting, and a benchmark suite to "retain and
strengthen." **It does not.** A full repository inspection (file tree, source grep,
README/roadmap/architecture docs) confirms the project is at **Phase 1 (query analysis),
in progress**. The table below is the authoritative baseline.

| Capability | Brief assumes | Reality | Classification |
| --- | --- | --- | --- |
| Apache Calcite | "integration" | Present, **only as a read-only parse gate** (`parsing/QueryParser`) | Reuse + extend |
| EXPLAIN capture + typed plan tree | — | Present (`plan/`) | Reuse |
| Bottleneck detectors (5) | — | Present (`analysis/detector/`) | Reuse |
| Read-only target DB safety (role, RO txn, timeout) | "restricted account" | Present (`connection/`) | Reuse (satisfies much of §8) |
| React workbench, sample DB, Testcontainers tests, CI | — | Present | Reuse |
| **Z3 semantic verification** | "existing implementation" | **Absent** (planned prose only) | **Net-new** |
| **LLM rewriting + structured output** | "review existing" | **Absent** | **Net-new** |
| **Retrieval / RAG** | — | **Absent** | **Net-new** |
| **XGBoost ranking + feature pipeline** | "existing model" | **Absent** | **Net-new** |
| **Python / FastAPI service** | — | **Absent** (no `services/`) | **Net-new** |
| **LangGraph orchestration** | — | **Absent** | **Net-new** |
| **Benchmark harness + baseline results** | "save existing results" | **Absent** | **Net-new** |

**Consequence:** this is ~90% net-new construction on a solid Phase-1 foundation, not an
enhancement of existing AI/ML components. No historical benchmark results exist to reuse;
every measured claim must come from a run of the enhanced pipeline (brief §16).

## 2. Chosen architecture (decisions)

Two decisions were taken at the start of this work and are binding unless revisited:

- **D-A — Spec-faithful agentic path.** Introduce a Python orchestration service now, with
  **LangGraph** as the explicit state-graph orchestrator and **Pydantic** typed state, per
  brief §4/§10. The Java Spring Boot app remains the public API and the authority for SQL
  parsing (Calcite), EXPLAIN capture, plan parsing, and all target-database access. This
  extends architecture-decision #2 (modular monolith): the Python service is the sanctioned
  second runtime, justified by LangGraph + the ML ecosystem.
- **D-B — Mock-first, pluggable LLM, off by default.** A provider-agnostic `LlmClient`
  interface with a deterministic mock used in all tests. A real provider (Claude via the
  Anthropic API is the default target) is configurable but **disabled unless a key is set**.
  No external calls, cost, or data egress occur until explicitly enabled. Satisfies brief §8
  ("avoid sending sensitive row-level data to external LLM providers") by construction:
  only schema metadata and SQL text — never result rows — are ever eligible to be sent.

## 3. Target architecture

```text
                         ┌──────────────────────────────────────────────┐
  Browser (React) ─HTTP─▶│  Java API (Spring Boot, Java 21)              │
                         │  • /api/v1/analyses   (Phase 1, unchanged)    │
                         │  • /api/v1/optimizations (new, proxies agent) │
                         │  • Calcite parse gate, EXPLAIN, plan parse     │
                         │  • TargetDatabase (read-only, the ONLY DB path)│
                         └───────┬───────────────────────┬───────────────┘
                                 │ HTTP/JSON (typed)      │ JDBC (read-only txn)
                                 ▼                        ▼
             ┌────────────────────────────────┐   ┌─────────────────────┐
             │ Python orchestrator (FastAPI)  │   │ Target PostgreSQL    │
             │ • LangGraph state graph        │   │ (sample-db locally)  │
             │ • Retrieval (schema + rules)   │   └─────────────────────┘
             │ • LlmClient (mock | provider)  │
             │ • Z3 verifier (z3-solver)      │
             │ • XGBoost ranker               │
             │ • calls back to Java for parse/│
             │   EXPLAIN/benchmark via HTTP   │
             └────────────────────────────────┘
```

The Python service never opens a database connection of its own. Every plan capture,
EXPLAIN, and benchmark execution goes back through the Java API, which keeps the single
read-only, timeout-bounded, rolled-back path to target databases (brief §8). This avoids
duplicating the safety model in a second language.

## 4. LangGraph orchestration (brief §4)

An explicit, bounded state graph — **not** an autonomous agent. Nodes:

1. `validate_input` — shape/size limits on the request.
2. `parse_sql` — call Java parse gate; reject non-read-only/unsupported here.
3. `retrieve_context` — schema metadata + optimization rules/examples (RAG, §5).
4. `inspect_plan` — call Java for the original `EXPLAIN (FORMAT JSON)` plan.
5. `generate_candidates` — LlmClient produces N structured rewrites (§6).
6. `validate_sql` — deterministic: each candidate back through the Java parse gate.
7. `verify_semantics` — Z3 on the supported fragment; differential testing elsewhere (§7).
8. `rank_candidates` — XGBoost over plan features (§9).
9. `benchmark` *(optional, controlled only)* — EXPLAIN ANALYZE via Java on test DBs (§11).
10. `finalize` — selected query + grounded explanation, or fallback to the original.

Typed `GraphState` (Pydantic) fields: `original_sql`, `schema_context`, `original_plan`,
`retrieved_refs`, `candidates`, `candidate_plans`, `verification_results`,
`ranking_predictions`, `retry_count`, `timings`, `result`, `errors`/`fallback`.

Conditional edges: reject invalid transforms, retry generation (counterexample feedback,
§7) up to a bounded `MAX_RETRIES`, and fall back to the original query when no candidate is
sufficiently validated. Hard limits: `MAX_ITERATIONS`, per-node timeouts, and a per-request
LLM-call budget. Deterministic nodes (parse, validate, verify, rank, DB access) do the
load-bearing work; the LLM is used only for candidate generation and explanation.

## 5. Retrieval-augmented generation (brief §5)

- **Structured retrieval** for schema metadata (tables, columns, types, PK/FK, constraints,
  indexes, `reltuples`/stats) and the original plan — fetched from the Java API, which reads
  the catalog in the same read-only transaction.
- **Lexical retrieval first** (BM25/TF-IDF over a small local corpus) for optimization rules
  and curated transformation examples, each with a **stable rule id** (e.g. `RULE-SARGABLE-001`).
  Embeddings/pgvector only if measurement justifies them — not initially.
- Retrieved text and DB metadata are treated as **untrusted data, never instructions**
  (brief §8). The prompt must require the LLM to cite applicable rule ids; the explanation is
  checked for grounding (§11 "explanation grounding accuracy").
- **Never** retrieve benchmark answers or measured target runtimes as optimization context.

## 6. Structured LLM output (brief §6)

Pydantic `CandidateRewrite`: `sql`, `strategy`, `referenced_rule_ids: list[str]`,
`explanation`, `assumptions: list[str]`, `semantic_risks: list[str]`. The model returns a
list of candidates. Malformed output and unsupported statements are rejected (re-validated
through the Java parse gate — the LLM's own claim of equivalence is never trusted). Prompt
templates are versioned (`prompt_version`) and recorded per run for reproducibility.

## 7. Semantic verification (brief §7)

Z3 (`z3-solver`) over an **explicitly supported fragment** (initially select-project-join
with conjunctive predicates). Documented limitations: NULL/three-valued logic, bag vs. set
(duplicate rows), joins, predicates, aggregation, ordering, type coercion, constraints.
Statuses: `VERIFIED | INVALID | UNKNOWN | UNSUPPORTED`. `UNKNOWN`/`UNSUPPORTED` are **never**
reported as verified. Counterexamples are generated where feasible and fed back into bounded
retries. Differential testing on controlled fixtures is **supplementary evidence, not a
proof**. No candidate is called "formally verified" unless the encoding + solver result
support it. Absent a sufficiently validated candidate, fall back to the original.

## 8. Guardrails and database safety (brief §8)

Mostly **already satisfied** by the Phase-1 Java safety model (architecture doc §"Safety
model"): read-only role, `SET TRANSACTION READ ONLY` + always rollback, per-txn statement
timeout, Calcite gate admitting exactly one read-only query, EXPLAIN-without-ANALYZE default.
New work: validate **every** LLM-generated candidate through the same gate before it reaches
the DB; enforce allowed schemas/tables; bound agent retries; restrict tool surface of the
Python service; keep EXPLAIN ANALYZE to controlled benchmark environments only; guarantee
row data never leaves for the LLM (D-B). The original query is always retained as fallback.

## 9. ML ranking (brief §9)

XGBoost ranker over plan-derived features (est. cost, est. rows, join count/algorithm, scan
characteristics, index use, plan depth, aggregation/sort operators, complexity, original-vs-
candidate plan deltas). Training targets come from **controlled offline benchmark** runtimes;
actual latency is **never** a prediction-time feature for the same candidate. Split train/eval
by query family/template to limit leakage. Baselines: PostgreSQL planner-cost selection.
Metrics: top-1/top-k accuracy, ranking quality, selected-query latency, regret vs. fastest
valid candidate, inference duration. Only redesign if a demonstrated limitation appears.

## 10. Java ↔ Python integration (brief §10)

- **Java:** public API, Calcite parse gate, EXPLAIN capture, plan parsing, all target-DB
  access, benchmark execution.
- **Python:** LangGraph orchestration, retrieval, LlmClient, Z3 verification, XGBoost.
- Typed request/response schemas on both sides; service timeouts, error handling, and a
  defined fallback (if the Python service is down, the Java API still serves Phase-1
  `/api/v1/analyses`). One service boundary only — no further microservices.

## 11. Evaluation (brief §11)

Reproducible suite comparing: **A** original, **B** Phase-1 engine, **C** RAG LLM rewriting,
**D** LangGraph + guardrails, **E** full pipeline. Fixed queries, fixed DB snapshot, fixed
environment; control caching/warm-up/ordering/repeats. Report query speedup **separately**
from optimization overhead. Metrics per brief §11 (latency median/p95, overhead, invalid-
rejection rate, acceptance rate, verification coverage, solver-timeout rate, candidate
success rate, top-1 accuracy, fallback rate, LLM tokens/latency, retrieval relevance,
explanation grounding). Include an **ablation** and, where useful, TPC-H-derived queries,
clearly labeled as personal experiments vs. official results.

## 12. Observability (brief §12)

Structured logs with a consistent `trace_id` across Java and Python for: LangGraph node
execution, workflow latency, LLM usage, retrieved context ids, verification results, ranking
outcomes, DB execution failures, retry counts, fallback decisions. A full per-query
optimization trace is inspectable. Logs must never expose credentials or row data.

## 13. Testing (brief §13)

Deterministic tests (mock LLM) for: SQL parse/validate, retrieval, structured-output
validation, LangGraph transitions, rejected/unsupported rewrites, Z3 counterexamples,
NULL/duplicate semantics, read-only restrictions, feature extraction, ranking, retry limits,
timeouts, fallback, and Java↔Python integration. Live-LLM tests stay optional. **All existing
Phase-1 regression tests are preserved and must keep passing.**

## 14. Non-goals / deferred

Kafka, Kubernetes, a dedicated vector DB, and pgvector are **out** unless measurements
justify them (brief §3). No production AWS deployment in this spec (that is roadmap Phase 4).

## 15. Acceptance criteria

Per brief §16, met when: Phase-1 functionality still works; LangGraph orchestrates a working
flow; retrieval demonstrably informs rewrites; LLM output is schema-validated; invalid
transforms are rejected; verification limits are documented; uncertain transforms fall back
safely; XGBoost ranks eligible candidates; reproducible comparative benchmarks exist;
tests cover critical failures; docs match the implementation. No fabricated or reused-as-new
benchmark results.
