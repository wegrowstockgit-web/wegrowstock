# InventorySystem

Multi-tenant Inventory / WMS / Supply-Chain B2B SaaS with append-only inventory ledger, PostgreSQL Row-Level Security, office + warehouse surfaces, B2B showroom portal, manufacturing, embedded payments/fintech, and offline-capable floor scanning.

## Quick Start

### Prerequisites

- Docker Desktop
- Java 25 (LTS) + Maven (for local backend dev)
- Node.js 24+ (Active LTS; for local frontend / Playwright)

### Run everything with Docker (`deploy.bat`)

On Windows, prefer the repo helper from the project root (quiet console; full log in `.deploy-last.log`). On macOS / Linux use `./deploy.sh` with the same commands:

```bat
deploy.bat deploy              Rem build images, start stack, wait for API health
deploy.bat seed                Rem load demo users / catalog (password123)
deploy.bat status              Rem container status + URLs
deploy.bat down                Rem stop containers (keeps DB volume)
```

```bash
chmod +x deploy.sh
./deploy.sh deploy
./deploy.sh seed
./deploy.sh status
./deploy.sh down
```

| Command | What it does |
|---------|----------------|
| `deploy.bat` / `deploy.bat deploy` | Rebuild and start the full stack |
| `deploy.bat --no-chatbot` | Same as `deploy --no-chatbot` (flag-only first arg is valid) |
| `deploy.bat deploy --clean-frontend` | Wipe `frontends/` app `node_modules`/`dist` first, then deploy |
| `deploy.bat seed` | Apply `ops/demo_seed.sql` (+ extra tenants if present) |
| `deploy.bat status` | Compact `docker compose ps` + endpoint list |
| `deploy.bat down` | Stop/remove containers; Postgres volume kept |
| `deploy.bat clean-frontend` / `./deploy.sh clean-frontend` | Remove `frontend_wms` / `frontend_admin` / `frontend_pos` build artifacts |
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

**Live Gemini (Support Co-Pilot + RAG embeddings):** only a Google AI Studio key is required — defaults use `gemini-2.5-flash` and `text-embedding-004`:

```bash
export GEMINI_API_KEY="your-actual-google-ai-studio-key"
# Optional: SPRING_AI_GOOGLE_GENAI_CHAT_OPTIONS_MODEL=gemini-2.5-flash
```

Without a key, the chatbot module forces `spring.ai.model.*=none` and `SUPPORT_AI_LLM=heuristic` so Docker/CI stay headless-safe. Test profile uses the same safe defaults.

| Plane | Service | URL / endpoint |
|-------|---------|----------------|
| **Data plane** (tenant WMS) | UI | http://localhost:3000 |
| | API gateway | http://localhost:8080 |
| | Swagger | http://localhost:8080/swagger-ui.html |
| | Health | http://localhost:8080/actuator/health |
| **Retail POS** (offline-first register) | UI | http://localhost:3003 |
| | Sync API | `POST /api/v1/pos/sync-receipts` via :8080 |
| **Control plane** (Super Admin) | UI | http://localhost:3002 |
| | Admin API gateway | http://localhost:8081 (`Host: admin.invsys.com`) |
| Observability | Grafana | http://localhost:3001 (admin / admin) |
| Dev SMTP | Mailpit | http://localhost:8025 (SMTP `mailpit:1025` inside Compose) |
| Data | Postgres | `localhost:5432` — runtime `app_user` / Flyway `app_owner` |

Containers: `invsys-web` (WMS SPA), `invsys-admin-web` (admin SPA), `invsys-pos-web` (retail POS SPA), `invsys-api` (WMS Spring Boot + `invsys-pos-api`), `invsys-admin-api` (control-plane Spring Boot :8081), `invsys-api-gateway`, `invsys-db` (Postgres 16), `invsys-mailpit` (local SMTP catcher).

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
docker compose build backend frontend backend-admin frontend-admin frontend-pos && docker compose up -d
```

### Demo credentials & tiers

Login requires **email** and **password** only — the tenant is resolved from the globally unique email. Load both seed files after the API is healthy:

```bash
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed_tenants_extra.sql
```

Password for every account below: **password123**.

**Super Admin (Control Plane)** — identity lives in `platform_admins` (not `users.is_super_admin`):

| Portal | URL | Email | Password |
|--------|-----|-------|----------|
| Super Admin UI | http://localhost:3002 (`admin.invsys.com`) | **owner@demo.test** | **password123** |
| Admin API | http://localhost:8081 | same | same |

**Commercial tier demo tenants** (for entitlement testing in the admin portal):

| Tenant | Slug | Tier | Enabled modules |
|--------|------|------|-----------------|
| Demo Corp | `demo-corp` | **ENTERPRISE** | CORE, B2B_SHOWROOM, FINTECH, AI_COPILOT, RETAIL_POS, MESH_NETWORK |
| Acme Wholesale | `acme-wholesale` | **BASIC** | CORE |
| Northwind Logistics | `northwind-logistics` | **INTERMEDIATE** | CORE, SHOPIFY, ADVANCED_FULFILLMENT |
| Pacific Parts Co | `pacific-parts` | **BASIC** | CORE |

WMS office login for Demo Corp owner remains **owner@demo.test** / **password123** on http://localhost:3000 — that is a *tenant* user row, separate from the platform admin row with the same email.

**Shift PIN (floor / scanners only):** on the first visit to a Surface B route, set a **4-digit shift PIN**. Demo / E2E convention: **1234**. The PIN is stored in the browser (IndexedDB) for that device profile — it is not a server password and is **not** shown on office login (`/dashboard`, settings, reports, showroom).

| Role | Office (Surface A) | Floor / scanners (Surface B) | Shift PIN |
|------|--------------------|------------------------------|-----------|
| OWNER | Full (incl. fintech) | Yes | Prompted on first floor visit |
| ADMIN | Ops + settings/billing (not fintech) | Yes | Prompted on first floor visit |
| WAREHOUSE_MANAGER | Orders + floor oversight | Yes | Prompted on first floor visit |
| PICKER | Nav-hidden; login lands on fulfillment | Yes (primary) | Prompted on first floor visit |
| VIEWER | Read-only office | No floor routes | Never |
| B2B_CUSTOMER | Showroom only | No | Never |
| RETAIL_CASHIER | No WMS — POS register only (`:3003`) | No | Register PIN **1234** (device vault) |
| RETAIL_MANAGER | No WMS — POS supervise / voids | No | Register PIN **1234**; override PIN **5678** |
| SUPPLIER | Vendor ASN portal | No | Never |

Only **Demo Corp** has the `RETAIL_POS` module. Cashier/retail-manager logins on Acme, Northwind, and Pacific succeed the auth gate but the register stays locked (`posEnabled=false`).

**POS manager override PIN** (server `terminal_pin_hash`, unique per tenant): warehouse manager **1234**, retail manager **5678**.

Floor routes that arm PIN setup / idle lock: `/fulfillment`, `/inbound/*`, `/cycle-counts`, `/manufacturing/terminal`, `/returns/receive`, `/issue-supplies`, `/replenishments`, `/field/*`.

#### Demo Corp (`demo-corp`) — primary E2E tenant

| Email | Role | Warehouses / LBAC | Shift PIN |
|-------|------|-------------------|-----------|
| owner@demo.test | OWNER | All (WH-01, WH-02) | Yes — use **1234** on floor |
| admin@demo.test | ADMIN | All | Yes — use **1234** on floor |
| manager@demo.test | WAREHOUSE_MANAGER | WH-01 only | Yes — use **1234** on floor (POS override **1234**) |
| picker@demo.test | PICKER | WH-01 only | Yes — use **1234** on floor |
| viewer@demo.test | VIEWER | WH-01 only | No (office only) |
| b2b@demo.test | B2B_CUSTOMER | Showroom (Metro Distributors) | No (showroom only) |
| cashier@demo.test | RETAIL_CASHIER | POS only (`:3003`) | Register PIN **1234** |
| retailmgr@demo.test | RETAIL_MANAGER | POS only (`:3003`) | Override PIN **5678** |
| supplier@demo.test | SUPPLIER | Vendor portal (Global Parts Inc) | No |

#### Acme Wholesale (`acme-wholesale`) — RLS isolation + full roles

| Email | Role | Warehouses / LBAC | Shift PIN |
|-------|------|-------------------|-----------|
| owner@acme.test | OWNER | All (Acme Central, Acme West) | Yes — **1234** on floor |
| admin@acme.test | ADMIN | All | Yes — **1234** on floor |
| manager@acme.test | WAREHOUSE_MANAGER | WH-01 only | Yes — **1234** on floor (POS override **1234**) |
| picker@acme.test | PICKER | WH-01 only | Yes — **1234** on floor |
| viewer@acme.test | VIEWER | WH-01 only | No |
| b2b@acme.test | B2B_CUSTOMER | Showroom | No |
| cashier@acme.test | RETAIL_CASHIER | POS login, `posEnabled=false` | Register PIN **1234** |
| retailmgr@acme.test | RETAIL_MANAGER | POS login, `posEnabled=false` | Override PIN **5678** |
| supplier@acme.test | SUPPLIER | Vendor portal (Acme Supplier) | No |

#### Northwind Logistics (`northwind-logistics`) — 3 DCs

| Email | Role | Warehouses / LBAC | Shift PIN |
|-------|------|-------------------|-----------|
| owner@northwind.test | OWNER | All (SEA, CHI, ATL) | Yes — **1234** on floor |
| admin@northwind.test | ADMIN | All | Yes — **1234** on floor |
| manager@northwind.test | WAREHOUSE_MANAGER | WH-SEA + WH-CHI | Yes — **1234** on floor (POS override **1234**) |
| picker@northwind.test | PICKER | WH-SEA only | Yes — **1234** on floor |
| viewer@northwind.test | VIEWER | WH-SEA only | No |
| b2b@northwind.test | B2B_CUSTOMER | Showroom | No |
| cashier@northwind.test | RETAIL_CASHIER | POS login, `posEnabled=false` | Register PIN **1234** |
| retailmgr@northwind.test | RETAIL_MANAGER | POS login, `posEnabled=false` | Override PIN **5678** |
| supplier@northwind.test | SUPPLIER | Vendor portal (Cascadia Containers) | No |

#### Pacific Parts Co (`pacific-parts`) — single DC hierarchy

| Email | Role | Warehouses / LBAC | Shift PIN |
|-------|------|-------------------|-----------|
| owner@pacific.test | OWNER | WH-PDX | Yes — **1234** on floor |
| admin@pacific.test | ADMIN | WH-PDX | Yes — **1234** on floor |
| manager@pacific.test | WAREHOUSE_MANAGER | WH-PDX | Yes — **1234** on floor (POS override **1234**) |
| picker@pacific.test | PICKER | WH-PDX | Yes — **1234** on floor |
| viewer@pacific.test | VIEWER | WH-PDX | No |
| b2b@pacific.test | B2B_CUSTOMER | Showroom | No |
| cashier@pacific.test | RETAIL_CASHIER | POS login, `posEnabled=false` | Register PIN **1234** |
| retailmgr@pacific.test | RETAIL_MANAGER | POS login, `posEnabled=false` | Override PIN **5678** |
| supplier@pacific.test | SUPPLIER | Vendor portal (Oregon Precision Metals) | No |

Quick login (office, no PIN): http://localhost:3000 — enter **owner@demo.test**, **Continue**, then **password123**. Identifier-first Home Realm Discovery hides the password until the email (or warehouse IP) is resolved.  
Retail POS register: http://localhost:3003 — **cashier@demo.test** / **password123**, then register PIN **1234**. Voids: warehouse manager PIN **1234** or **retailmgr@demo.test** override **5678**.  
WMS POS config (Demo Corp only): http://localhost:3000/settings?tab=retailPos as **owner@demo.test** — receipt text, USD/MXN, CFDI, blind closeout. Acme/Northwind/Pacific owners do not see that tab.  
Vendor portal: **supplier@demo.test** / **password123** on http://localhost:3000.  
Super Admin portal: http://localhost:3002 (`admin.invsys.com`) — **owner@demo.test** / **password123** via `platform_admins` (control-plane cookies).  
Floor smoke (PIN **1234** after first open): same password, then open **Fulfillment** or sign in as **picker@demo.test**.

## Local Development (without Docker for app code)

```bash
# Start database only
docker compose up db -d

# Apply demo seed (first time / after reset)
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed_tenants_extra.sql

# Data-plane API (WMS)
cd backend
mvn spring-boot:run -pl invsys-app -Dspring-boot.run.profiles=dev

# Control-plane API (Super Admin) — separate process on :8081
mvn spring-boot:run -pl invsys-admin-api -Dspring-boot.run.profiles=dev

# Frontends (pnpm workspace)
cd frontends
pnpm install
pnpm --filter frontend_wms dev      # http://localhost:5173 → WMS API :8080
pnpm --filter frontend_admin dev    # http://localhost:5174 → Admin API :8081
pnpm --filter frontend_pos dev      # http://localhost:5175 → WMS API :8080 (POS sync)
```

Useful scripts: `pnpm --filter frontend_wms build|test|test:e2e`, `pnpm --filter frontend_admin build|test`, `pnpm --filter frontend_pos build|test|test:coverage|test:e2e`.

## Architecture

InventorySystem is split into independently deployable planes:

| Plane | Purpose | Backend | Frontend | Edge |
|-------|---------|---------|----------|------|
| **Data plane** | Tenant WMS / warehouse ops | `invsys-app` (:8080) | `frontends/apps/frontend_wms` | `app.invsys.com` / `:8080` — **blocks** `/api/v1/control-plane/**` |
| **Retail POS** | Offline-first store registers | `invsys-pos-api` (on `invsys-app`) | `frontends/apps/frontend_pos` | `:3003` / Vite `:5175` — syncs via `:8080` |
| **Control plane** | Super Admin ops: entitlements, billing, impersonation, RAG, kill-switch, audit, shards, DLQ, telemetry, compliance | `invsys-admin-api` (:8081) | `frontends/apps/frontend_admin` | `admin.invsys.com` / `:8081` — admin JWT cookies (`invsys_admin_*`, SameSite=Strict) |

Shared engine: `invsys-core` (entities, Flyway through `V121`, `TenantSubscriptionService`). Frontends share `@invsys/shared-types` and `@invsys/shared-ui`. Both APIs must load the **same** RS256 PEMs from `ops/jwt/` so impersonation tokens verify on the WMS.

- **Backend:** Java 25 LTS, Spring Boot 4.1, JPA + Flyway (through `V121`), RS256 JWT, virtual threads, Actuator/Prometheus
- **Database:** PostgreSQL 16, RLS on tenant tables, append-only `inventory_ledger`, trigger-maintained `inventory_levels` (optional `lpn_id` for palletized stock); control-plane writes use `app_owner` / BootstrapJdbc
- **Commercial entitlements:** `tenant_subscriptions` + `@RequireModule` (e.g. FINTECH → 402 `MODULE_LOCKED`)
- **Tenant suspend:** `tenants.status = SUSPENDED` → WMS `SuspendedTenantAccessFilter` returns **403** immediately
- **CQRS dashboard:** `dashboard_kpi_snapshots` read model; refreshed from outbox (`STOCK_LEVEL_CHANGED`, `ORDER_ALLOCATED`, `INVOICE_PAID`, …)
- **Realtime:** SSE `GET /api/v1/dashboard/stream` + `useDashboardStream` (replaces office polling intervals)
- **Frontend (WMS):** React 19.2, TypeScript, Vite, Tailwind (Surface A office / Surface B warehouse), TanStack Query + persist, Zustand, Lucide — **no** control-plane UI (login accepts `?impersonateToken=` only)
- **Frontend (Admin):** Login, tenants (impersonate / suspend / sandbox), billing, Copilot knowledge, integrations kill-switch, audit trail, shards, DLQ, concurrency throttling, global compliance, commercial + health reports. Sell **Retail Point of Sale (POS)** as an Enterprise addon.
- **Frontend (POS):** Split-pane register (cart + tender). Checkout writes Dexie `outbox_receipts` immediately; `invsys-pos-api` converts receipts into `inventory_level_deltas` for the async WMS flush worker. Receipt header/footer, default currency (USD/MXN), CFDI 4.0, and blind closeout are configured in WMS **Settings → Retail POS** (OWNER/ADMIN + `RETAIL_POS`).
- **Surfaces (WMS):** Office shell, warehouse floor ops, B2B showroom (`/showroom`), Mesh Network hub (`/mesh-network`, `MESH_NETWORK`)
- **Digital Twin / LPNs / GS1 / Offline / Tenancy / LBAC / Integrations:** unchanged data-plane capabilities (see `DEVELOPER_ARCHITECTURE.md`)

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

# Control-plane API ITs (JaCoCo gate 85% on com.invsys.admin)
cd backend && mvn -q "-Dsurefire.failIfNoSpecifiedTests=false" -pl invsys-admin-api -am verify

# POS ingest module (JaCoCo gate 85%)
cd backend && mvn -q -pl invsys-pos-api -am verify

# Frontend unit + build (pnpm workspace)
cd frontends && pnpm --filter frontend_wms test && pnpm --filter frontend_wms build
cd frontends && pnpm --filter frontend_admin test && pnpm --filter frontend_admin build
cd frontends && pnpm --filter frontend_pos test:coverage && pnpm --filter frontend_pos build

# E2E (requires docker stack + demo seed on :3000)
cd frontends/apps/frontend_wms && pnpm test:e2e

# Warehouse pillar journeys only
cd frontends/apps/frontend_wms && pnpm exec playwright test e2e/journeys/21-lpn-tote-interleave.spec.ts e2e/journeys/22-pallet-builder.spec.ts e2e/journeys/23-digital-twin-astar.spec.ts e2e/journeys/24-cqrs-sse-gs1-path.spec.ts
```

CI: `.github/workflows/ci-backend.yml`, `ci-frontends.yml` (WMS bundle must contain zero control-plane strings).

Playwright notes:

- `e2e/global.setup.ts` logs in all six demo roles and caches storage under `frontends/apps/frontend_wms/playwright/.auth/` (gitignored)
- Role fixtures: `ownerPage`, `adminPage`, `managerPage`, `pickerPage`, `viewerPage`, `b2bPage`
- Journey helpers: `e2e/journeys/helpers.ts` (`contextForRole`, `hidScan`, `apiJson`)
- Suites include RBAC/LBAC, B2B fulfill cycle, offline picker, cross-dock, blind counts, internal lot mint, **LPN/tote/interleave**, **pallet builder**, **Digital Twin / A\***, **CQRS/SSE/GS1/path**, **mesh network hub** (`e2e/mesh-network.spec.ts`)

## Project Structure

```
InventorySystem/
├── backend/                      # Maven multi-module
│   ├── invsys-core/              # Shared domain, Flyway, tenancy, WMS APIs
│   ├── invsys-chatbot/           # Optional Support Co-Pilot
│   ├── invsys-training/          # Optional Flight Simulator
│   ├── invsys-pos-api/           # Retail POS ingest (offline receipt sync)
│   ├── invsys-app/               # Data-plane bootable runner (:8080)
│   ├── invsys-admin-api/         # Control-plane bootable runner (:8081)
│   ├── Dockerfile                # WMS image
│   └── Dockerfile.admin          # Admin API image
├── frontends/                    # pnpm workspace
│   ├── apps/frontend_wms/        # Tenant WMS SPA + Playwright e2e
│   ├── apps/frontend_admin/      # Super Admin SPA (tenants, billing, ops, reports)
│   ├── apps/frontend_pos/        # Offline-first retail POS PWA
│   ├── packages/shared-types/    # AppModule, CommercialTier, …
│   └── packages/shared-ui/       # Button, Table, Modal, Drawer, Input
├── ops/
│   ├── api-gateway/nginx.conf    # app.* vs admin.* routing
│   ├── terraform/infra/          # Plane-routing SSM profile + cost/HA
│   ├── demo_seed.sql
│   ├── jwt/
│   └── postgres/init/
├── .github/workflows/            # ci-backend, ci-frontends, terraform-*
├── docker-compose.yml            # db + both APIs + WMS/admin/POS UIs + gateway + LGTM
├── deploy.bat
├── deploy.sh
├── DATABASE_GUIDE.md
├── DEVELOPER_ARCHITECTURE.md
├── PRODUCT.md
└── BUILD_PLAN.md
```

## API Overview

Auth & tenancy:

- `POST /api/v1/auth/signup` — Create tenant + owner
- `GET /api/v1/auth/discovery` — Home Realm Discovery (email domain and/or corporate CIDR → SSO or password)
- `POST /api/v1/auth/login` — Access + refresh JWT; optional `targetApp` (`POS`/`WMS`) stamps an `app_context` claim that sandboxes the session to that surface
- `POST /api/v1/auth/impersonation/accept` — Exchange a 15-min control-plane impersonation JWT for WMS cookies
- `POST /api/v1/auth/refresh` — Rotate refresh token (access may be unchanged within the same second); preserves `app_context`

Core inventory & orders:

- `GET /api/v1/scan/{barcode}` — Barcode lookup
- `POST /api/v1/fulfillment/scan` — Floor pick/pack/receive scan
- `GET /api/v1/dashboard/stats` — Office dashboard metrics (CQRS snapshot)
- `GET /api/v1/dashboard/mesh-sourcing-suggestions` — Low-stock SKUs available from connected mesh partners
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

Portal, money & platform (data plane):

- `/api/v1/portal/**` — B2B showroom catalog & draft orders
- `/api/v1/mesh/**` — Discover published partner products, handshake (`connections/request`, `connections/{id}/approve`), shared catalog publish
- `/api/v1/settings/mesh/**` — Connected-partner SKU mappings (Partner Catalog)
- `POST /api/v1/purchase-orders/{id}/confirm` — Submit + synchronous mesh PO→SO (`UNALLOCATED`) when the supplier is a connected partner
- `/api/v1/fintech/**` — Capital / underwriting cockpit (OWNER-gated; `@RequireModule(FINTECH)`)
- `/api/v1/ap-ingestions/**` — Supplier invoice OCR/ingest
- `/api/v1/webhooks/**` + public webhook receivers — Stripe, Shopify, EasyPost
- `GET|PATCH|PUT /api/v1/settings` — tenant JSONB prefs; Retail POS keys (`pos_receipt_header`, `pos_receipt_footer`, `pos_default_currency` USD/MXN, `pos_require_blind_closeout`, `pos_enable_cfdi_invoicing`) are OWNER/ADMIN. The WMS **Settings → Retail POS** tab (`/settings?tab=retailPos`) is shown only when the tenant has `RETAIL_POS`
- users, invitations, SSO, account mappings, tax rates, shipping credentials — invites and `PUT /api/v1/users/{id}/roles` accept **multiple roles** (additive RBAC; union of permissions)
- `POST /api/v1/pos/sync-receipts` — Offline POS receipt batch (`@RequireModule(RETAIL_POS)`); enqueues `inventory_level_deltas`
- `GET /api/v1/pos/managers/sync-pins`, `GET /api/v1/pos/manager-overrides`, `POST /api/v1/pos/audit-sync` — Register manager PIN vault, void-override lookup, offline audit-event batch (`RETAIL_POS`)

Control plane (admin API only — **not** on `:8080`):

- `POST /api/v1/control-plane/auth/login|logout` — Super Admin session (`invsys_admin_*` cookies + CSRF)
- `GET /api/v1/control-plane/auth/me` / `GET .../auth/csrf`
- `GET|PATCH /api/v1/control-plane/tenants/**` — Tier, modules, **status** (`ACTIVE`/`SUSPENDED`)
- `POST /api/v1/control-plane/tenants/{id}/impersonate` — 15-min WMS JWT (`expiresInSeconds=900`) + `loginUrl`
- `POST /api/v1/control-plane/tenants/{id}/clone-sandbox` — Isolated UAT tenant + one-time API key
- `GET /api/v1/control-plane/billing/overview` — Estimated MRR, card/dunning status, usage
- `POST|GET|DELETE /api/v1/control-plane/knowledge/**` — Markdown SOP ingest → PGVector chunks
- `GET /api/v1/control-plane/integrations/traffic` + `POST .../kill-switch` — Pause tenant outbox sync
- `GET /api/v1/control-plane/audit-logs` — SOC 2 Super Admin mutation trail (`platform_audit_logs`)
- `GET|PUT /api/v1/control-plane/shards/**` — Tenant → DB shard / Aurora routing
- `GET|POST /api/v1/control-plane/queues/dead-letters/**` — Cross-tenant failed `outbox_events` + retry
- `GET|PUT /api/v1/control-plane/telemetry/**` — Per-tenant rate-limit multipliers
- `GET|POST /api/v1/control-plane/compliance/broadcasts/**` — Global tax/hazmat fan-out
- `GET /api/v1/control-plane/reports/commercial|health` — Platform GMV, adoption, webhooks, ledger growth

See WMS Swagger UI (`:8080`) for the data-plane surface. Admin OpenAPI is disabled in Docker.
