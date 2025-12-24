Traveloptimizer — Local Development

## Local Dev

This project runs a Spring Boot backend and a Vite-based frontend. The backend expects a PostgreSQL database for local development.

1) Start Postgres with Docker Compose

PowerShell:

    # Copy the example file and set real secrets (do NOT commit this file)
    Copy-Item .env.postgres.example .env.postgres
    # Edit .env.postgres and set a secure password
    docker compose up -d postgres

2) Set required environment variables (PowerShell)

    # $env:SPRING_DATASOURCE_URL points to the DB. Example:
    $env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5433/traveloptimizer'
    $env:SPRING_DATASOURCE_USERNAME = 'postgres'
    $env:SPRING_DATASOURCE_PASSWORD = 'your_db_password'

    # Optional: export DB_* fallback variables if scripts expect them
    $env:DB_URL = $env:SPRING_DATASOURCE_URL
    $env:DB_USERNAME = $env:SPRING_DATASOURCE_USERNAME
    $env:DB_PASSWORD = $env:SPRING_DATASOURCE_PASSWORD

3) Start the backend

    # mvn may need JAVA_HOME pointing to JDK 17
    mvn -DskipTests spring-boot:run

4) Start the frontend

    cd frontend
    npm install
    npm run dev

5) Verify the backend

    # Check actuator health
    curl http://localhost:8080/actuator/health

Notes:
 - The compose file exposes Postgres on host port 5433 and configures the container to listen on 5433 for consistency with local dev.
 - Do NOT commit `.env.postgres` containing real passwords. Use `.env.postgres.example` as a template.
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
