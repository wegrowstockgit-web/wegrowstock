# InventorySystem

Multi-tenant Inventory / WMS / Supply-Chain B2B SaaS with append-only inventory ledger, PostgreSQL Row-Level Security, office + warehouse surfaces, B2B showroom portal, manufacturing, embedded payments/fintech, and offline-capable floor scanning.

## Quick Start

### Prerequisites

- Docker Desktop
- Java 25 (LTS) + Maven (for local backend dev)
- Node.js 24+ (Active LTS; for local frontend / Playwright)

### Run everything with Docker (`deploy.bat`)

On Windows, prefer the repo helper from the project root (quiet console; full log in `.deploy-last.log`):

```bat
deploy.bat deploy              Rem build images, start stack, wait for API health
deploy.bat seed                Rem load demo users / catalog (password123)
deploy.bat status              Rem container status + URLs
deploy.bat down                Rem stop containers (keeps DB volume)
```

| Command | What it does |
|---------|----------------|
| `deploy.bat` / `deploy.bat deploy` | Rebuild and start the full stack |
| `deploy.bat deploy --clean-frontend` | Wipe frontend `node_modules`/`dist` first, then deploy |
| `deploy.bat seed` | Apply `ops/demo_seed.sql` (+ extra tenants if present) |
| `deploy.bat status` | Compact `docker compose ps` + endpoint list |
| `deploy.bat down` | Stop/remove containers; Postgres volume kept |
| `deploy.bat clean-frontend` | Remove frontend build artifacts only |
| `deploy.bat help` | Print usage |

**Support Co-Pilot / chatbot** (optional module — backend + frontend together):

```bat
deploy.bat chatbot-status      Rem show current preference
deploy.bat chatbot-disable     Rem persist OFF (.invsys-chatbot-disabled)
deploy.bat chatbot-enable      Rem persist ON (default)
deploy.bat deploy              Rem rebuild both api + web with that preference

Rem One-shot overrides (do not change the saved preference):
deploy.bat deploy --no-chatbot
deploy.bat deploy --with-chatbot
```

When disabled: backend omits the `invsys-chatbot` jar (`-P-with-chatbot`) and sets `INVSYS_CHATBOT_ENABLED=false`; frontend builds with `VITE_ENABLE_CHATBOT=false` and a stub UI bridge. Core inventory still runs.

**Live Gemini (Support Co-Pilot + RAG embeddings):** only a Google AI Studio key is required — defaults use `gemini-2.0-flash` and `text-embedding-004`:

```bash
export GEMINI_API_KEY="your-actual-google-ai-studio-key"
# Optional: SPRING_AI_GOOGLE_GENAI_CHAT_OPTIONS_MODEL=gemini-2.0-flash
```

Without a key, the chatbot module forces `spring.ai.model.*=none` and `SUPPORT_AI_LLM=heuristic` so Docker/CI stay headless-safe. Test profile uses the same safe defaults.

| Service  | URL / endpoint |
|----------|----------------|
| Frontend | http://localhost:3000 |
| API      | http://localhost:8080 |
| Swagger  | http://localhost:8080/swagger-ui.html |
| Health   | http://localhost:8080/actuator/health |
| Grafana  | http://localhost:3001 (admin / admin) |
| Postgres | `localhost:5432` — app runtime user `app_user` / Flyway owner `app_owner` |

Containers: `invsys-web` (nginx SPA), `invsys-api` (Spring Boot), `invsys-db` (Postgres 16).

Copy `.env.example` → `.env` when overriding JWT keys, webhook secrets, or DB credentials. Dev JWT PEMs live under `ops/jwt/` (generated automatically on first `deploy.bat deploy` if missing).

#### Manual `docker compose` (any OS)

```bash
# From repo root
docker compose up --build -d

# Wait for backend healthy, then load demo data (or use: deploy.bat seed):
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed_tenants_extra.sql
```

To toggle chatbot without `deploy.bat`, set compose build/runtime env before build:

```bash
# Disable both sides for this build
export INVSYS_WITH_CHATBOT=false INVSYS_CHATBOT_ENABLED=false VITE_ENABLE_CHATBOT=false
docker compose build backend frontend && docker compose up -d
```

### Demo credentials

Login requires **email** and **password** only — the tenant is resolved from the globally unique email. Load both seed files after the API is healthy:

```bash
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed_tenants_extra.sql
```

Password for every account below: **password123**.

**Shift PIN (floor / scanners only):** on the first visit to a Surface B route, set a **4-digit shift PIN**. Demo / E2E convention: **1234**. The PIN is stored in the browser (IndexedDB) for that device profile — it is not a server password and is **not** shown on office login (`/dashboard`, settings, reports, showroom).

| Role | Office (Surface A) | Floor / scanners (Surface B) | Shift PIN |
|------|--------------------|------------------------------|-----------|
| OWNER | Full (incl. fintech) | Yes | Prompted on first floor visit |
| ADMIN | Ops + settings/billing (not fintech) | Yes | Prompted on first floor visit |
| WAREHOUSE_MANAGER | Orders + floor oversight | Yes | Prompted on first floor visit |
| PICKER | Nav-hidden; login lands on fulfillment | Yes (primary) | Prompted on first floor visit |
| VIEWER | Read-only office | No floor routes | Never |
| B2B_CUSTOMER | Showroom only | No | Never |

Floor routes that arm PIN setup / idle lock: `/fulfillment`, `/inbound/*`, `/cycle-counts`, `/manufacturing/terminal`, `/returns/receive`, `/issue-supplies`, `/replenishments`, `/field/*`.

#### Demo Corp (`demo-corp`) — primary E2E tenant

| Email | Role | Warehouses / LBAC | Shift PIN |
|-------|------|-------------------|-----------|
| owner@demo.test | OWNER | All (WH-01, WH-02) | Yes — use **1234** on floor |
| admin@demo.test | ADMIN | All | Yes — use **1234** on floor |
| manager@demo.test | WAREHOUSE_MANAGER | WH-01 only | Yes — use **1234** on floor |
| picker@demo.test | PICKER | WH-01 only | Yes — use **1234** on floor |
| viewer@demo.test | VIEWER | WH-01 only | No (office only) |
| b2b@demo.test | B2B_CUSTOMER | Showroom portal | No (showroom only) |

#### Acme Wholesale (`acme-wholesale`) — RLS isolation + full roles

| Email | Role | Warehouses / LBAC | Shift PIN |
|-------|------|-------------------|-----------|
| owner@acme.test | OWNER | All (Acme Central, Acme West) | Yes — **1234** on floor |
| admin@acme.test | ADMIN | All | Yes — **1234** on floor |
| manager@acme.test | WAREHOUSE_MANAGER | WH-01 only | Yes — **1234** on floor |
| picker@acme.test | PICKER | WH-01 only | Yes — **1234** on floor |
| viewer@acme.test | VIEWER | WH-01 only | No |
| b2b@acme.test | B2B_CUSTOMER | Showroom | No |

#### Northwind Logistics (`northwind-logistics`) — 3 DCs

| Email | Role | Warehouses / LBAC | Shift PIN |
|-------|------|-------------------|-----------|
| owner@northwind.test | OWNER | All (SEA, CHI, ATL) | Yes — **1234** on floor |
| admin@northwind.test | ADMIN | All | Yes — **1234** on floor |
| manager@northwind.test | WAREHOUSE_MANAGER | WH-SEA + WH-CHI | Yes — **1234** on floor |
| picker@northwind.test | PICKER | WH-SEA only | Yes — **1234** on floor |
| viewer@northwind.test | VIEWER | WH-SEA only | No |
| b2b@northwind.test | B2B_CUSTOMER | Showroom | No |

#### Pacific Parts Co (`pacific-parts`) — single DC hierarchy

| Email | Role | Warehouses / LBAC | Shift PIN |
|-------|------|-------------------|-----------|
| owner@pacific.test | OWNER | WH-PDX | Yes — **1234** on floor |
| admin@pacific.test | ADMIN | WH-PDX | Yes — **1234** on floor |
| manager@pacific.test | WAREHOUSE_MANAGER | WH-PDX | Yes — **1234** on floor |
| picker@pacific.test | PICKER | WH-PDX | Yes — **1234** on floor |
| viewer@pacific.test | VIEWER | WH-PDX | No |
| b2b@pacific.test | B2B_CUSTOMER | Showroom | No |

Quick login (office, no PIN): http://localhost:3000 — **owner@demo.test** / **password123**.  
Floor smoke (PIN **1234** after first open): same password, then open **Fulfillment** or sign in as **picker@demo.test**.

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
