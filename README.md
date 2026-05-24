# TravelOptimizer

[![CI](https://github.com/adriangarciao/Intelligent-Travel-Cost-Optimizer/actions/workflows/ci.yml/badge.svg)](https://github.com/adriangarciao/Intelligent-Travel-Cost-Optimizer/actions/workflows/ci.yml)

**Live Demo:** [https://YOUR_RAILWAY_URL](https://YOUR_RAILWAY_URL) _(replace with your Railway deployment URL)_

TravelOptimizer is a developer-focused prototype for smarter flight decisions. It fetches flight offers, normalizes results, scores each offer with a Value Score, and provides a Buy/Wait recommendation to help users decide when to book.

> **Demo mode:** The live deployment uses mock flight data — no real API calls are made. Airlines, prices, and routes are realistic but generated. To run with live Amadeus flights locally, see [Local Setup](#local-setup).

## Screenshots

| Hero page | Search form | Results page | Compare page |
|---|---|---|---|
| ![Hero page](frontend/public/images/heropage.png) | ![Search form](frontend/public/images/searchform.png) | ![Results page](frontend/public/images/resultspage.png) | ![Compare page](frontend/public/images/comparePage.png) |

## Key features

**Search and browsing**
- Date window searches (earliest/latest), one-way and round-trip
- Paginated results via backend pagination endpoint
- Per-option detail view with price, segments, flags, and score breakdown

**Decision support**
- Value Score (0–1) ranks offers relative to others in the same search
- Deal Meter shows percentile position within the current search
- Buy/Wait recommendation from ML client or built-in baseline heuristic

**Productivity**
- Save offers and searches (server-backed, keyed by `X-Client-Id` header)
- Compare up to 3 offers side-by-side
- Export / share saved offers

**Engineering**
- Provider abstraction — mock (default) and Amadeus implementations
- Optional Redis caching and Micrometer metrics via Spring Actuator
- Integration tests with Testcontainers and WireMock

## Architecture

```
Frontend (Vite / React / TypeScript)
    └─> Backend API (Spring Boot)
            ├─> Provider (Amadeus API or mock)
            ├─> DB (Postgres via Flyway)
            ├─> Cache (optional Redis)
            └─> ML (baseline heuristic or external ML service)
```

## Tech stack

- **Frontend:** React, TypeScript, Vite, Tailwind CSS, react-router-dom, react-query, react-hook-form, zod, vitest
- **Backend:** Spring Boot (Java 17), Spring WebMVC/WebFlux, Spring Data JPA, Flyway, Spring Cache/Redis, Micrometer, Actuator, Resilience4j
- **Data:** Postgres (recommended), H2 for lightweight dev
- **Testing:** JUnit 5, Testcontainers (Postgres), WireMock
- **Tooling:** Maven, Node/npm, Docker, PowerShell helper scripts

## Local setup

See [dev/SETUP.md](dev/SETUP.md) for full local dev instructions including environment variables, Docker compose, and script usage.

**Quick start (H2, no Docker):**

```bash
# Backend
pwsh ./scripts/run-backend.ps1 -Db h2

# Frontend (separate terminal)
cd frontend && npm install && npm run dev
```

Backend: `http://localhost:8080` | Frontend: `http://localhost:5173`

**Running with live Amadeus data:**

Set `AMADEUS_API_KEY`, `AMADEUS_API_SECRET`, and `TRAVEL_PROVIDERS_FLIGHTS=amadeus` before starting the backend. The demo banner in the UI disappears when the live provider is active.

## Testing

```bash
# Backend (requires Docker for Testcontainers)
mvn test

# Frontend
cd frontend && npm test

# CI-equivalent (H2, mock providers, coverage check)
mvn -B verify -Dspring.profiles.active=ci
```

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/trips/search` | Submit a search (origin, destination, dates, travelers) |
| `GET` | `/api/trips/{searchId}/options` | Paginated options (`page`, `size`, `sortBy`, `sortDir`) |
| `GET` | `/api/trips/recent` | Recent searches |
| `GET` | `/api/demo-status` | Returns `{ "demoMode": true }` when mock provider is active |
| `POST/GET/DELETE` | `/api/saved` | Saved searches (requires `X-Client-Id` header) |
| `POST/GET/DELETE` | `/api/saved/offers` | Saved offers (requires `X-Client-Id` header) |
| `POST` | `/api/feedback` | UI feedback events (fire-and-forget) |
| `GET` | `/actuator/health` | Health check |

## Deployment (Railway)

A `railway.toml` is included at the repo root. Set these environment variables in Railway:

| Variable | Description |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL (Railway injects this if you add a Postgres service) |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `AMADEUS_API_KEY` / `AMADEUS_API_SECRET` | Optional — omit to use mock data |
| `TRAVEL_PROVIDERS_FLIGHTS` | Set to `amadeus` for live data; omit for mock (default) |

Railway injects `PORT` automatically — `server.port=${PORT:8080}` in `application.properties` picks it up.

## License

TBD
