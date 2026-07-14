# InventorySystem

Multi-tenant Inventory / WMS / Supply Chain B2B SaaS with embedded payments, append-only inventory ledger, and PostgreSQL Row-Level Security.

## Quick Start

### Prerequisites
- Docker Desktop
- Java 21 + Maven (for local backend dev)
- Node.js 22+ (for local frontend dev)

### Run everything with Docker

```bash
# From repo root
docker compose up --build -d

# Wait for backend healthy, then load demo data into ALL tables:
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
```

| Service  | URL                          |
|----------|------------------------------|
| Frontend | http://localhost:3000        |
| API      | http://localhost:8080        |
| Swagger  | http://localhost:8080/swagger-ui.html |
| Postgres | localhost:5432 (user: app_user) |

### Demo credentials

Login requires **company slug**, **email**, and **password**. Run the demo seed command above before using these accounts.

| Company slug     | Email              | Password     | Role   |
|------------------|--------------------|--------------|--------|
| `demo-corp`      | owner@demo.test    | password123  | OWNER  |
| `demo-corp`      | admin@demo.test    | password123  | ADMIN  |
| `demo-corp`      | manager@demo.test  | password123  | WAREHOUSE_MANAGER |
| `demo-corp`      | picker@demo.test   | password123  | PICKER |
| `demo-corp`      | viewer@demo.test   | password123  | VIEWER |
| `demo-corp`      | b2b@demo.test      | password123  | B2B_CUSTOMER (showroom portal) |
| `acme-wholesale` | owner@acme.test    | password123  | OWNER (tenant B) |

Quick login: open http://localhost:3000, use **demo-corp** / **owner@demo.test** / **password123** (the login form pre-fills the company slug and email).

## Local Development (without Docker for app code)

```bash
# Start database only
docker compose up db -d

# Apply demo seed (first time)
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql

# Backend
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm install
npm run dev   # http://localhost:5173
```

## Architecture

- **Backend:** Java 21, Spring Boot 3.3, JPA + Flyway, RS256 JWT, virtual threads
- **Database:** PostgreSQL 16, RLS on every table, append-only `inventory_ledger`
- **Frontend:** React 19, TypeScript, Vite, Tailwind, TanStack Query/Virtual, Zustand
- **Tenancy:** `set_config('app.current_tenant', ...)` per transaction; fail-closed RLS

## Demo Seed Data

`ops/demo_seed.sql` populates **every table** with realistic test data for two tenants:
- **Demo Corp** — full warehouse hierarchy, products, POs, SOs, allocations, invoices, payments
- **Acme Wholesale** — minimal second tenant for RLS isolation testing

`inventory_levels` is maintained automatically by triggers when ledger/allocation rows are inserted.

## Tests

```bash
# Backend (JUnit + Testcontainers)
cd backend && mvn test

# Frontend build
cd frontend && npm run build

# E2E (requires docker stack + demo seed)
cd frontend && npm run test:e2e
```

## Project Structure

```
InventorySystem/
├── backend/          # Spring Boot API
├── frontend/         # React SPA
├── ops/
│   ├── demo_seed.sql # Full test data for all tables
│   └── postgres/init/
├── docker-compose.yml
└── BUILD_PLAN.md     # Master build plan
```

## API Overview

- `POST /api/v1/auth/signup` — Create tenant + owner
- `POST /api/v1/auth/login` — Get JWT tokens
- `GET /api/v1/scan/{barcode}` — Barcode lookup
- `GET /api/v1/dashboard/stats` — Dashboard metrics
- Full CRUD under `/api/v1/` for products, orders, inventory, settings, users

See Swagger UI for complete OpenAPI documentation.
