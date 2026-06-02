# Local Development Setup

## Prerequisites

- Java 17
- Maven (or use the included `./mvnw` wrapper)
- Node 18+ and npm
- Docker (optional — required for Postgres + Redis mode)

## Mode 1: Fast dev with H2 (no Docker)

**Backend (PowerShell):**

```powershell
pwsh .\scripts\run-backend.ps1 -Db h2
```

Builds and runs the Spring Boot jar against an in-memory H2 database. Backend listens on port 8080.

**Frontend:**

```bash
cd frontend
npm install
npm run dev
```

Frontend dev server defaults to port 5173. Vite proxies `/api` to `http://localhost:8080` when `VITE_API_BASE_URL` is unset.

## Mode 2: Full dev with Docker (Postgres + Redis)

**Start Docker services:**

```bash
docker compose up -d
```

Docker Compose maps Postgres to host port 5433 by default.

**Backend (PowerShell):**

```powershell
pwsh .\scripts\run-backend.ps1 -Db postgres
```

**Frontend:** same as Mode 1.

## Environment variables

These are for local dev only. **Do not commit secrets.**

```powershell
# PostgreSQL connection
$env:SPRING_DATASOURCE_URL      = 'jdbc:postgresql://localhost:5433/traveloptimizer'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
$env:SPRING_DATASOURCE_PASSWORD = 'your_db_password'

# Amadeus live flights (optional — mock is used when unset)
$env:AMADEUS_API_KEY    = 'your_key'
$env:AMADEUS_API_SECRET = 'your_secret'
$env:TRAVEL_PROVIDERS_FLIGHTS = 'amadeus'

# ML service (optional)
$env:ML_SERVICE_URL = 'http://localhost:8000'
```

`scripts/run-backend.ps1` reads `.env.amadeus` and `.env.postgres` automatically and maps keys to the Spring vars above. Use `.env.postgres.example` as a template — copy it to `.env.postgres` and fill in your password.

## Notes

- If `VITE_API_BASE_URL` is set to an absolute URL the frontend bypasses the Vite proxy. Leave it unset for local dev.
- The compose file exposes Postgres on host port 5433 (not 5432) to avoid conflicts with a local Postgres install.
- Actuator health endpoint: `http://localhost:8080/actuator/health`
