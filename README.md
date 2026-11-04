# QueryForge

[![CI](https://github.com/spollito/queryforge/actions/workflows/ci.yml/badge.svg)](https://github.com/spollito/queryforge/actions/workflows/ci.yml)

QueryForge is a SQL query optimization engine for PostgreSQL. It is being built to analyze execution plans, find performance bottlenecks, generate optimized rewrites, verify that each rewrite is semantically equivalent to the original, and benchmark the result.

> **Status: Phase 1 (query analysis) in progress.** QueryForge analyzes execution plans and reports bottlenecks with a suggested fix. Rewriting, equivalence checking, learned ranking, and benchmarking are not built yet; see [Current status](#current-status).

## Why QueryForge

Asking a language model to "make this query faster" is easy. Knowing whether the answer is *correct* and *actually faster* is the hard part. QueryForge treats the model as one source of candidate rewrites among several, and puts every candidate through the same pipeline:

1. **Understand the query.** Parse the SQL and read the planner's execution plan.
2. **Find the bottleneck.** Locate where time goes: misestimated row counts, sequential scans behind selective predicates, sorts and hashes spilling to disk.
3. **Propose rewrites.** Apply rule-based transformations and LLM-guided rewrites.
4. **Prove or test equivalence.** Use an SMT solver (Z3) for a supported SQL subset, and differential testing elsewhere.
5. **Rank and measure.** Predict performance with a learned model, then benchmark the top candidates against the original.

## What works today

Submit a `SELECT` (in the web workbench or with `POST /api/v1/analyses`) and QueryForge:

1. **Checks it is a single read-only query** by parsing it with Apache Calcite, before anything reaches the database.
2. **Captures the plan** on the target database with `EXPLAIN (FORMAT JSON)`, or with `EXPLAIN (ANALYZE, BUFFERS)` if you ask it to run the query. Either way it runs in a read-only transaction with a statement timeout, as a read-only role, and is rolled back.
3. **Runs bottleneck detectors** over the plan:

   | Finding | Needs the query to run | Suggested fix |
   | --- | --- | --- |
   | Sequential scan that keeps a small fraction of a large table | No | Index on the filtered columns (equality first, then range) |
   | Sequential scan repeated per outer row (nested loop inner side, correlated subquery) | No | Index on the correlated column, or a join rewrite |
   | Row estimate off by 10× or more, reported where it originates | Yes | Extended statistics for correlated filter columns, or `ANALYZE` |
   | Sort that spilled to disk | Yes | `work_mem` sized from the spill, or an index on the sort key |
   | Hash join or hash aggregate processed in batches on disk | Yes | More hash memory, or a smaller hash input |

4. **Shows the plan** as an EXPLAIN-style tree with each node's own share of total time (or estimated cost), so the hot spot stands out.

Suggestions are starting points to measure, not guarantees. QueryForge does not apply them.

## Proposed capabilities

These are planned, not built. The [roadmap](docs/roadmap.md) tracks them.

- Connection management for any number of target databases, with credentials stored encrypted
- Rule-based and LLM-guided query rewriting with an evaluated prompt set
- Semantic equivalence checking with Z3 for a defined SQL subset
- Learned performance prediction (XGBoost) for ranking candidate rewrites
- A repeatable benchmarking harness that compares the original and rewritten queries

## Current status

| Area | Status |
| --- | --- |
| Monorepo layout, docs, contribution guidelines | Done |
| Spring Boot API with Actuator health and liveness/readiness probes | Done |
| React web client with typed API client and live status checks | Done |
| PostgreSQL 18 via Docker Compose | Done |
| Integration tests against real PostgreSQL (Testcontainers), web unit tests | Done |
| GitHub Actions CI | Done |
| Sample target database with a reproducible e-commerce dataset | Done (Phase 1) |
| Read-only query gate (Calcite), EXPLAIN capture, plan parsing | Done (Phase 1) |
| Bottleneck detectors (5) and web workbench | Done (Phase 1) |
| Analysis history, multiple targets, rewrite rules, benchmarking | Next (Phase 1) |
| LLM rewriting, equivalence verification, ML ranking | Planned (Phases 2–3) |

## Technology stack

| Layer | In use now | Planned |
| --- | --- | --- |
| Web | React 19, TypeScript 6, Vite 8, Tailwind CSS 4, Vitest, ESLint | SQL editor, plan visualization |
| API | Java 21, Spring Boot 4.1 (Web MVC, Actuator, JDBC, Validation), Apache Calcite 1.42, Maven Wrapper | Flyway, Z3 |
| Database | PostgreSQL 18 (QueryForge's own database and a sample target) | |
| ML service | | Python, FastAPI, XGBoost, scikit-learn, LLM API |
| Infrastructure | Docker Compose, GitHub Actions | AWS |

## Repository layout

```text
queryforge/
├── apps/
│   ├── api/            Spring Boot API (Maven)
│   └── web/            React + TypeScript web client (Vite)
├── docs/
│   ├── architecture.md System design and architectural decisions
│   └── roadmap.md      Phased development plan
├── infrastructure/
│   └── sample-db/      Schema, data generator, and read-only role for the sample target
├── .github/            CI workflow and issue templates
├── compose.yaml        Local PostgreSQL: QueryForge's database and the sample target
└── .env.example        Template for local configuration
```

`services/` (Python ML service) will be added in the phase that needs it.

## Local development

### Prerequisites

- JDK 21
- Node.js 22.13+ or 24+ (`apps/web/.nvmrc` pins 22)
- Docker with Compose v2 (Docker Desktop or Docker Engine)

If several JDKs are installed on macOS, select 21 with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

### 1. Configure

```bash
cp .env.example .env
```

Edit `.env` and set `POSTGRES_PASSWORD` and `TARGET_DB_PASSWORD` to values of your own. Docker Compose, the API, and the web app all read this one file, which git ignores.

### 2. Start the databases

Compose runs two PostgreSQL servers: `postgres` is QueryForge's own database, and `sample-db` is a target database to analyze, seeded with a synthetic e-commerce dataset (500,000 orders at the default scale). The first start of `sample-db` generates the data, which takes about a minute.

```bash
docker compose up -d --wait     # start both and wait until healthy
docker compose ps               # check status
docker compose logs -f sample-db
docker compose stop             # stop, keeping data
docker compose down             # remove the containers, keeping data
docker compose down -v          # remove the containers AND delete all data (regenerates the sample on next start)
```

Both listen on loopback only (`127.0.0.1:5432` and `127.0.0.1:5433`), and data persists in named volumes. The sample schema leaves some indexes out on purpose; see [`infrastructure/sample-db/sql/schema.sql`](infrastructure/sample-db/sql/schema.sql).

Open a `psql` shell with:

```bash
docker compose exec postgres psql -U queryforge -d queryforge
docker compose exec sample-db psql -U sample_owner -d sample
```

### 3. Run the API

```bash
cd apps/api
./mvnw spring-boot:run
```

The API serves on <http://localhost:8080>. Check it with:

```bash
curl http://localhost:8080/actuator/health            # overall, includes the database
curl http://localhost:8080/actuator/health/liveness   # process is alive
curl http://localhost:8080/actuator/health/readiness  # ready for traffic (requires the database)
```

The API starts even when PostgreSQL is down. Health then reports `DOWN` with HTTP 503, and liveness stays `UP`.

Analyze a query:

```bash
curl -s -X POST http://localhost:8080/api/v1/analyses \
  -H 'Content-Type: application/json' \
  -d '{"sql": "SELECT id FROM orders WHERE customer_id = 42", "mode": "estimate"}'
```

`mode` is `estimate` (plan only, the default) or `execute` (runs the query with EXPLAIN ANALYZE). Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with a stable `code`: `invalid-request` (400), `unsupported-query` and `query-error` (422, with the error position), `target-unavailable` (503), and `statement-timeout` (504).

### 4. Run the web app

```bash
cd apps/web
npm install
npm run dev
```

Open <http://localhost:5173>. Pick a sample query or paste your own, then choose whether to run it.

### Configuration reference

All settings are environment variables. Real environment variables override values in `.env`.

| Variable | Default | Used by | Purpose |
| --- | --- | --- | --- |
| `POSTGRES_DB` | `queryforge` | Compose, API | Database name |
| `POSTGRES_USER` | `queryforge` | Compose, API | Database user |
| `POSTGRES_PASSWORD` | *(none)* | Compose, API | Database password; Compose refuses to start without it |
| `POSTGRES_HOST` | `localhost` | API | Database host |
| `POSTGRES_PORT` | `5432` | Compose, API | Host port for PostgreSQL |
| `SAMPLE_DB_PORT` | `5433` | Compose | Host port for the sample target database |
| `SAMPLE_SCALE` | `1` | Compose | Sample dataset size multiplier (1 = 500,000 orders); applies when the volume is first created |
| `TARGET_DB_URL` | `jdbc:postgresql://localhost:5433/sample` | API | JDBC URL of the database to analyze |
| `TARGET_DB_USER` | `queryforge_reader` | API | Role for analysis; should be read-only |
| `TARGET_DB_PASSWORD` | *(none)* | Compose, API | Password for that role; Compose creates the sample role with it |
| `TARGET_DB_STATEMENT_TIMEOUT` | `10s` | API | Longest a single EXPLAIN or EXPLAIN ANALYZE may run |
| `TARGET_DB_TIME_ZONE` | `UTC` | API | Session time zone for analysis, so timestamp literals do not depend on the API host |
| `API_PORT` | `8080` | API | HTTP port |
| `API_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | API | Comma-separated browser origins allowed to call the API |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Web | API base URL, embedded at build time |

## Testing

```bash
# API: compiles, then runs unit and integration tests. Requires Docker,
# because integration tests start a disposable PostgreSQL with Testcontainers.
cd apps/api && ./mvnw verify

# Web
cd apps/web
npm run lint      # ESLint with type-aware rules
npm test          # Vitest
npm run build     # type-check and production build
```

CI runs the same commands on every push to `main` and on every pull request. Any build, lint, or test failure fails the workflow.

## Roadmap

| Phase | Focus |
| --- | --- |
| 0. Foundation | Repository, scaffolding, local database, CI, docs **(done)** |
| 1. Query analysis | Target connections, Calcite parsing, plan analysis, bottleneck detection, first rewrite rules, benchmarking **(in progress)** |
| 2. AI and automated reasoning | LLM-guided rewriting, prompt evaluation, Z3 equivalence checking, correctness testing |
| 3. Machine learning | Benchmark datasets, plan features, XGBoost performance prediction, candidate ranking |
| 4. Production readiness | Observability, security hardening, performance, AWS deployment |

Details and exit criteria are in [docs/roadmap.md](docs/roadmap.md). System design and decisions are in [docs/architecture.md](docs/architecture.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for workflow, commit conventions, and checks to run before opening a pull request.

## License

[MIT](LICENSE)
