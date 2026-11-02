# Contributing to QueryForge

Thanks for your interest. This guide covers how to set up, how changes flow into `main`, and what a pull request needs.

## Setup

Follow [Local development](README.md#local-development) in the README. In short:

```bash
cp .env.example .env          # then set POSTGRES_PASSWORD
docker compose up -d --wait
cd apps/api && ./mvnw spring-boot:run
cd apps/web && npm install && npm run dev
```

## Workflow

1. Open or pick an issue. For anything beyond a small fix, agree on the approach in the issue first.
2. Branch from `main` with a short, typed name, such as `feat/plan-parser`, `fix/health-cors`, or `docs/roadmap-phase-1`.
3. Keep each pull request focused on one change. Separate refactoring from behavior changes.
4. Run the checks below locally.
5. Open a pull request against `main`, link the issue, and describe what changed and how you verified it.

`main` must always build. CI runs on every pull request and must pass before merge.

## Commit messages

QueryForge follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```text
<type>(<scope>): <summary in the imperative, lower case, no period>

[optional body: what and why, wrapped at 72 characters]

[optional footer: BREAKING CHANGE: ..., Refs #123]
```

**Types**

| Type | Use for |
| --- | --- |
| `feat` | A new capability |
| `fix` | A bug fix |
| `refactor` | Code change with no behavior change |
| `perf` | A performance improvement |
| `test` | Adding or correcting tests |
| `docs` | Documentation only |
| `build` | Build system or dependencies (Maven, npm) |
| `ci` | CI configuration |
| `chore` | Maintenance that fits nowhere else |

**Scopes:** `api`, `web`, `db`, `ci`, `docs`, plus `ml` and `infra` once those areas exist.

**Examples**

```text
feat(api): parse EXPLAIN (FORMAT JSON) output into a plan tree
fix(web): show database status when the API returns 503
build(api): upgrade Spring Boot to 4.1.3
docs: record decision to pin PostgreSQL 18
```

Mark breaking changes with `!` after the type or scope (`feat(api)!: ...`) and a `BREAKING CHANGE:` footer.

## Checks to run before a pull request

```bash
# API (requires Docker for Testcontainers)
cd apps/api && ./mvnw verify

# Web
cd apps/web && npm run lint && npm test && npm run build
```

## Engineering standards

- **Tests.** New behavior comes with tests. Prefer integration tests against real PostgreSQL (Testcontainers) over mocks for anything that touches SQL or execution plans, since planner behavior is PostgreSQL-specific.
- **Dependencies.** Add a dependency only when the code that needs it lands, and explain non-obvious additions in the pull request.
- **Configuration.** Keep configuration out of business logic. New settings are environment variables with safe local defaults, documented in `.env.example` and the README.
- **Secrets.** Never commit credentials, tokens, or real connection strings. `.env` is git-ignored. Keep it that way.
- **Honesty in docs.** Mark planned functionality as planned. Report performance numbers only when they come from a reproducible benchmark, and say how it was run.
- **Decisions.** Record non-obvious architectural decisions in [docs/architecture.md](docs/architecture.md#architectural-decisions).
- **Style.** Java follows Spring conventions (tab indentation, constructor injection). TypeScript must pass ESLint with no warnings. `.editorconfig` sets whitespace rules for every file type.

## Reporting bugs and requesting features

Use the issue templates. Bug reports need reproduction steps and environment details. Remove credentials and private data from logs before posting.
