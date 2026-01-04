# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Travel Optimizer is a full-stack application for searching and comparing flight offers with ML-powered recommendations. It consists of a Spring Boot 4.x backend (Java 17) and a Vite/React frontend with TypeScript.

## Build and Development Commands

### Backend (Spring Boot)
```powershell
# Run backend (requires PostgreSQL or use dev-no-security profile)
mvn -DskipTests spring-boot:run

# Run without database (H2 in-memory, no auth)
mvn -DskipTests clean package
java -jar target/*-SNAPSHOT.jar --spring.profiles.active=dev-no-security

# Run all tests (uses Testcontainers for PostgreSQL)
mvn test

# Run a single test class
mvn test -Dtest=TripSearchServiceImplTest

# Run a single test method
mvn test -Dtest=TripSearchServiceImplTest#testSearchTrips
```

### Frontend (Vite/React)
```powershell
cd frontend
npm install
npm run dev      # Development server
npm run build    # Production build
npm test         # Run vitest tests
```

### Database
```powershell
# Start PostgreSQL via Docker Compose
docker compose up -d postgres
```

## Architecture

### Backend Structure
- **Controller Layer** (`controller/`): REST endpoints at `/api/trips/`, `/api/saved/`, `/api/feedback/`
- **Service Layer** (`service/impl/`): Business logic including `TripSearchServiceImpl` (main search orchestration), `BuyWaitServiceImpl` (buy/wait recommendations), `TripFlagService` (explainability flags)
- **Provider Layer** (`provider/impl/`): External API integrations - `AmadeusFlightSearchProvider` for real flight data, `MockFlightSearchProvider` for development
- **Repository Layer** (`repository/`): Spring Data JPA repositories for PostgreSQL
- **Mapper Layer** (`mapper/`): Manual DTO/entity mappers (MapStruct was removed)
- **Client Layer** (`client/`): ML service clients with circuit breaker patterns via Resilience4j

### Frontend Structure
- **Pages** (`pages/`): `SearchPage`, `ResultsPage`, `ComparePage`, `SavedOffersPage`
- **Components** (`components/`): `SearchForm`, `TripCard`, `DiagnosticsPanel`, `SuggestedFilters`
- **API Layer** (`lib/api.ts`): All backend API calls with client ID tracking
- **State**: React Query for server state, custom hooks for comparison/watch features

### Key Data Flow
1. `SearchForm` submits to `POST /api/trips/search`
2. `TripSearchController` delegates to `TripSearchServiceImpl`
3. Service calls flight providers in parallel with timeouts
4. `TripAssemblyService` combines flights/lodging into `TripOption` entities
5. `TripFlagService` adds explainability flags (BEST_VALUE, LOWEST_PRICE, etc.)
6. `BuyWaitService` computes buy/wait recommendations from price history
7. Results persisted to PostgreSQL, paginated via `GET /api/trips/{searchId}/options`

### Provider Integration
- Amadeus API integration is enabled via `AMADEUS_API_KEY` and `AMADEUS_API_SECRET` environment variables
- Set `amadeus.enabled=true` to use real flight data
- Tests use WireMock stubs, not the real Amadeus API

## Environment Variables

Required for full functionality:
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` - PostgreSQL connection
- `AMADEUS_API_KEY`, `AMADEUS_API_SECRET` - Amadeus Self-Service API (optional)
- `ML_SERVICE_URL` - ML recommendation service endpoint (optional)

## Testing Patterns

- Integration tests use `@Testcontainers` for PostgreSQL
- WireMock is used for external API mocking
- Frontend uses Vitest with React Testing Library
- E2E tests in `frontend/src/e2e/` use Puppeteer

## CI Commands

Commands used by CI (GitHub Actions). Run these locally to verify before pushing.

### Backend
```bash
# Check code formatting (fails if not formatted)
mvn -B spotless:check

# Auto-fix formatting
mvn spotless:apply

# Run all tests with CI profile (H2, mock providers)
mvn -B verify -Dspring.profiles.active=ci

# This runs: tests, JaCoCo coverage report, coverage threshold check (40%), package
# Coverage report generated at: target/site/jacoco/index.html
```

### Frontend
```bash
cd frontend
npm ci                # Install dependencies
npm run lint          # Run ESLint
npm run build         # Production build
npm test              # Run vitest tests
```

### Quality Gates
- **Backend**: Java 17 enforced, Spotless formatting, JaCoCo 40% instruction coverage
- **Frontend**: ESLint (max 150 warnings), Vite build
- CI runs on every PR and push to main
