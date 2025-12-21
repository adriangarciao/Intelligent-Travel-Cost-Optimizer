# Local dev: `dev-no-security` profile

This repository includes a temporary development-only profile `dev-no-security` that disables authentication for local verification.

How to run (local only):

1. Build the jar:

```powershell
mvn -DskipTests clean package
```

2. Run with the dev profile (H2 in-memory datasource will be used):

```powershell
java -jar target/*-SNAPSHOT.jar --spring.profiles.active=dev-no-security
```

Notes:
- This profile is intended only for local development and testing. Do NOT use it in staging/production.
- It configures an H2 in-memory datasource and excludes security auto-configuration to allow unauthenticated requests to API endpoints for quick verification.
- If you need more detailed inspection (security beans, filter chains), ask and I can add a dev-only inspector that logs them at startup.
# Intelligent Travel Cost Optimizer (backend)

This is a Spring Boot 3.x backend (Java 17+) for the Intelligent Travel Cost Optimizer project.

Quick start

1. Create a local `.env` file from `.env.example` and fill in `DB_PASSWORD` (do NOT commit `.env`).

2. Load the `.env` variables into your PowerShell session (example):

```powershell
(Get-Content .env) -split "`r?`n" | ForEach-Object {
  if ($_ -and -not $_.StartsWith('#')) {
    $parts = $_ -split '=',2
    if ($parts.Length -eq 2) { [System.Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process') }
  }
}
mvn spring-boot:run
```

Running tests

Unit and integration tests are executed with Maven. Integration tests use Testcontainers to run a temporary PostgreSQL instance.

```powershell
mvn test
```

CI

- The repository includes a GitHub Actions workflow `CI` that runs Maven tests on push/PR to `main`.

![CI](https://github.com/adriangarciao/Intelligent-Travel-Cost-Optimizer/actions/workflows/ci.yml/badge.svg)

Notes

- Do not commit secrets. `.env` is ignored and `.env.example` provides placeholders.
- Flyway migrations are located under `src/main/resources/db/migration` and will run automatically at startup when a DB is configured.

**Testing & Integration Setup**

- **Unit tests:** small, fast JVM tests (no Docker). Run with `mvn test`.
- **Integration tests:** use Testcontainers to provide PostgreSQL and Redis and exercise Spring contexts.
- **ML stubbing:** integration tests use the `WireMockMlServerExtension` which starts a dynamic-port WireMock server and registers ML stubs for:
  - `POST /predict/best-date-window` → 200 JSON
  - `POST /predict/option-recommendation` → 200 JSON
- **CI:** runs integration tests on Docker-enabled runners (Testcontainers + WireMock). Ensure Docker is available in CI.
- **Local runs:**
  - Quick: `mvn test` (integration tests start Docker containers via Testcontainers)
  - If you need unit-only runs: run specific tests with `-Dtest=...` or use test categories/naming conventions.
- **ML failure coverage:** negative ML scenarios are covered by `TripSearchMlFailureTest` (it intentionally points at an unused ML port to verify fallback behavior).
