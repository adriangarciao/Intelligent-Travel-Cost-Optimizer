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


![CI](https://github.com/adriangarciao/Intelligent-Travel-Cost-Optimizer/actions/workflows/ci.yml/badge.svg)

Notes


**Testing & Integration Setup**

  - `POST /predict/best-date-window` → 200 JSON
  - `POST /predict/option-recommendation` → 200 JSON
  - Quick: `mvn test` (integration tests start Docker containers via Testcontainers)
  - If you need unit-only runs: run specific tests with `-Dtest=...` or use test categories/naming conventions.

Amadeus integration (optional)
- To enable Amadeus Self-Service flight offers locally, set the following env vars (do NOT commit secrets):
  - `AMADEUS_API_KEY` and `AMADEUS_API_SECRET`
- Then set `amadeus.enabled=true` (e.g., export/set env var) and optionally `amadeus.base-url` (test API is default).
- The code respects caching and a short timeout to avoid hitting Amadeus rate limits. Tests use WireMock and do not call the real Amadeus API.
