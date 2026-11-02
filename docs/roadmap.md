# Roadmap

QueryForge is built in phases. Each phase ends with working, tested software and updated documentation. A phase is done when its exit criteria are met, not when its task list is empty.

Status key: **Done**, **Next**, **Planned**.

## Phase 0: Foundation (Done)

Goal: a clean, working repository that later phases build on without rework.

- [x] Monorepo layout, README, CONTRIBUTING, MIT license, issue templates
- [x] Spring Boot 4.1 API on Java 21 with Maven Wrapper
- [x] Actuator health with liveness and readiness probes; component details hidden
- [x] Environment-based configuration from a shared, git-ignored `.env`
- [x] React + TypeScript web client with Vite, Tailwind CSS, ESLint, and a typed API client
- [x] PostgreSQL 18 via Docker Compose: persistent volume, health check, loopback-only port
- [x] Integration tests against real PostgreSQL (Testcontainers); web unit tests (Vitest)
- [x] GitHub Actions CI: build, lint, and test for API and web
- [x] Architecture documentation and this roadmap

**Exit criteria (met):** the API starts and reports health, the web app builds and shows live status, PostgreSQL runs through Compose, and all checks pass locally and in CI.

## Phase 1: Query Analysis (Next)

Goal: given a SQL query and a target database, explain why it is slow and suggest deterministic improvements, with measured evidence.

- [ ] **Persistence foundation:** Flyway migrations and the first QueryForge schema (analysis runs, captured plans, findings)
- [ ] **Target connection management:** register target PostgreSQL databases; enforce read-only sessions, `statement_timeout`, and connection limits; never expose credentials through the API
- [ ] **SQL parsing:** Apache Calcite parsing of PostgreSQL-dialect `SELECT` statements into relational algebra; a documented supported subset with clear errors outside it
- [ ] **Execution-plan analysis:** capture `EXPLAIN (FORMAT JSON)` and parse it into a typed plan tree; opt-in `EXPLAIN (ANALYZE, BUFFERS)` inside a rolled-back transaction
- [ ] **Bottleneck detection:** rules for row-estimate errors, sequential scans behind selective predicates, nested loops over large inputs, sort and hash spills, and missing-index candidates, each with an explanation pointing at the plan node
- [ ] **Initial rewrite rules:** deterministic rewrites such as making predicates index-friendly (sargable) and decorrelating subqueries, plus index recommendations
- [ ] **Benchmarking harness:** warm-up runs, repeated measurement, median and spread reporting, recorded environment, and result-set comparison between original and rewrite
- [ ] **Reference dataset:** a reproducible synthetic schema and data generator for development and tests
- [ ] **Web:** SQL input, plan tree view, findings list, benchmark comparison

**Exit criteria:** on the reference dataset, QueryForge accepts a query, shows its plan, flags at least the bottleneck types above with correct node references, and benchmarks rule-based rewrites against the original. All behavior is covered by integration tests against real PostgreSQL.

## Phase 2: AI and Automated Reasoning (Planned)

Goal: generate better rewrites with an LLM, and never present a rewrite as correct without evidence.

- [ ] **LLM-guided rewriting:** structured prompts built from the query, relevant schema, plan, and detected bottlenecks; structured output parsed back through Calcite (unparseable output is rejected)
- [ ] **Prompt evaluation:** a versioned evaluation set of queries with known good rewrites; track validity, equivalence, and speedup per prompt version, so prompt changes are measured rather than guessed
- [ ] **Semantic equivalence:** encode a defined subset (initially select-project-join with conjunctive predicates) as SMT constraints and use Z3 to search for counterexamples
- [ ] **Differential testing** for queries outside the Z3 subset: run original and candidate on generated data and compare results
- [ ] **Correctness and safety testing:** a test corpus of equivalent and deliberately non-equivalent pairs; guarantees that credentials and data never reach the LLM unintentionally
- [ ] **Web:** rewrite review with diff, verification status (proved, tested, unverified), and evidence

**Exit criteria:** every presented rewrite carries a verification status backed by evidence, the evaluation set runs in CI, and known non-equivalent pairs are always rejected.

## Phase 3: Machine Learning (Planned)

Goal: predict which candidates are worth benchmarking, using models trained on QueryForge's own measurements.

- [ ] **Benchmark dataset generation:** systematic query and parameter variation over reference schemas, recording plans and measured runtimes with environment metadata
- [ ] **Feature extraction:** plan-tree features such as node types, estimated vs. actual rows, cost, depth, join and scan types, and spill indicators
- [ ] **Model training:** XGBoost models for runtime prediction and pairwise ranking, with scikit-learn pipelines and held-out evaluation
- [ ] **Serving:** a Python FastAPI service (`services/`) for inference, called by the API
- [ ] **Candidate ranking:** integrate predictions into the pipeline to choose which candidates to benchmark
- [ ] **Evaluation:** ranking quality (for example rank correlation and top-k hit rate) compared with PostgreSQL's own cost estimates as the baseline, with methodology documented

**Exit criteria:** the model's ranking quality is reported against the planner-cost baseline on held-out data, and ranking is used in the pipeline only where it beats that baseline.

## Phase 4: Production Readiness (Planned)

Goal: run QueryForge as a dependable hosted service.

- [ ] **Observability:** structured logs, Micrometer metrics, OpenTelemetry tracing across API, ML service, and database calls
- [ ] **Security hardening:** authentication and authorization, encrypted target credentials in a secrets manager, dependency and container scanning, rate limiting, review of every widened default
- [ ] **Performance:** profiling and tuning of the pipeline; caching of plans and schema metadata
- [ ] **AWS deployment:** container images, infrastructure as code (`infrastructure/`), managed PostgreSQL, deployment workflow with environments
- [ ] **Comprehensive testing:** end-to-end browser tests, load tests, and failure-injection tests for target-database outages

**Exit criteria:** a deployed environment with monitoring and alerting, a documented security model, and an automated, repeatable deployment.
