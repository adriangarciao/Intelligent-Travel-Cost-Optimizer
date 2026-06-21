# Intelligent Travel Cost Optimizer

[![CI](https://github.com/adriangarciao/Intelligent-Travel-Cost-Optimizer/actions/workflows/ci.yml/badge.svg)](https://github.com/adriangarciao/Intelligent-Travel-Cost-Optimizer/actions/workflows/ci.yml)

**Live demo:** https://intelligent-travel-cost-optimizer.vercel.app/

Travel Optimizer searches and compares flight offers, then ranks them with a transparent value score and a buy-or-wait recommendation. It pairs a Spring Boot (Java 17) backend with a Vite/React/TypeScript frontend, and is built to feel like a real product: paginated search, side-by-side compare, and saved offers. Every recommendation is explainable, so you can see why one offer scores higher than another.

> **Demo mode:** The live deployment uses mock flight data, so no real API calls are made. Airlines, prices, and routes are realistic but generated. To run with live Amadeus flights locally, see [Local setup](#local-setup).

## Screenshots

| Hero page | Search form | Results page | Compare page |
|---|---|---|---|
| ![Hero page](frontend/public/images/heropage.png) | ![Search form](frontend/public/images/searchform.png) | ![Results page](frontend/public/images/resultspage.png) | ![Compare page](frontend/public/images/comparePage.png) |

## Highlights

- **Personalized smart filters:** The app learns from the offers you save and dismiss to suggest filters (nonstop only, max layovers, preferred or avoided airlines), each with a confidence score and a plain-language reason. Suggestions are computed server-side from your interaction history, keyed by an anonymous client ID.
- **Redis caching:** Provider responses and search results are cached through Spring Cache backed by Redis, cutting repeat-query latency and external API load.
- **Continuous integration:** Every push and pull request runs the full GitHub Actions pipeline. See the badge above for live status.
- **Flyway migrations:** The Postgres schema is versioned and applied automatically through Flyway, so the database stays reproducible across environments.
- **Explainable scoring:** A value score (0 to 1) and a deal meter rank each offer against the rest of the same search, with a per-offer breakdown.
- **Resilient providers:** A clean provider abstraction (mock by default, Amadeus for live data) with Resilience4j circuit breakers.

## Key features

**Search and browsing**
- Date window searches (earliest and latest), one-way and round-trip
- Paginated results served by a dedicated backend pagination endpoint
- Per-option detail view with price, segments, flags, and score breakdown

**Decision support**
- Value score (0 to 1) ranks offers relative to others in the same search
- Deal meter shows percentile position within the current search
- Buy-or-wait recommendation from the ML client or a built-in baseline heuristic
- Smart filter suggestions personalized from your save/dismiss history, applied to results client-side with active-filter chips

**Productivity**
- Save offers server-side, keyed by the `X-Client-Id` header; recent searches are served from the backend
- Save searches locally in the browser for quick re-access
- Compare up to 3 offers side by side
- Export and share saved offers

## Architecture

```
Frontend (Vite / React / TypeScript)
    |
    v
Backend API (Spring Boot)
    |-- Provider (Amadeus API or mock)
    |-- Database (Postgres, versioned by Flyway)
    |-- Cache (Redis via Spring Cache)
    |-- ML (baseline heuristic or external ML service)
```

## Tech stack

- **Frontend:** React, TypeScript, Vite, Tailwind CSS, react-router-dom, react-query, react-hook-form, zod, vitest
- **Backend:** Spring Boot (Java 17), Spring WebMVC/WebFlux, Spring Data JPA, Flyway, Spring Cache with Redis, Micrometer, Actuator, Resilience4j
- **Data:** Postgres (recommended), H2 for lightweight dev
- **Testing:** JUnit 5, Testcontainers (Postgres), WireMock
- **CI/CD:** GitHub Actions (Spotless format check, tests, JaCoCo coverage gate), Vercel (frontend), Railway (backend)
- **Tooling:** Maven, Node/npm, Docker, PowerShell helper scripts

## Local setup

See [dev/SETUP.md](dev/SETUP.md) for full instructions, including environment variables, Docker Compose, and helper scripts.

**Quick start (H2, no Docker):**

```bash
# Backend
pwsh ./scripts/run-backend.ps1 -Db h2

# Frontend (separate terminal)
cd frontend && npm install && npm run dev
```

Backend: `http://localhost:8080` | Frontend: `http://localhost:5173`

**Running with Postgres and Redis (Docker):**

```bash
docker compose up -d postgres redis
```

Flyway applies the schema migrations on startup, so no manual database setup is needed.

**Running with live Amadeus data:**

Set `AMADEUS_API_KEY`, `AMADEUS_API_SECRET`, and `TRAVEL_PROVIDERS_FLIGHTS=amadeus` before starting the backend. The demo banner in the UI disappears once the live provider is active.

## Testing

```bash
# Backend (requires Docker for Testcontainers)
mvn test

# Frontend
cd frontend && npm test

# CI-equivalent run (H2, mock providers, coverage gate)
mvn -B verify -Dspring.profiles.active=ci
```

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/trips/search` | Submit a search (origin, destination, dates, travelers) |
| `GET` | `/api/trips/{searchId}/options` | Paginated options (`page`, `size`, `sortBy`, `sortDir`) |
| `GET` | `/api/trips/recent` | Recent searches |
| `GET` | `/api/demo-status` | Returns `{ "demoMode": true }` when the mock provider is active |
| `POST/GET/DELETE` | `/api/saved` | Saved searches (requires `X-Client-Id` header) |
| `POST/GET/DELETE` | `/api/saved/offers` | Saved offers (requires `X-Client-Id` header) |
| `POST` | `/api/feedback` | UI feedback events — saves, dismisses, filter applies (fire and forget) |
| `GET` | `/api/users/{userId}/smart-filters` | Personalized filter suggestions computed from feedback history |
| `GET` | `/actuator/health` | Health check |

## Deployment

The frontend is deployed to Vercel and the backend to Railway. A `railway.toml` is included at the repo root. Set these environment variables in Railway:

| Variable | Description |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL (Railway injects this when you add a Postgres service) |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `APP_REDIS_ENABLED` | Set to `true` to enable Redis-backed caching (optional; uses `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT`) |
| `AMADEUS_API_KEY` / `AMADEUS_API_SECRET` | Optional; omit to use mock data |
| `TRAVEL_PROVIDERS_FLIGHTS` | Set to `amadeus` for live data; omit for mock (default) |

Railway injects `PORT` automatically, and `server.port=${PORT:8080}` in `application.properties` picks it up.

## Roadmap

- Richer ML explanations with a per-feature weight breakdown for the value score
- Expanded Redis caching with a fallback path for provider results
- End-to-end frontend tests with Playwright or Cypress

## License

TBD
