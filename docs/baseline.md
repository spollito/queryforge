# Phase-1 Baseline Record

Captured before any agentic-enhancement changes, so the working baseline is preserved and
its state is reproducible (enhancement brief §2, §16). The brief asked to "save existing
benchmark configuration and results" — **no benchmark harness or results exist yet**, so
there is nothing of that kind to save; this record captures the validated build/test state
instead.

## Commit

- Baseline commit: `4cf6fa5` (`Merge pull request #1 from spollito/feat/plan-analysis`)
- Branch: `main`
- Date captured: 2026-11-18

## Toolchain

- JDK: OpenJDK **21.0.2** (project targets 21; selected via `/usr/libexec/java_home -v 21`).
  Note: a JDK 23 is also installed and is **not** used for the build.
- Node: per `apps/web/.nvmrc` (22).
- Docker: running (required for Testcontainers integration tests).

## Validation results

| Suite | Command | Result |
| --- | --- | --- |
| API (unit + integration) | `cd apps/api && ./mvnw verify` | **BUILD SUCCESS** (exit 0); 61 `@Test` methods across 11 test classes, incl. Testcontainers PostgreSQL 18 integration tests |
| Web (lint + unit + build) | `cd apps/web && npm ci && npm run lint && npm test && npm run build` | **PASS** (exit 0); 21 tests in 5 files; production build succeeds |

Expected in-test log noise (not failures): a `ConnectException: Connection refused` from
the "database down" health test and connection-classification tests, and a Mockito
self-attach warning. The overall builds exit 0.

## How to re-validate

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd apps/api && ./mvnw verify
cd ../web && npm ci && npm run lint && npm test && npm run build
```

All enhancement work must keep these green (SPEC §13).
