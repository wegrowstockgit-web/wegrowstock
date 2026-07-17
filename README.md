# InventorySystem

Multi-tenant Inventory / WMS / Supply-Chain B2B SaaS with append-only inventory ledger, PostgreSQL Row-Level Security, office + warehouse surfaces, B2B showroom portal, manufacturing, embedded payments/fintech, and offline-capable floor scanning.

## Quick Start

### Prerequisites

- Docker Desktop
- Java 25 (LTS) + Maven (for local backend dev)
- Node.js 24+ (Active LTS; for local frontend / Playwright)

### Run everything with Docker

```bash
# From repo root
docker compose up --build -d

# Wait for backend healthy, then load demo data (base + multi-tenant expansion):
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed_tenants_extra.sql
```

| Service  | URL / endpoint |
|----------|----------------|
| Frontend | http://localhost:3000 |
| API      | http://localhost:8080 |
| Swagger  | http://localhost:8080/swagger-ui.html |
| Health   | http://localhost:8080/actuator/health |
| Postgres | `localhost:5432` — app runtime user `app_user` / Flyway owner `app_owner` |

Containers: `invsys-web` (nginx SPA), `invsys-api` (Spring Boot), `invsys-db` (Postgres 16).

Copy `.env.example` → `.env` when overriding JWT keys, webhook secrets, or DB credentials. Dev JWT PEMs live under `ops/jwt/`.

### Demo credentials

Login requires **email** and **password** only — the tenant is resolved from the globally unique email. Load both seed files after the API is healthy:

```bash
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed_tenants_extra.sql
```

Password for every account below: **password123**.

#### Demo Corp (`demo-corp`) — primary E2E tenant

| Email | Role | Warehouses / LBAC |
|-------|------|-------------------|
| owner@demo.test | OWNER | All (WH-01, WH-02) |
| admin@demo.test | ADMIN | All |
| manager@demo.test | WAREHOUSE_MANAGER | WH-01 only |
| picker@demo.test | PICKER | WH-01 only |
| viewer@demo.test | VIEWER | WH-01 only |
| b2b@demo.test | B2B_CUSTOMER | Showroom portal |

#### Acme Wholesale (`acme-wholesale`) — RLS isolation + full roles

| Email | Role | Warehouses / LBAC |
|-------|------|-------------------|
| owner@acme.test | OWNER | All (Acme Central, Acme West) |
| admin@acme.test | ADMIN | All |
| manager@acme.test | WAREHOUSE_MANAGER | WH-01 only |
| picker@acme.test | PICKER | WH-01 only |
| viewer@acme.test | VIEWER | WH-01 only |
| b2b@acme.test | B2B_CUSTOMER | Showroom |

#### Northwind Logistics (`northwind-logistics`) — 3 DCs

| Email | Role | Warehouses / LBAC |
|-------|------|-------------------|
| owner@northwind.test | OWNER | All (SEA, CHI, ATL) |
| admin@northwind.test | ADMIN | All |
| manager@northwind.test | WAREHOUSE_MANAGER | WH-SEA + WH-CHI |
| picker@northwind.test | PICKER | WH-SEA only |
| viewer@northwind.test | VIEWER | WH-SEA only |
| b2b@northwind.test | B2B_CUSTOMER | Showroom |

#### Pacific Parts Co (`pacific-parts`) — single DC hierarchy

| Email | Role | Warehouses / LBAC |
|-------|------|-------------------|
| owner@pacific.test | OWNER | WH-PDX |
| admin@pacific.test | ADMIN | WH-PDX |
| manager@pacific.test | WAREHOUSE_MANAGER | WH-PDX |
| picker@pacific.test | PICKER | WH-PDX |
| viewer@pacific.test | VIEWER | WH-PDX |
| b2b@pacific.test | B2B_CUSTOMER | Showroom |

Quick login: http://localhost:3000 — **owner@demo.test** / **password123**.

## Local Development (without Docker for app code)

```bash
# Start database only
docker compose up db -d

# Apply demo seed (first time / after reset)
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed_tenants_extra.sql

# Backend
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm install
npm run dev   # http://localhost:5173 (proxies /api → :8080)
```

Useful frontend scripts: `npm run build`, `npm test` (Vitest), `npm run test:e2e` (Playwright against http://localhost:3000 by default).

## Architecture

- **Backend:** Java 25 LTS, Spring Boot 4.1, JPA + Flyway (through `V075+`), RS256 JWT, virtual threads, Actuator/Prometheus
- **Database:** PostgreSQL 16, RLS on tenant tables, append-only `inventory_ledger`, trigger-maintained `inventory_levels` (optional `lpn_id` for palletized stock)
- **CQRS dashboard:** `dashboard_kpi_snapshots` read model; refreshed from outbox (`STOCK_LEVEL_CHANGED`, `ORDER_ALLOCATED`, `INVOICE_PAID`, …)
- **Realtime:** SSE `GET /api/v1/dashboard/stream` + `useDashboardStream` (replaces office polling intervals)
- **Frontend:** React 19.2 (latest stable), TypeScript, Vite, Tailwind design tokens (Surface A office / Surface B warehouse), TanStack Query + persist, Zustand, Lucide
- **Surfaces:** Office shell (expandable icon rail + ⌘K palette), warehouse floor ops (HID scan, waves, LPN move / Build Pallet, A* mini-map, cycle counts, issue supplies, van truck), B2B showroom (`/showroom`)
- **Digital Twin:** `locations.coord_x/y/z` + `walkable_edges` power A* wayfinding; pick waves sort hierarchically by location path
- **LPNs / MIB:** Mint/pack/move license plates; wave `tote_identifier`; ship-by-LPN; task interleaving (`/tasks/next-best-action`)
- **GS1:** Client-side `validatePickScan` blocks wrong SKU/qty before HTTP 409s
- **Pillars:** Stockroom internal consumption (`INTERNAL_CONSUMPTION`), lot genealogy `/compliance/lot-trace`, field van-stock (`locations.type=VEHICLE`)
- **Offline:** IndexedDB mutation outbox + service-worker-friendly scan queue; JWT refresh only when access token is near expiry; 403 is RBAC (does not sign out)
- **Tenancy:** Slugless login resolves `tenant_id` from globally unique email via `BootstrapJdbc`; RLS uses `set_config('app.current_tenant', ...)` per transaction (fail-closed)
- **LBAC:** `user_warehouses` + JWT `warehouse_ids` (includes active van location for technicians) + `X-Warehouse-Id` enforced by `WarehouseAccessFilter`
- **Integrations:** Stripe / Shopify / EasyPost webhook stubs, channel + EDI hooks, AP invoice ingestion, supplier portal tokens

## Demo Seed Data

Two SQL files under `ops/` populate **every major table** across **four tenants**:

| Seed file | Tenants |
|-----------|---------|
| `ops/demo_seed.sql` | **Demo Corp** (full WMS/B2B/manufacturing) + **Acme Wholesale** skeleton |
| `ops/demo_seed_tenants_extra.sql` | Acme full roles/warehouses + **Northwind Logistics** (3 DCs) + **Pacific Parts Co** (PDX hierarchy) |

`inventory_levels` is maintained automatically by triggers when ledger/allocation rows are written. Password resets: `ops/fix_passwords.sql`.

## Tests

```bash
# Backend (JUnit + Testcontainers; JaCoCo gate 85%)
cd backend && mvn test
cd backend && mvn -q verify   # includes jacoco:check

# Focused warehouse / dashboard pillar coverage
cd backend && mvn -q "-Dtest=LpnMoveHttpTest,LpnPalletizationHttpTest,TaskInterleavingHttpTest,PickingWaveToteHttpTest,AStarPathfindingTest,SpatialMapHttpTest,PathOptimizationHeuristicTest,PickingServiceTest,PickingWaveServiceTest,DashboardKpiCqrsHttpTest,DashboardStreamHttpTest" test

# Frontend unit
cd frontend && npm test

# Frontend production build
cd frontend && npm run build

# E2E (requires docker stack + demo seed on :3000)
cd frontend && npm run test:e2e

# Warehouse pillar journeys only
cd frontend && npx playwright test e2e/journeys/21-lpn-tote-interleave.spec.ts e2e/journeys/22-pallet-builder.spec.ts e2e/journeys/23-digital-twin-astar.spec.ts e2e/journeys/24-cqrs-sse-gs1-path.spec.ts
```

Playwright notes:

- `e2e/global.setup.ts` logs in all six demo roles and caches storage under `frontend/playwright/.auth/` (gitignored)
- Role fixtures: `ownerPage`, `adminPage`, `managerPage`, `pickerPage`, `viewerPage`, `b2bPage`
- Journey helpers: `e2e/journeys/helpers.ts` (`contextForRole`, `hidScan`, `apiJson`)
- Suites include RBAC/LBAC, B2B fulfill cycle, offline picker, cross-dock, blind counts, internal lot mint, **LPN/tote/interleave**, **pallet builder**, **Digital Twin / A\***, **CQRS/SSE/GS1/path**

## Project Structure

```
InventorySystem/
├── backend/                 # Spring Boot API (controllers, domain, Flyway V001–V074+)
├── frontend/
│   ├── src/                 # React app (pages, layout, offline queue, stores)
│   ├── e2e/                 # Playwright specs + role fixtures + journeys/
│   └── playwright/          # Cached auth states (local only)
├── ops/
│   ├── demo_seed.sql        # Full multi-tenant demo data
│   ├── fix_passwords.sql
│   ├── jwt/                 # Dev RS256 keypair
│   └── postgres/init/       # Bootstrap roles on first DB create
├── DATABASE_GUIDE.md        # Human + dictionary schema guide
├── DEVELOPER_ARCHITECTURE.md
├── PRODUCT.md               # Product register / UX principles
├── BUILD_PLAN.md            # Master build plan
├── docker-compose.yml       # db + api + web (+ observability)
└── .env.example
```

## API Overview

Auth & tenancy:

- `POST /api/v1/auth/signup` — Create tenant + owner
- `POST /api/v1/auth/login` — Access + refresh JWT
- `POST /api/v1/auth/refresh` — Rotate refresh token (access may be unchanged within the same second)

Core inventory & orders:

- `GET /api/v1/scan/{barcode}` — Barcode lookup
- `POST /api/v1/fulfillment/scan` — Floor pick/pack/receive scan
- `GET /api/v1/dashboard/stats` — Office dashboard metrics (CQRS snapshot)
- `GET /api/v1/shipments/cartonize-preview` — FFD 3D packing + billable weight
- CRUD under `/api/v1/` for products, variants/UOM, locations, POs, SOs, shipments, invoices, customers, suppliers, returns, cycle counts

Warehouse & manufacturing:

- `/api/v1/picking/waves` — Generate / optimize / release / claim waves (A* sequenced; MIB `toteIdentifier`)
- `/api/v1/picking/wayfinding` — A* polyline between two locations
- `/api/v1/tasks/next-best-action` — Closest interleaved floor task
- `/api/v1/inventory/lpns/mint|move|{barcode}/pack` — On-the-fly palletization
- `/api/v1/locations/{id}/coordinates` — Digital Twin drag-save
- `/api/v1/locations/heatmap` — 7-day movement intensity
- `/api/v1/manufacturing/**` — BOMs, production orders, terminal
- `/api/v1/reports/**` — COGS, profit, operational reports

Portal, money & platform:

- `/api/v1/portal/**` — B2B showroom catalog & draft orders
- `/api/v1/fintech/**` — Capital / underwriting cockpit (OWNER-gated draws)
- `/api/v1/ap-ingestions/**` — Supplier invoice OCR/ingest
- `/api/v1/webhooks/**` + public webhook receivers — Stripe, Shopify, EasyPost
- `/api/v1/settings`, users, invitations, SSO, account mappings, tax rates, shipping credentials

See Swagger UI for the complete OpenAPI surface.
