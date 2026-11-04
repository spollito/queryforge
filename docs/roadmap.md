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

## Phase 1: Query Analysis (In progress)

Goal: given a SQL query and a target database, explain why it is slow and suggest deterministic improvements, with measured evidence.

- [x] **Reference dataset:** a reproducible synthetic e-commerce schema and data generator (`infrastructure/sample-db`), run by Docker Compose and by integration tests
- [x] **Target connection (single):** one configured target database; read-only role, read-only rolled-back transactions, per-transaction statement timeout and time zone; credentials never exposed
- [x] **SQL parsing:** Apache Calcite (Babel) parsing of PostgreSQL-dialect queries as a gate admitting exactly one read-only query, with positioned errors outside the supported grammar
- [x] **Execution-plan analysis:** `EXPLAIN (FORMAT JSON)` parsed into a typed plan tree; opt-in `EXPLAIN (ANALYZE, BUFFERS)`; correct row accounting for loops and parallel plans; each node's own share of time or cost
- [x] **Bottleneck detection:** selective sequential scans, repeated sequential scans, row-estimate errors (reported at their origin), sort spills, and hash spills, each pointing at its plan node with a suggested fix
- [x] **Web:** SQL workbench with sample queries, findings, and an EXPLAIN-style plan tree
- [ ] **Persistence foundation:** Flyway migrations and the first QueryForge schema (analysis runs, captured plans, findings)
- [ ] **Target connection management:** register several target databases at runtime, with credentials stored encrypted
- [ ] **More detectors:** missing join indexes, nested loops over large inputs, index scans with heavy rechecks, CTE and subquery fences
- [ ] **Initial rewrite rules:** deterministic rewrites such as making predicates index-friendly (sargable) and decorrelating subqueries
- [ ] **Benchmarking harness:** warm-up runs, repeated measurement, median and spread reporting, recorded environment, and result-set comparison between original and rewrite
- [ ] **Web:** analysis history and benchmark comparison

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
