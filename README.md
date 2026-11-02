# QueryForge

[![CI](https://github.com/spollito/queryforge/actions/workflows/ci.yml/badge.svg)](https://github.com/spollito/queryforge/actions/workflows/ci.yml)

QueryForge is a SQL query optimization engine for PostgreSQL. It is being built to analyze execution plans, find performance bottlenecks, generate optimized rewrites, verify that each rewrite is semantically equivalent to the original, and benchmark the result.

> **Status: Phase 0 (foundation) complete.** The repository has a working API, web client, local database, tests, and CI. None of the optimization capabilities below are implemented yet; see [Current status](#current-status).

## Why QueryForge

Asking a language model to "make this query faster" is easy. Knowing whether the answer is *correct* and *actually faster* is the hard part. QueryForge treats the model as one source of candidate rewrites among several, and puts every candidate through the same pipeline:

1. **Understand the query.** Parse the SQL and read the planner's execution plan.
2. **Find the bottleneck.** Locate where time goes: misestimated row counts, sequential scans behind selective predicates, sorts and hashes spilling to disk.
3. **Propose rewrites.** Apply rule-based transformations and LLM-guided rewrites.
4. **Prove or test equivalence.** Use an SMT solver (Z3) for a supported SQL subset, and differential testing elsewhere.
5. **Rank and measure.** Predict performance with a learned model, then benchmark the top candidates against the original.

## Proposed capabilities

These are planned, not built. The [roadmap](docs/roadmap.md) tracks them.

- Connection management for target PostgreSQL databases, with read-only access and statement timeouts
- SQL parsing and plan analysis with Apache Calcite and `EXPLAIN (FORMAT JSON)`
- Bottleneck detection over execution plans
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
| Query analysis, rewriting, verification, ML, benchmarking | Planned (Phases 1–3) |

## Technology stack

| Layer | In use now | Planned |
| --- | --- | --- |
| Web | React 19, TypeScript 6, Vite 8, Tailwind CSS 4, Vitest, ESLint | SQL editor, plan visualization |
| API | Java 21, Spring Boot 4.1 (Web MVC, Actuator, JDBC), Maven Wrapper | Apache Calcite, Flyway, Z3 |
| Database | PostgreSQL 18 | |
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
├── .github/            CI workflow and issue templates
├── compose.yaml        Local PostgreSQL
└── .env.example        Template for local configuration
```

`services/` (Python ML service) and `infrastructure/` (deployment configuration) will be added in the phases that need them.

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

Edit `.env` and set `POSTGRES_PASSWORD` to a value of your own. Docker Compose, the API, and the web app all read this one file, which git ignores.

### 2. Start PostgreSQL

```bash
docker compose up -d --wait     # start and wait until healthy
docker compose ps               # check status
docker compose logs -f postgres # follow logs
docker compose stop             # stop, keeping data
docker compose down             # remove the container, keeping data
docker compose down -v          # remove the container AND delete all data
```

The database listens on `127.0.0.1:5432` only, and data persists in the `queryforge_postgres-data` volume.

Open a `psql` shell with:

```bash
docker compose exec postgres psql -U queryforge -d queryforge
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

### 4. Run the web app

```bash
cd apps/web
npm install
npm run dev
```

Open <http://localhost:5173>. The page shows live API and database status.

### Configuration reference

All settings are environment variables. Real environment variables override values in `.env`.

| Variable | Default | Used by | Purpose |
| --- | --- | --- | --- |
| `POSTGRES_DB` | `queryforge` | Compose, API | Database name |
| `POSTGRES_USER` | `queryforge` | Compose, API | Database user |
| `POSTGRES_PASSWORD` | *(none)* | Compose, API | Database password; Compose refuses to start without it |
| `POSTGRES_HOST` | `localhost` | API | Database host |
| `POSTGRES_PORT` | `5432` | Compose, API | Host port for PostgreSQL |
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
| 1. Query analysis | Target connections, Calcite parsing, plan analysis, bottleneck detection, first rewrite rules, benchmarking |
| 2. AI and automated reasoning | LLM-guided rewriting, prompt evaluation, Z3 equivalence checking, correctness testing |
| 3. Machine learning | Benchmark datasets, plan features, XGBoost performance prediction, candidate ranking |
| 4. Production readiness | Observability, security hardening, performance, AWS deployment |

Details and exit criteria are in [docs/roadmap.md](docs/roadmap.md). System design and decisions are in [docs/architecture.md](docs/architecture.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for workflow, commit conventions, and checks to run before opening a pull request.

## License

[MIT](LICENSE)
