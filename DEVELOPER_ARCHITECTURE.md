# InventorySystem — Developer & Architect Guide

> **Audience:** Senior developers and architects joining the codebase.  
> **Goal:** Understand structure, class roles, and end-to-end interaction flows quickly.  
> **Diagrams:** ASCII only (no Mermaid).  
> **Companion docs:** `DATABASE_GUIDE.md` (schema story), `USER_GUIDE.md` (operator onboarding), `SEQUENCE_FLOW.md` (role sequences), `README.md` (quick start), `PRODUCT.md` / `BUILD_PLAN.md` (product intent).

---

## Table of contents

1. [What this system is](#1-what-this-system-is)
2. [Repository map](#2-repository-map)
3. [Runtime topology](#3-runtime-topology)
4. [Layered architecture](#4-layered-architecture)
5. [Tenancy, security, and request path](#5-tenancy-security-and-request-path)
6. [Domain model (entities)](#6-domain-model-entities)
7. [Backend packages and classes](#7-backend-packages-and-classes)
8. [Frontend structure](#8-frontend-structure) (nav matrix, tours, products grid)
9. [Sequential business flows](#9-sequential-business-flows)
10. [Cross-cutting mechanisms](#10-cross-cutting-mechanisms)
11. [Database, Flyway, and seed](#11-database-flyway-and-seed)
12. [Integrations and outbox](#12-integrations-and-outbox)
13. [E2E journey matrix](#13-e2e-journey-matrix)
14. [Local ops cheat sheet](#14-local-ops-cheat-sheet)
15. [Onboarding checklist for new seniors](#15-onboarding-checklist-for-new-seniors)

---

## 1. What this system is

**InventorySystem (InvSys)** is a multi-tenant Warehouse Management + light ERP platform:

| Capability | Surface |
|------------|---------|
| Catalog, PO, SO, shipments, invoices | Office (Surface A) — React desktop shell |
| Pick / receive / scan / manufacturing terminal | Floor (Surface B) — glove-friendly scanner UX |
| B2B customer portal | Showroom |
| Supplier portal | Public tokenized PO view |
| Accounting, Shopify, EasyPost, Stripe, EDI, mesh | Outbox-driven integrations |

**Stack**

| Layer | Tech |
|-------|------|
| API | Java 25, Spring Boot 4.1, JPA + selective jOOQ, Flyway |
| DB | PostgreSQL 16, **FORCE RLS**, append-only `inventory_ledger` |
| UI | React 19.2, Vite 7, TanStack Query, Zustand, Tailwind, TanStack Virtual, driver.js |
| Edge | Nginx gateway: data plane `:8080` + control plane `:8081` |
| Infra | Redis (PIN lockout / rate limits), MinIO/S3 media, pgvector for support RAG |
| Auth (WMS) | RS256 JWT in HttpOnly cookies (`invsys_access` / `invsys_refresh`); optional `app_context` claim (`POS`/`WMS`) sandboxes a session to one app surface |
| Auth (Admin) | Isolated admin cookies (`invsys_admin_access` / `invsys_admin_refresh`, SameSite=Strict) + `platform_admin` JWT claim |
| Optional AI | Spring AI (Google GenAI / Gemini) lives only in `invsys-chatbot` — core runs without it |

**Two golden rules** (also in `DATABASE_GUIDE.md`):

1. **Apartment building** — every row is `tenant_id`-scoped; Postgres RLS enforces isolation via `app.current_tenant`.
2. **Bank statement** — inventory never “updates a qty in place” as truth; movements append to `inventory_ledger`; levels are maintained by deltas / flush worker + allocation paths.

**Scale notes (current head V121):** `inventory_ledger` and `audit_log` are monthly RANGE-partitioned; aged audit rows cold-archive to S3/MinIO; credential vault supports `LOCAL` / `AWS_KMS` / `HASHICORP_VAULT`; platform support RAG uses global `support_knowledge_*` tables (pgvector + GraphRAG, no tenant RLS). Control-plane governance lives in `platform_admins` (V106), `tenant_shard_routing` / kill-switch / rate overrides / compliance broadcasts / knowledge docs (V107), and append-only `platform_audit_logs` (V108). Retail POS (`RETAIL_POS`) syncs offline receipts into `pos_synced_receipts` (V111) and enqueues `inventory_level_deltas` without locking `inventory_levels`. Mesh hub (V114) stores published listings in `mesh_catalog_listings` and handshake rows in `tenant_mesh_partners` (`REQUESTED` until approve creates Supplier/Customer).

---

## 2. Repository map

Backend is a **Maven multi-module** build. Core inventory/fulfillment never depends on Spring AI; Support Co-Pilot is an optional module.

```
InventorySystem/
├── backend/                         Maven aggregator (`invsys-parent`, packaging pom)
│   ├── pom.xml
│   ├── Dockerfile                   Builds `-pl invsys-app -am` → WMS fat jar (:8080)
│   ├── Dockerfile.admin             Builds `-pl invsys-admin-api -am` → Admin fat jar (:8081)
│   ├── invsys-core/                 Library: domain, repos, services, tenancy, auth, Flyway
│   │   └── src/main/java/com/invsys/
│   │       ├── api/                 Data-plane REST controllers (no control-plane)
│   │       ├── core/security/       WMS JWT cookies (`invsys_access`); denies `/api/v1/control-plane/**`
│   │       ├── domain/subscription/ AppModule, CommercialTier, TenantSubscription
│   │       ├── service/             Includes TenantSubscriptionService (+ cache eviction)
│   │       └── …                    billing, media, mesh, rtls, modules/*, …
│   ├── invsys-chatbot/              Optional Support Co-Pilot (Spring AI + PgVector RAG)
│   ├── invsys-training/             Optional Flight Simulator (shadow tenant interceptor)
│   ├── invsys-pos-api/              Retail POS: receipt/audit sync, session, manager PIN vault + overrides
│   ├── invsys-app/                  Data-plane runner (`InvSysApplication`, artifact `invsys-api`)
│   │   └── src/test/java/...        WMS integration tests
│   └── invsys-admin-api/            Control-plane runner (`InvSysAdminApplication`)
│       ├── api/                     Auth, tenants, billing, knowledge, integrations,
│       │                            audit, shards, queues, telemetry, compliance, reports
│       ├── audit/                   @PlatformAudit + PlatformAuditAspect
│       ├── security/                AdminSecurityConfig + AdminJwtAuthFilter + admin cookies
│       └── service/                 Impersonation, lifecycle, sandbox, billing, RAG ingest,
│                                    kill-switch, shards, DLQ, telemetry, compliance, reports
├── frontends/                       pnpm workspace (apps/* + packages/*)
│   ├── apps/frontend_wms/           Tenant WMS SPA + Playwright e2e (NO control-plane routes)
│   │   └── src/
│   │       ├── api/                 Axios + DTOs
│   │       ├── components/          Shells + layout (`navConfig.ts`, `Sidebar.tsx`)
│   │       ├── features/            Feature-sliced UI (products, fulfillment, fintech, …)
│   │       ├── lib/router/          `moduleRegistry.ts` (entitlement-aware)
│   │       ├── modules/chatbot/     Optional Support Co-Pilot
│   │       ├── modules/training/    Optional Flight Simulator
│   │       ├── offline/             IDB mutation queue + PIN vault
│   │       ├── pages/               Platform routes
│   │       └── stores/              Zustand session (enabledModules, isSuperAdmin flag only)
│   ├── apps/frontend_pos/           Offline-first retail POS PWA (`:3003` / Vite `:5175`)
│   ├── apps/frontend_admin/         Super Admin SPA (admin.invsys.com)
│   │   └── src/features/            auth, tenants, billing, copilot, integrations,
│   │                                audit, infrastructure, operations, telemetry,
│   │                                compliance, reports
│   ├── packages/shared-types/       AppModule, CommercialTier, ControlPlaneTenant
│   └── packages/shared-ui/          Button, Table, Modal, SlideOutDrawer, Input
├── ops/
│   ├── api-gateway/nginx.conf       Data plane :8080 (blocks CP) + control plane :8081
│   ├── terraform/infra/             Plane-routing SSM + cost/HA profile
│   ├── jwt/                         Dev RS256 PEMs (shared by both APIs)
│   ├── postgres/init/               app_owner / app_user roles + vector ext
│   └── demo_seed.sql                Demo Corp + Acme; Super Admin in platform_admins (owner@demo.test)
├── docker-compose.yml               db, both APIs, WMS + admin + POS UIs, gateway, LGTM
├── deploy.bat                       Windows deploy / seed / status (WMS + POS + admin)
├── deploy.sh                        macOS / Linux parity with deploy.bat
├── .github/workflows/               ci-backend, ci-frontends, terraform-*
├── DATABASE_GUIDE.md
├── USER_GUIDE.md
├── SEQUENCE_FLOW.md
└── DEVELOPER_ARCHITECTURE.md        (this file)
```

| Module | Packaging | Depends on | Notes |
|--------|-----------|------------|-------|
| `invsys-core` | jar | Spring Boot web/security/JPA/Flyway — **no** `spring-ai-*` | Shared by both runners |
| `invsys-chatbot` | jar | `invsys-core` + Spring AI | WMS optional via Maven profile |
| `invsys-pos-api` | jar | `invsys-core` | Always on `invsys-app`; gated by `RETAIL_POS` |
| `invsys-app` | boot jar (`invsys-api`) | `invsys-core` + `invsys-pos-api`; chatbot via **`with-chatbot`** | Data plane — excludes `com.invsys.admin.*` |
| `invsys-admin-api` | boot jar | `invsys-core` | Control plane only — excludes WMS `api.*` controllers |

```
# Default (core + chatbot)
cd backend && mvn -DskipTests package -pl invsys-app -am

# Core-only artifact (no chatbot on classpath)
cd backend && mvn -DskipTests package -pl invsys-app -am -P"!with-chatbot"
```

Runtime toggle (chatbot still on classpath): `invsys.features.chatbot.enabled=false` / `INVSYS_CHATBOT_ENABLED=false`.

### 2.1 Package-by-feature (modular monolith)

Inside `invsys-core`, business code is organized as vertical slices under `com.invsys.modules.*`. Shared infrastructure lives in `com.invsys.core` and **must not** import feature modules (enforced by `ModularMonolithBoundaryTest`).

| Package | Owns |
|---------|------|
| `com.invsys.core.tenancy` | `TenantContext`, RLS filters, DataSource |
| `com.invsys.core.security` | JWT filter, `SecurityConfig`, auth services |
| `com.invsys.core.common` | `ApiException`, MDC, `BaseEntity` / `TenantScopedEntity` |
| `com.invsys.core.integration` | Outbox + credential vault |
| `com.invsys.modules.catalog` | Product / variant / lot / location |
| `com.invsys.modules.inventory` | Ledger, levels, LPN, cycle count |
| `com.invsys.modules.purchasing` | PO, supplier, AP OCR ingestion |
| `com.invsys.modules.sales` | SO, customer, invoicing |
| `com.invsys.modules.fulfillment` | Allocation, picking, shipment |
| `com.invsys.modules.fintech` | Factoring / credit underwriting |

Frontend mirrors this with `frontends/apps/frontend_wms/src/features/{products,purchasing,sales,fulfillment,fintech}` registered via `src/lib/router/moduleRegistry.ts`. Disable a slice with `VITE_ENABLE_<MODULE>=false` to drop its routes and sidebar items without runtime crashes. Commercial module gates also honor `enabledModules` from `/me` (`@RequireModule` on the API).

---

## 3. Runtime topology

### 3.1 Docker traffic (production-like local) — dual plane

```
 Browser
    │
    ├── :3000 ──► invsys-web (frontend_wms SPA)
    │                 │
    │                 └── /api/v1/* ──► api-gateway :8080 ──► invsys-api :8080
    │                                      │
    │                                      └── /api/v1/control-plane/* ──► 404 (blocked)
    │
    ├── :3002 ──► invsys-admin-web (frontend_admin SPA)
    │                 │
    │                 └── /api/v1/control-plane/* ──► api-gateway :8081 ──► invsys-admin-api :8081
    │
    └── Vite data plane :5173 ── proxies /api → :8080
        Vite admin     :5174 ── proxies /api → :8081

 Shared: Postgres :5432, PgBouncer :6432, Redis, MinIO, Grafana :3001
```

| Container | Role |
|-----------|------|
| `invsys-web` | Data-plane SPA (`frontend_wms`) |
| `invsys-admin-web` | Control-plane SPA (`frontend_admin`) |
| `invsys-api-gateway` | Dual listeners: `:8080` data plane (blocks CP), `:8081` control plane (+ CIDR allowlist hook) |
| `invsys-api` | WMS business API; JDBC as `app_user`; Flyway as `app_owner` |
| `invsys-admin-api` | Super Admin API; admin JWT (same PEMs as WMS); entitlements, billing, impersonation, RAG, kill-switch, audit, shards, DLQ, telemetry, compliance |
| `invsys-db` | Postgres 16 + volumes |
| `invsys-pgbouncer` | Transaction pooling |
| `invsys-redis` | PIN lockout / rate limits |
| `invsys-minio` | Media + archives |
| Observability | Prometheus / Loki / Tempo / Grafana (`:3001`) |

### 3.2 Dev vs Docker API path

```
Docker WMS:     Browser → :3000 → gateway:8080 → backend:8080
Docker Admin:   Browser → :3002 → gateway:8081 → backend-admin:8081
Vite WMS:       Browser → :5173 → localhost:8080
Vite Admin:     Browser → :5174 → localhost:8081
```

Terraform encodes the same contract in SSM `/…/infra/plane-routing` (`data_plane_hostname`, `control_plane_hostname`, ports, CIDR allowlist).
---

## 4. Layered architecture

```
┌──────────────────────────────────────────────────────────────────┐
│  PRESENTATION                                                    │
│  React pages → features → ui  |  Playwright journeys             │
│  Zustand (UX) + TanStack Query (server cache) + IDB offline      │
└────────────────────────────┬─────────────────────────────────────┘
                             │ HTTPS + cookies + X-Warehouse-Id
┌────────────────────────────▼─────────────────────────────────────┐
│  EDGE                                                            │
│  Frontend nginx → API gateway nginx → Spring filters             │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│  API                                                             │
│  Controllers (@PreAuthorize) → DTOs / records                    │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│  APPLICATION SERVICES                                            │
│  *Service classes, outbox handlers, media, mesh                  │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│  PERSISTENCE                                                     │
│  JPA repositories (CRUD)  +  jOOQ DSLContext (hot-path SQL)      │
│  TenantAwareDataSource sets app.current_tenant                   │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│  POSTGRES                                                        │
│  FORCE RLS  |  Flyway schema  |  ledger append-only grants       │
└──────────────────────────────────────────────────────────────────┘
```

**Rule of thumb for where to change code**

| Change | Prefer |
|--------|--------|
| New HTTP endpoint | `api/*Controller` + thin call into `service/*` |
| Business rule / status machine | `service/*Service` |
| New table / constraint | Flyway `V0xx__*.sql` + `domain/*` entity |
| Hot read path / warehouse matching SQL | jOOQ in service (not N+1 JPA) |
| Floor UX | `frontends/apps/frontend_wms/src/pages/FulfillmentPage.tsx` + `features/fulfillment/*` |
| Office list UX | `pages/*Page.tsx` + `components/layout/ListPageState.tsx` |

---

## 5. Tenancy, security, and request path

### 5.1 Identity pipeline (every authenticated API call)

```
 Client
   │  Cookie: invsys_access (RS256 JWT)
   │  Optional: Authorization Bearer
   │  Optional: X-Warehouse-Id, Idempotency-Key, X-Offline-Replay
   ▼
 JwtAuthFilter
   │  validate JWT → roles, tenant_id, warehouse_ids
   │  TenantContext.set(tenant, user, warehouses, …)
   ▼
 SuspendedTenantAccessFilter
   │  tenants.status = SUSPENDED → HTTP 403 TENANT_SUSPENDED
   ▼
 WarehouseAccessFilter
   │  LBAC: requested warehouse must be in JWT claims / assignments
   ▼
 @PreAuthorize on controller
   ▼
 TenantTransactionAspect / TenantAwareDataSource
   │  SET app.current_tenant = <uuid>
   ▼
 Service → Repository / jOOQ
   │  RLS policy: tenant_id = current_setting('app.current_tenant')::uuid
   ▼
 Response (+ optional OutboxEvent for async side effects)
```

### 5.2 Roles (Spring `ROLE_*`)

| Role | Typical access |
|------|----------------|
| `OWNER` | Everything including fintech / billing ownership |
| `ADMIN` | Office admin, users, settings, reports |
| `WAREHOUSE_MANAGER` | PO/SO/allocate/waves/returns approve |
| `PICKER` | Floor scan, receive, pick, limited manufacturing/returns receive |
| `VIEWER` | Read-mostly |
| `B2B_CUSTOMER` | Showroom portal only |
| `SUPPLIER` | Authenticated supplier portal |
| `RETAIL_CASHIER` | POS register only (`pos.operate`) — V118 |
| `RETAIL_MANAGER` | POS supervision incl. void overrides (`pos.supervise`) — V118 |
| `SUPER_ADMIN` | Control plane only (`platform_admins` + `token_type=PLATFORM_ADMIN`) — never a WMS role |

Roles are **additive**: a user may hold several `user_roles` rows (e.g. `WAREHOUSE_MANAGER` + `RETAIL_CASHIER`) and permissions are the union. Invites and user edits accept multiple roles (`UserManagementService.applyRolesChange`, `invitations.additional_roles` V120); the Settings → Users UI uses a checkbox `RoleMultiSelect` and renders one badge per role. Cross-app leakage of a multi-role session is blocked by the JWT `app_context` claim (see `JwtAuthFilter` below).

Frontend mirrors this in `useSessionStore.hasRole`, `ProtectedRoute`, and nested `NAV_MATRIX` filters (`roles`, `hideForPicker`, `hideForViewer`) in `components/layout/navConfig.ts`.

### 5.3 DB roles

| Role | Purpose |
|------|---------|
| `postgres` | Container superuser |
| `app_owner` | Flyway migrations; seed via `deploy.bat seed` |
| `app_user` | Runtime JDBC; **`NOBYPASSRLS`** — cannot escape tenant policies |

Bootstrap / invitation accept / some platform webhooks use `BootstrapJdbc` (app_owner connection) when tenant GUC is not yet available.

---

## 6. Domain model (entities)

Entities live in `com.invsys.domain`. Most extend `TenantScopedEntity` (`id`, timestamps, `tenant_id`).

### 6.1 Conceptual ER (simplified)

```
 Tenant ──┬── User / Role / UserWarehouse / Invitation
          ├── TenantSettings / TenantDomain / TenantSsoConfig
          ├── Location (WH → ZONE → AISLE → BIN path)
          ├── Product → ProductVariant → VariantBarcode / UOM / Media
          ├── Supplier → PurchaseOrder → PurchaseOrderLine
          ├── Customer → SalesOrder → SalesOrderLine → Allocation
          ├── InventoryLevel (on_hand, allocated) ← triggers from ledger
          ├── InventoryLedger (append-only movements)
          ├── PickingWave → PickingBatch → PickingTask
          ├── Shipment → ShipmentLine
          ├── ReturnOrder → ReturnLine
          ├── Bom → BomLine / BomOperation / BomOutput
          ├── ProductionOrder / ManufacturingWorkCenter / Timesheet
          ├── Invoice → InvoiceLine / Payment*
          ├── MediaObject → MediaAttachment / TransactionMedia
          ├── OutboxEvent / WebhookEvent / OfflineSyncConflict
          └── AuditLog / IdempotencyKey / FulfillmentException
```

### 6.2 Entity catalog by domain

#### Identity & tenancy

| Entity | Purpose |
|--------|---------|
| `Tenant` | Company / subscription boundary |
| `TenantSettings` | JSON preferences (blind receiving, alerts, costing, …) |
| `TenantDomain` | Custom domain + DNS verification → CORS |
| `TenantSsoConfig` | Per-tenant OIDC/SAML config |
| `TenantMeshPartner` | Cross-tenant trading mesh link (`REQUESTED` → `CONNECTED`) |
| `User` | Login identity |
| `Role` / `UserRole` | RBAC |
| `UserWarehouse` | LBAC warehouse assignment |
| `Invitation` | Pending hire accept flow |
| `RefreshToken` | Rotating refresh sessions |
| `MagicLoginToken` | Email magic-link |
| `WebAuthnCredential` / `WebAuthnChallenge` | Terminal biometric switch |

#### Catalog

| Entity | Purpose |
|--------|---------|
| `Product` | SKU root / product family |
| `ProductVariant` | Sellable unit (barcode, kit flag, lot/serial flags) |
| `VariantBarcode` | Extra barcodes |
| `VariantUomConversion` | Purchasing/standard UOM factors |
| `ProductCategory` / `ProductMedia` | Classification + images |
| `SoftKitComponent` | Soft-kit explode into SO component lines |
| `VolumePriceBreak` | Volume pricing |
| `ExternalReference` | External / mesh IDs |
| `MeshCatalogListing` | Publish-to-network flag + mesh wholesale price |

#### Warehouse topology

| Entity | Purpose |
|--------|---------|
| `Location` | Hierarchical WH/ZONE/AISLE/BIN (path e.g. `WH-01/Z-SHIP/S-01`) |
| `WarehouseContextRule` | Wi-Fi SSID / geofence → warehouse lock |
| `BinReplenishmentRule` | Forward-bin replenishment |
| `VehicleAssignment` | Technician ↔ van location |

#### Inventory core

| Entity | Purpose |
|--------|---------|
| `InventoryLevel` | Current on-hand / allocated by location (+ lot) |
| `InventoryLedger` | Immutable movement history (`RECEIVE`, `ADJUST`, `SHIP`, …) |
| `Lot` / `SerialNumber` | Traceability |
| `LicensePlate` | LPN pallet / tote (`OPEN` → `DISPATCHED`) |
| `WalkableEdge` | Digital Twin travel graph edge |
| `Allocation` | Promise against SO line (`ACTIVE`, `CROSS_DOCK_ROUTED`, …) |
| `CycleCount` / `CycleCountLine` | Cycle count audits |
| `IdempotencyKey` | Replay-safe mutation responses |
| `FulfillmentException` | Skip & Flag without ledger write |

#### Inbound / outbound / returns

| Entity | Purpose |
|--------|---------|
| `Supplier` / `PurchaseOrder` / `PurchaseOrderLine` | Procurement |
| `Customer` / credit / price / catalog restriction | Selling |
| `SalesOrder` / `SalesOrderLine` | Outbound demand (`DRAFT`→`CONFIRMED`→`BACKORDERED`/`ALLOCATED`→ship) |
| `PickingWave` / `PickingBatch` / `PickingTask` | Wave picking (`toteIdentifier` for MIB) |
| `Shipment` / `ShipmentLine` | Ship / pack-label / ship-by-LPN |
| `ReturnOrder` / `ReturnLine` | RMA + disposition |

#### Manufacturing

| Entity | Purpose |
|--------|---------|
| `Bom` / `BomLine` / `BomOperation` / `BomOutput` | Recipe |
| `ProductionOrder` | Build job |
| `ManufacturingWorkCenter` / `ManufacturingOperation` | Routing |
| `ProductionTimesheet` / `TeamLaborRate` | Labor |

#### Commerce & platform

| Entity | Purpose |
|--------|---------|
| `Invoice` / `InvoiceLine` / `Payment` / `PaymentIntent` | AR |
| `StripeAccount` / `FactoredInvoice` / `CapitalCreditLine` | Billing / fintech |
| `MediaObject` / `MediaAttachment` / `TransactionMedia` | Object storage refs |
| `OutboxEvent` / `WebhookEvent` / `ChannelIntegration` | Integrations |
| `OfflineSyncConflict` | Parked offline business failures |
| `AuditLog` | Actor + action + JSON diff |
| `ApInvoiceIngestion` / EDI / tax / cost center / requisition | Adjacent enterprise pillars |

---

## 7. Backend packages and classes

### 7.1 Entrypoint

| Class | Purpose |
|-------|---------|
| `InvSysApplication` | Data-plane Spring Boot main (`invsys-app`) |
| `InvSysAdminApplication` | Control-plane Spring Boot main (`invsys-admin-api`) |

### 7.2 `api` — HTTP surface

Controllers are thin: validate input, authorize, call services, return DTOs/entities.

| Controller | Base concern |
|------------|--------------|
| `AuthController` | Login, refresh, logout, magic, terminal switch, impersonation accept, me |
| `PurchaseOrderController` | Suppliers + PO lifecycle + receive |
| `SalesOrderController` | Customers + SO confirm/allocate/cancel |
| `InventoryController` | Levels, receive/adjust/transfer, ledger reverse |
| `FulfillmentController` | Floor scan, cross-dock check, exceptions |
| `PickingController` / `PickingWaveController` | Waves + cross-dock suggestions |
| `ShipmentController` | Ship + pack labels |
| `ReturnController` | RMA |
| `ProductController` / `ProductVariantController` / `ProductMediaController` | Catalog |
| `LocationController` | Locations / warehouses / put-away |
| `MediaController` | Presign / complete / attach |
| `BomController` / `ProductionOrderController` / manufacturing* | Manufacturing |
| `PortalController` | B2B |
| `PublicWebhookController` / `PublicOauthCallbackController` / `PublicSupplierPortalController` | Public ingress |
| `OfflineSyncConflictController` | Conflict ops |
| `OfficeExceptionController` | Resolve Skip & Flag |
| `DashboardController` / `ReportsController` / `ForecastingController` | Analytics |
| `SettingsController` / `UserController` / `BillingController` / `FintechController` | Admin |
| `LandedCostController` / `Ap*Controller` / `EdiController` / `FieldController` / … | Enterprise extensions |

Notable filter in API package:

| Class | Purpose |
|-------|---------|
| `OfflineReplayBodyFilter` | Cache body when `X-Offline-Replay` for conflict parking |

DTO records live under `api.dto.*` (reports, portal, manufacturing responses, etc.).

### 7.3 `auth` — authentication

| Class | Purpose |
|-------|---------|
| `AuthService` | Credential login, refresh rotation, warehouse PIN, terminal switch; honors `targetApp` on login and stamps `app_context` into access + refresh tokens (context survives rotation via `refresh_tokens.app_context`, V119) |
| `AuthCookieService` | HttpOnly cookie write/clear |
| `JwtService` | RS256 issue/validate; embeds/extracts the `app_context` claim |
| `TaskOrchestratorService` | Dynamic interleaving (pick / putaway / count / predictive REPLENISH) via coords + hierarchy |
| `PredictiveReplenishmentWorker` | VT worker: 48h demand vs pick-face qty → `wave_replenishment_triggers` |
| `TenantIsolationFilter` | Outermost absolute `try/finally` → `TenantContext.clear()` (worker-pool isolation) |
| `JwtAuthFilter` | Bind tenant/user from RS256 JWT; enforces `app_context` audience scoping (POS tokens → `/api/v1/pos/**` + `/api/v1/auth/**` only, WMS tokens barred from `/api/v1/pos/**`, else 403); `filterChain.doFilter` always in `try/finally` with `TenantContext.clear()` |
| `SuspendedTenantAccessFilter` | After JWT bind: `tenants.status = SUSPENDED` → 403 (control-plane dunning) |
| `MdcLoggingFilter` | Request-id MDC enrichment (clears MDC in `finally`) |
| `SecurityConfig` | Filter chain, permitAll public routes |
| `JwksController` | `/.well-known/jwks.json` |
| `MagicLoginService` | Magic-link tokens |
| `TenantSsoResolver` / `oidc.*` / `saml.*` | Enterprise SSO |
| `HomeRealmDiscoveryService` / `CorporateCidrMatcher` | Identifier-first HRD (`GET /api/v1/auth/discovery`) |
| `RedisPinLockoutService` / `TerminalPinBruteForceGuard` | PIN abuse protection |
| `PemUtils` / `PasswordEncoderConfig` | Keys + BCrypt |
| `WarehouseAccessFilter` | LBAC gate |
| `UnauthorizedEntryPoint` | JSON 401 |

### 7.4 `tenancy`

| Class | Purpose |
|-------|---------|
| `TenantContext` | ThreadLocal tenant/user/warehouse/customer/supplier |
| `TenantAwareDataSource` | Bind `app.current_tenant` on borrow |
| `TenantTransactionAspect` | Keep tenant bound across `@Transactional` |
| `DataSourceConfig` | Wire tenant-aware datasource |
| `BootstrapJdbc` | app_owner JDBC for RLS-exempt bootstrap |

### 7.5 `service` — core business (high-signal)

| Service | Responsibility |
|---------|----------------|
| `InventoryService` | Receive / adjust / transfer / ship / quarantine / reverse; LPN consolidate/move/ship-level; lot+serial; outbox stock events |
| `LpnService` | Mint LPN + ZPL; pack levels/barcodes onto LPN; ship LPN contents; contents query |
| `PurchaseOrderService` | Submit; receive lines; **cross-dock intercept**; landed-cost multi-receive |
| `SalesOrderService` | Confirm; allocate → `ALLOCATED` or **`BACKORDERED`**; cancel + audit |
| `AllocationService` | Reserve stock; kit explode; claim/consume/release for pick |
| `CrossDockService` | Match inbound to open SO demand; staging location; fulfill backorders |
| `PickingWaveService` / `PickingService` | Wave generate/optimize/release/claim; **hierarchical location-path** pick sort; A* wayfinding polylines |
| `DashboardKpiService` / `DashboardSseHub` | CQRS KPI snapshot + SSE fan-out from `OutboxDispatchedEvent` |
| `TaskInterleavingService` | Closest next floor task (pick / count / putaway LPN) after a commit |
| `SpatialMapService` | Coordinate PATCH, 7-day ledger heatmap, walkable-edge CRUD |
| `ShipmentService` | Create shipment; consume allocations; EasyPost labels; optional `lpnBarcode` bulk ship |
| `ReturnService` | RMA lifecycle + quarantine |
| `ManufacturingService` / `ManufacturingLaborService` | BOM, production allocate/assemble, labor |
| `KitService` / `SoftKitExplosionService` | Hard kits vs soft kits |
| `ScanService` / `Gs1BarcodeParser` | Barcode resolution + put-away path |
| `FulfillmentExceptionService` | Damaged/skip shunt |
| `LandedCostService` + `service.landedcost.*` | Freight/customs allocation strategies |
| `CostingService` | Moving average / cost bumps |
| `IdempotencyService` | Store/replay keyed responses |
| `AuditService` | Append audit log |
| `AuditLogArchivalWorker` | Nightly S3/MinIO JSONL cold archive + purge (90d default); metrics on failure |
| `AuditArchiveStorageService` | Upload/list/download archive objects |
| `PartitionMaintenanceWorker` | Ensure forward monthly partitions for ledger + audit |
| `InventoryLevelFlushWorker` | Drain `inventory_level_deltas` → `inventory_levels` |
| `OfflineSyncConflictService` | Sink / list / dismiss / retry conflicts |
| `InvoicingService` / `BillingService` / `FintechUnderwritingService` | AR + Connect + capital |
| `PortalService` / supplier portal services | External portals |
| `UserManagementService` / `TenantOnboardingService` / `TenantProvisioningService` | Users & signup |
| `ReplenishmentService` / `PutAwaySuggestionService` | Directed warehouse ops |
| `ReportingAnalyticsService` / `InventoryGenealogyService` / forecasting* | Reports & ML hooks |
| `WebhookService` / `PublicWebhookService` / processors | Webhook inbox |
| `FieldFulfillmentService` / `InternalConsumptionService` | Van + stockroom |
| Schedulers (`PathOptimizationScheduler`, `FxSyncWorker`, …) | Background jobs |

### 7.6 `media`

| Class | Purpose |
|-------|---------|
| `ObjectStorage` / `S3ObjectStorage` | Storage SPI + S3-compatible impl |
| `MediaPreSignService` | Presigned PUT |
| `MediaCompleteService` | HeadObject → `MediaObject` |
| `MediaUploadService` | Server multipart upload |
| `MediaAttachmentService` | Link media to domain entities |
| `ImageContentValidator` / `MediaUrlValidator` | Safety |

### 7.7 `integration` (summary)

| Area | Classes |
|------|---------|
| Outbox core | `OutboxService`, `OutboxDispatcher`, `OutboxEventHandler`, polling config |
| Handlers | EasyPost labels, accounting sync, channel orders, EDI, mesh PO/ship, media, costing |
| Adapters | Shopify / QBO / Xero / EasyPost / Slack / SMTP (live + mock) |
| Vault | `CredentialVaultService` envelope `ENV1` (`LOCAL` / `AWS_KMS` / `HASHICORP_VAULT`) |
| Webhooks | Signature validators + `WebhookReplayDriftFilter` (300s skew → 401) |

### 7.8 `common` / `config` / `gateway`

| Class | Purpose |
|-------|---------|
| `ApiException` | Typed business errors (`code`, HTTP status, properties) |
| `GlobalExceptionHandler` | ProblemDetail; parks offline conflicts as 202 |
| `RequestIdFilter` / `TenantMdcFilter` | Observability |
| `RateLimitFilter` / `DistributedRateLimiter` | Abuse control |
| `JooqConfig` / `JwtProperties` / `RedisConfig` / `OpenApiConfig` / `AsyncConfig` | Infra beans |
| `ProductionSecurityValidator` | Fail-fast if prod secrets are mock |
| `ApiGatewayCorsFilter` / `DynamicCorsWhitelist` | Verified-domain CORS |

### 7.9 `repository`

~99 Spring Data interfaces mirroring entities, plus:

| Extra | Purpose |
|-------|---------|
| `AnalyticsRepository` | jOOQ analytics |
| `OutboxEventRepositoryCustom` / `Impl` | Claim/poll outbox rows |

**jOOQ is used when:** scan matching, cross-dock demand SQL, put-away suggestions, replenishment, genealogy, serial scan, heavy reports — not for ordinary CRUD.

### 7.10 `support` / `invsys-chatbot` — optional in-app copilot (RAG + GraphRAG)

Support Co-Pilot, CQRS tool-calling, Action Drafts, and training-simulator backends live in **`invsys-chatbot`**, not `invsys-core`.

| Piece | Role |
|-------|------|
| `ChatbotAutoConfiguration` | `@ConditionalOnProperty(invsys.features.chatbot.enabled=true, matchIfMissing=true)` + `@ComponentScan(com.invsys.support)` |
| `SupportChatService` | Role-aware chat; heuristic or Gemini (`invsys.support.ai.llm`); retrieves chunks by embedding + audience |
| Spring AI config | `spring.ai.model.chat` / `embedding.text` default `google-genai`; chat model `gemini-2.5-flash`; embeddings `text-embedding-004` via `GEMINI_API_KEY`. Test profile forces `none` + `heuristic`. |
| `SupportCopilotToolsConfig` / `SupportCopilotReadService` | CQRS tools — tenant **only** from `TenantContext` (never LLM/client tenant args) |
| Knowledge repos | Back `support_knowledge_chunks` / `_nodes` / `_edges` (V089–V095; embeddings 768-d as of V092) |
| API | `SupportChatController` → `POST /api/v1/support/chat` (SSE), `/actions/*` |
| Security (core) | `requestMatchers("/api/v1/support/**").authenticated()` — if module/beans absent → **HTTP 404** (no bypass) |
| Frontend | Lazy `SupportAssistantWidget` FAB + tours — gated by `VITE_ENABLE_CHATBOT` / `isChatbotEnabled()` |

Chunks are **global** (no tenant RLS). Never store customer PII or tenant ledger data in the knowledge base.

**Core without chatbot:** build with `-P"!with-chatbot"` or set `invsys.features.chatbot.enabled=false`. Guardrail IT: `CoreModuleWithoutChatbotIT` (receive → allocate → pick → ship; no `SupportChatService` bean).

---

## 8. Frontend structure

### 8.1 Boot sequence

```
main.tsx
  │
  ├─ QueryProvider (PersistQueryClientProvider + offlineFirst)
  ├─ SessionHydrationGate (wait for zustand persist rehydrate)
  ├─ ToastProvider
  ├─ startMutationQueueReplay()  (IDB → API when online)
  └─ <App /> BrowserRouter
        │
        ├─ if isChatbotEnabled(): <ChatbotHost />  (@/lib/chatbot/active → module or stub)
        └─ Routes (office / floor / showroom / public)
```

`src/lib/featureFlags.ts`: `IS_CHATBOT_ENABLED = import.meta.env.VITE_ENABLE_CHATBOT !== 'false'`.  

**Optional frontend module:** `src/modules/chatbot/` (Support FAB, tours, training only). Core never imports it directly — only `@/lib/chatbot/active`, which `scripts/resolve-chatbot.mjs` points at the real module or `src/lib/chatbot/stub`.

**Page Info ("i")** always uses `@/lib/pageKnowledge` (route playbooks). Disabling the chatbot does **not** remove help overlay content.

```
npm run chatbot:enable    # use module (default when folder present)
npm run chatbot:disable   # stub + .chatbot-disabled marker
npm run build:no-chatbot  # production build without chatbot UI
# Or delete src/modules/chatbot — resolve script auto-stubs; tsc + vite still succeed
```

### 8.2 Routing mental model

```
Public:     /login  /signup  /invite/:token  /supplier-portal/po/:token
B2B only:   /showroom/*              → ShowroomLayout
Office:     /dashboard, /products, … → ProtectedRoute(officeOnly) → AppShell
Floor:      /fulfillment, cycle-counts, manufacturing/terminal, returns/receive,
            replenishments, field/truck, inbound/receive
            → ProtectedRoute → WarehouseFloorShell (no corporate sidebar)
Floor home: exclusive PICKER users land on /fulfillment
```

Key office routes: `/dashboard`, `/products`, `/purchase-orders`, `/sales-orders`, `/mesh-network` (`MESH_NETWORK`, OWNER/ADMIN), `/import` (not in sidebar — Products **Import** button), `/exceptions`, `/manufacturing/*`, `/returns`, `/reports`, `/rtls`, `/settings` (Admin → Organization; **Retail POS** tab at `?tab=retailPos` when OWNER/ADMIN **and** `RETAIL_POS`), `/settings/fintech` (OWNER).

**Do not** wrap floor routes in `AppShell` — that regresses glove-friendly hit targets and dual-surface design.

### 8.3 Nested sidebar (`NAV_MATRIX`)

Config: `frontends/apps/frontend_wms/src/components/layout/navConfig.ts`  
UI: `Sidebar.tsx` (expand/collapse categories; mobile drawer ≤1023px).

| Category | Parent icon | Leaf examples |
|----------|-------------|---------------|
| *(solo)* | LayoutDashboard | Dashboard |
| Inbound | DownloadCloud | PO `FileSpreadsheet`, Suppliers `Factory`, Mesh Network `Network`, Returns |
| Outbound | UploadCloud | SO `ShoppingCart`, Customers, Invoices, Fulfillment |
| Inventory | Package | Products `Layers`, Replenishments, Cycle counts, Exceptions, Lot Trace |
| Manufacturing | Component | BOMs, Production Orders |
| Field | MapPin | Issue Supplies, Technician Truck |
| Admin | Settings | Reports, RTLS, Organization (`/settings`) |

Icons must stay unique across parent + leaves. Tour anchors (`tourAnchor`) live on key leaves (e.g. `nav-products`). E2E helpers: `e2e/fixtures/nav.ts` (`expandNavCategory`, `clickNavLink`).

### 8.4 State split (important)

```
┌────────────────────┐     ┌─────────────────────────────┐
│ Zustand            │     │ TanStack Query              │
│ - session (user)   │     │ - server lists / details    │
│ - warehouse pick   │     │ - persisted to IndexedDB    │
│ - scan buffer      │     │ - invalidate after mutate   │
│ - offline quarantine│    │ - offlineFirst networkMode  │
│ - UI density/grid  │     └─────────────────────────────┘
│ - tour machine     │
│ - scanner PIN lock │
└────────────────────┘
         │
         ▼
  JWTs NEVER in JS — HttpOnly cookies only
  apiClient attaches cookies + X-Warehouse-Id + refresh on 401
```

| Store | Holds |
|-------|-------|
| `useSessionStore` | Auth flag, user, roles, terminal primary session |
| `useActiveWarehouseStore` | Selected WH + hardware lock reason |
| `useWarehouseStore` | Allowed warehouses list |
| `useScanBufferStore` | HID buffer / last scan |
| `useWarehouseUXStore` | 5s mis-scan undo |
| `useOfflineStore` / `useSyncConflictStore` | Offline failures / conflict toasts |
| `usePreferencesStore` | Density + **tour machine** (`activeTourId`, `currentTourStep`, `isTourMovingRoutes`, `targetRoute`, `transitionToSubpage` / `clearTour`) |
| `useGridColumnStore` | Per-`gridId` visibility / pin / order (`setColumnVisibilityMap` for Show all / Ops only) |
| `useRailStore` | Sidebar open/collapsed chrome |
| `useScannerLockStore` | Floor PIN hydrate / lock / `tryUnlock` / wipe |
| `useCryptoMemoryKeyStore` | Volatile AES key (RAM only) after PIN unlock |
| `usePrintStore` | Workstation print targets (when used) |
| `useVariantCacheStore` | SKU → lot-tracking for GS1 UX |

PIN material: `offline/pinVault` + IndexedDB verifier. E2E hook: `window.__INVSYS_SCANNER_LOCK__` / `__INVSYS_PREFERENCES__`.

### 8.5 Pages (route → purpose)

| Page | Purpose |
|------|---------|
| `LoginPage` / `SignupPage` / `InvitePage` | Auth & onboarding |
| `DashboardPage` | KPIs, work queue, ledger, conflicts |
| `ProductsPage` | Virtualized catalog + Import dialog + responsive cards |
| `FulfillmentPage` | Surface B scanner hub (`WarehouseFloorShell`) |
| `InboundReceivePage` | Floor PO → item → qty → bin putaway |
| `PurchaseOrdersPage` / `SalesOrdersPage` / `InvoicesPage` | Office order ops (+ tour anchors) |
| `CustomersPage` / `SuppliersPage` | Master data |
| `ExceptionsPage` | Resolve Skip & Flag |
| `ManufacturingBomsPage` / `ManufacturingOrdersPage` / `ProductionTerminalPage` | Manufacturing |
| `ReturnsPage` / `ReturnsReceivePage` | RMA office + floor |
| `IssueSuppliesPage` / `TechnicianTruckPage` | Internal + field |
| `LotTracePage` | Genealogy |
| `ImportPage` | CSV / legacy ingest route (also embedded on Products) |
| `ReportsPage` | Analytics charts |
| `SettingsPage` / `BillingSettingsPage` / `FintechSettingsPage` | Admin |
| `showroom/*` | B2B portal |
| `SupplierPortalPage` | Public supplier PO |

### 8.6 Features modules

| Feature | Purpose |
|---------|---------|
| `fulfillment/ScannerView` | Scan history, GS1 fields, Skip & Flag |
| `fulfillment/CrossDockOverlay` | Bypass put-away → staging instruction |
| `fulfillment/QuarantineReview` / `ReplenishmentQueue` | Floor side panels |
| `inventory/LedgerHistoryTable` | Dashboard reverse UX |
| `offline/SyncConflictsPanel` | Office conflict resolution |
| `ingestion/ImportWizard` | Import UX (Products dialog + `/import`) |
| `compliance/LotTraceView` | Genealogy UI |
| `modules/chatbot` (optional) | `ChatbotHost`, tours, FAB, training sandbox, route knowledge |
| `lib/chatbot/active` | Generated bridge — stub when module disabled/absent |
| `settings/*` | Integrations, carriers, accounting, warehouse map, `PosSettingsPanel` (`/settings?tab=retailPos`) |

### 8.7 Design system & products grid

Buttons (`Button`, `BigButton`), forms (`Input`, `Select`), `Card`, `Modal`/`AlertDialog`, enterprise `Table` + **`VirtualizedTable`** (TanStack Virtual, sticky/pinned columns, density `estimateSize` 32/44/64, non-sticky `flexGrow` only — never inflate pinned Name into a canyon), drawers, density toolbar, `ColumnVisibilityMenu` (**Show all** / **Ops only** presets when `opsOnlyColumnIds` set), `ScanFlashOverlay`, `UndoToast`, media capture. Theming via `data-theme=office|warehouse` and `data-density`.

**Products (`gridId="products"`):**

| Concern | Implementation |
|---------|----------------|
| Ops preset ids | `sku,name,barcode,onHand,allocated,atp,reorder,uom,channelSync` |
| Sticky freeze | Default pin `sku` + `name` (+ non-hideable `thumb`) |
| Desktop | Sticky left identifiers; H-scroll for overflow |
| Tablet (768–1023) | Shed compliance columns (`TABLET_SHED_COLUMN_IDS`); `minRowPx ≥ 48` |
| Mobile (<768) | Unmount table → `ProductMobileCards` |
| Density | Compact 32 / Cozy 44 / Spacious 64 via `usePreferencesStore` |
| Search | `useConcurrentSearch` (`useDeferredValue` + `useTransition`) |

E2E: `e2e/products-responsive-grid-matrix.spec.ts`, journeys 37/39/40.

### 8.8 Multi-page tours (driver.js)

| TourId | Routes | Notes |
|--------|--------|-------|
| `office` | SO / products / density / columns | Single-app-shell highlights |
| `floor` | fulfillment / inbound | Scanner shell |
| `receiving-to-allocation` | PO → `/inbound/receive` → SO (6 steps) | `transitionToSubpage` destroys driver, sets `isTourMovingRoutes` + `targetRoute`, navigates; `TourOrchestrator` resumes on `requestAnimationFrame` after pathname match |

Progress text: `Step {{current}} of {{total}}`. Final done: **Finish Onboarding** → `clearTour()`.

---

## 9. Sequential business flows

### 9.1 Login → session → shell

```
[LoginPage]
    │ GET /api/v1/auth/discovery?email=…  (IP via ClientIpResolver, then verified domain)
    │   → sso-redirect | sso-optional | password
    │ POST /api/v1/auth/login {email,password}  (password path only)
    ▼
[AuthController] → [AuthService.login]
    │ verify password, load roles (may be several — additive) + warehouseIds
    │ JwtService.createAccessToken (+ app_context claim when login sent targetApp POS/WMS)
    │ persist RefreshToken hash (carries app_context across rotation)
    │ AuthCookieService.writeSessionCookies
    ▼
[Browser] cookies set; Zustand setSessionFromLogin
    │ navigate by role
    ▼
[AppShell]
    │ GET /api/v1/auth/me → applyMeProfile
    │ load warehouses; WarehouseContextGate (SSID/geo)
    ▼
[Sidebar] NAV_MATRIX categories; filtered by hasRole / hideForPicker / hideForViewer
```

### 9.2 Purchase order → receive (standard)

```
Manager (Surface A)
  POST /purchase-orders          → DRAFT + lines
  POST /purchase-orders/{id}/submit → SUBMITTED (+ outbox → CONFIRMED seller SO if mapped mesh)
  POST /purchase-orders/{id}/confirm → submit + sync UNALLOCATED seller SO + PO note (mesh)
       │
       ▼
Picker / Manager
  POST /purchase-orders/lines/{lineId}/receive
       │
       ▼
[PurchaseOrderService.receiveLine]
       │ UOM → standard qty
       │ InventoryService.receive (reason PO_RECEIVE)
       │ refresh PO PARTIALLY_RECEIVED / RECEIVED
       ▼
 inventory_ledger RECEIVE
       → inventory_level_deltas (+on_hand)   [lock-free append]
       → InventoryLevelFlushWorker (VT)      [batch upsert inventory_levels]
```

Outbound shipping cartonization uses First-Fit Decreasing 3D packing (`CartonizationEngine`) and returns `packing[]` placements from `/shipments/cartonize-preview`. Floor packing uses `useDigitalScale` (Web Serial + Web Bluetooth); a stable scale reading auto-triggers pack-label. PWA `sw.js` applies **cache-first** to `/api/v1/media/*`. JDBC URLs set `prepareThreshold=0` for PgBouncer transaction pooling.

### 9.3 Sales order → allocate → wave → pick → ship

```
 DRAFT
   │ confirm
   ▼
 CONFIRMED
   │ allocate (AllocationService per line)
   ├─ stock found ──► ALLOCATED  (+ ACTIVE allocations)
   └─ no stock ─────► BACKORDERED (qtyAllocated = 0)
   │
   ▼
 Wave: generate → optimize → release → claim
   │
   ▼
 Floor pick: FulfillmentController scan mode=pick
   │ assertPickable → InventoryService.adjust(-qty) → consumeForPick
   ▼
 Ship: ShipmentService.createShipment
   │ InventoryService.ship + outbox SALES_ORDER_SHIPPED
   ▼
 SO → SHIPPED / PARTIALLY_SHIPPED
```

### 9.4 Cross-dock orchestration (Track 13)

This is the intercept path when inbound stock matches an open / backordered SO.

```
 Manager                         Engine                              Picker (mobile)
 ────────                        ──────                              ──────────────
 Create SO (0 OH)
 Confirm + Allocate ──► status BACKORDERED
 Create PO 50u SUBMITTED
                                    CrossDockService.previewOpenDemand
                                    sees BACKORDERED SO (priority 0)
                                                         Receive mode
                                                         HID scan product
                                    FulfillmentController.scan
                                    checkVariant → match
                                                         ◄── CrossDockOverlay
                                                             "go to Z-SHIP/S-01"
                                                             NOT Z-A/A-1/B-01
                                                         HID scan S-01 (confirm UI)
 Receive PO line ──────► PurchaseOrderService.receiveLine
                            location forced → staging
                            reason CROSS_DOCK_ROUTING
                            InventoryService.receive @ S-01
                            fulfillOpenDemand
                               allocation CROSS_DOCK_ROUTED
                               SO → ALLOCATED
 Ledger: RECEIVE @ staging + CROSS_DOCK_ROUTING
 UI (TanStack refetch): BACKORDERED → ALLOCATED
```

**Classes to read first for this path**

1. `SalesOrderService.allocate`  
2. `CrossDockService` (`previewOpenDemand`, `checkVariant`, `fulfillOpenDemand`)  
3. `PurchaseOrderService.receiveLine`  
4. `FulfillmentController.executeScan` (receive branch)  
5. `frontend/.../CrossDockOverlay.tsx` + `FulfillmentPage.tsx`

### 9.5 Fulfillment scan (online)

```
 HID / DataWedge / intent
        │
        ▼
 useBarcodeScanner → onScan(code)
        │
        ├─ (if cross-dock prompt + staging barcode) → confirm UI only
        │
        ▼
 POST /api/v1/fulfillment/scan
   Header: Idempotency-Key (required)
        │
        ▼
 IdempotencyService.find → replay if cached
        │
        ▼
 Resolve variant (barcode → SKU → GTIN)
        │
   mode=receive                 mode=pick
        │                            │
        ├─ checkVariant              ├─ assertPickable
        │   match? → overlay         ├─ adjust(-qty) SCAN_PICK
        │   else blind-receive?      └─ consumeForPick
        └─ InventoryService.receive
```

Offline path: `bufferMisScan` (5s undo) → `enqueueMutation` (IndexedDB) → replay with `X-Offline-Replay`; business errors → `OfflineSyncConflict` (HTTP 202).

### 9.6 Manufacturing assembly

```
 BOM CRUD (ManufacturingService)
    │
 ProductionOrder create
    │ allocate components (allocations / levels)
    │
 ProductionTerminalPage scans recipe SKUs
    │ assemble → consume components + RECEIVE finished goods
    │ ledger reason ASSEMBLY (and related)
    ▼
 Complete / optional disassemble
```

Hard kits use `KitService` during SO allocation; soft kits explode at SO create via `SoftKitExplosionService`.

### 9.7 Returns (RMA)

```
 Office: POST /returns → approve
 Floor:  /returns/receive → scan → receive line
           │
           ├─ RESTOCK → inventory receive (often via quarantine)
           └─ SCRAP   → disposition without sellable OH
 release-from-quarantine when disposition allows
```

### 9.8 Media upload

```
 GET  /media/presign-upload  → MediaPreSignService
 Client PUT bytes → MinIO/S3
 POST /media/complete        → MediaCompleteService (HeadObject → MediaObject)
 POST /media/attachments     → link to product / transaction / etc.
```

Alternate: multipart `MediaUploadService`.

### 9.9 Offline conflict parking

```
 Client mutation (offline queued) with X-Offline-Replay: 1
        │
 Business ApiException (409/422)
        │
 GlobalExceptionHandler
        │ OfflineSyncConflictService.sink
        ▼
 HTTP 202 { conflictId }
        │
 Office Dashboard / Settings → OfflineSyncConflictController
        dismiss / retry / resolve
```

---

## 10. Cross-cutting mechanisms

| Mechanism | Where | Notes |
|-----------|-------|-------|
| RLS | Postgres + `TenantAwareDataSource` | FORCE RLS; runtime user cannot bypass |
| JWT cookies | `AuthCookieService` | Access ~15m, refresh ~7d |
| LBAC | `UserWarehouse` + `WarehouseAccessFilter` + `X-Warehouse-Id` | Terminal may lock warehouse via SSID/geo |
| Idempotency | `IdempotencyService` | Required on fulfillment scan |
| Audit | `AuditService` + archival worker | Hot `audit_log` (partitioned); cold S3 after retention |
| Outbox | `OutboxService` + dispatcher | At-least-once integration side effects |
| Append-only ledger | `InventoryLedger` grants + partitions | Reverse via compensating entry, not UPDATE |
| Edge rate limits | `ops/api-gateway/nginx.conf` | Magic-login ~5 r/m; fulfillment scan ~120 r/m |
| Credential vault | `CredentialVaultService` | Envelope encryption; provider switchable |
| Webhook drift | `WebhookReplayDriftFilter` | Stripe/Shopify timestamp window 300s |
| Virtual threads | Spring config | High concurrency I/O (flush / archival / outbox) |
| ProblemDetail errors | `GlobalExceptionHandler` | Stable `code` field for clients |
| Metrics | `WmsMetrics` | Includes `wms.audit.archive.failures` (tenant-tagged) |

---

## 11. Database, Flyway, and seed

### 11.1 Migration convention

Files: `backend/invsys-core/src/main/resources/db/migration/Vnnn__description.sql`  
JPA: `ddl-auto: validate` (schema owned by Flyway).

| Theme | Versions (approx.) |
|-------|-------------------|
| Foundation (identity→billing) | V001–V008 |
| RLS & grants | V009 (+ nullsafe evolutions) |
| Manufacturing / returns / portal | V012–V015 |
| Enterprise / SSO / serials | V018–V024 |
| WMS ops / LBAC / landed cost | V025–V043 |
| Media | V044–V046 |
| Fulfillment / kits / lots / exceptions | V047–V050 |
| Ingestion / tax / replenishment / AP | V051–V058 |
| Mesh / domains / alerts / subscription | V053–V062 |
| Invitation bootstrap | V063 |
| Cross-dock `BACKORDERED` status | V064 |

Staging bins (`Z-SHIP` / `S-01`) are seeded in `ops/demo_seed.sql` (not Flyway inserts — FORCE RLS blocks migrator inserts without BYPASSRLS).

Recent warehouse pillar migrations (keep Flyway head current):

| Version | Purpose |
|---------|---------|
| `V071` | Blind cycle-count settings + variance columns |
| `V072` | `license_plates`, `lpn_id` on levels/ledger, `picking_tasks.tote_identifier` |
| `V073` | LPN status `DISPATCHED` |
| `V074` | `coord_x/y/z` on `locations`, `walkable_edges` + RLS |
| `V075` | `dashboard_kpi_snapshots` CQRS read model for `/dashboard/stats` |
| `V076` | Async `inventory_level_deltas` hotspot flush (virtual threads) |
| `V077` | `wave_replenishment_triggers` predictive replenishment |
| `V078` | Enterprise master data (facility specs, customer/supplier/user fields) |
| `V079` | `floor_load_capacity_lbs` + FSMA `vendor_lot_captured` ledger index |
| `V080` | ProductVariant enterprise trade/handling/lifecycle + location putaway constraints + shipment DG flag |
| `V081` | Two-tier user profile fields + UI density preferences |
| `V082` | Sales order status `NEEDS_REVIEW` |
| `V083` | `integration_channels` (+ RLS) and richer sync log columns |
| `V084` | Location lat/long; `rtls_tags` / `rtls_position_events` |
| `V085` | Append-only audit trigger hardening |
| `V086` | `archive_purge_audit_logs` SECURITY DEFINER |
| `V087` | RANGE partition `inventory_ledger` + `audit_log` by `created_at`; `ensure_monthly_partitions` |
| `V088` | Ensure ~12 months back + 6 months forward partitions (idempotent) |
| `V089` | `support_knowledge_chunks` + HNSW pgvector(384) — global RAG corpus |
| `V090` | `support_knowledge_nodes` / `support_knowledge_edges` — GraphRAG |
| `V091` | Offline sync conflict metadata |
| `V092` | Support RAG embeddings → 768-d + support tickets |
| `V093` | Training sandbox bindings |
| `V094`–`V095` | Hybrid FTS + hierarchical RAG metadata |
| `V096` | Invoice document URL |
| `V097` | Enterprise feature matrix |
| `V098`–`V099` | RBAC permission matrix + seed |
| `V100` | Tenant business automations |
| `V101` | RTV + supplier chargebacks |
| `V102` | Dock-door scheduling |
| `V103` | Floor labor time tracking |
| `V104` | `tenant_subscriptions` (tier + enabled_modules) |
| `V105` | AppModule catalog expand (SHOPIFY…AI_COPILOT) |
| `V106` | `platform_admins` + `platform_admin_refresh_tokens` (Super Admin off `users`) |
| `V107` | Shard routing, kill-switch, rate overrides, compliance broadcasts, knowledge docs, sandbox credentials |
| `V108` | `platform_audit_logs` — append-only Super Admin mutation trail |
| `V109` | `platform_tier_definitions` (dynamic commercial bundles) |
| `V110` | Security hardening |
| `V111` | `pos_synced_receipts` + ENTERPRISE includes `RETAIL_POS` |
| `V112` | B2B RFQ statuses + allocation policy |
| `V113` | `wholesale_applications` |
| `V114` | Mesh hub handshake + `mesh_catalog_listings` + PO `notes` |
| `V115` | Home Realm Discovery: domain TXT/`is_verified` + SSO provider/ACS/cert/corporate CIDRs |
| `V116` | `app_owner` INSERT policy on `tenants` (clone-sandbox / training UAT under FORCE RLS) |
| `V117` | ENTERPRISE `tenant_subscriptions` backfill for `B2B_SHOWROOM` + `MESH_NETWORK` |
| `V118` | Retail POS roles `RETAIL_CASHIER` / `RETAIL_MANAGER` + `pos.operate` / `pos.supervise` |
| `V119` | `refresh_tokens.app_context` — POS/WMS JWT audience survives refresh |
| `V120` | `invitations.additional_roles` — multi-role invites |
| `V121` | `roles.network_access_level` (LAN / ANY) |

Retail POS **WMS settings** (receipt header/footer, `pos_default_currency` USD/MXN, CFDI, blind closeout) are JSONB keys on existing `tenant_settings.settings` — no extra Flyway column. `GET|PATCH|PUT /api/v1/settings` (`SettingsController` / `TenantSettingsDto`) validate currency and 2000-char receipt text. The WMS tab is gated in `posSettingsAccess.canConfigureRetailPos` (OWNER/ADMIN **and** explicit `RETAIL_POS`; empty modules do not unlock).

**Compliance pillars (enforced in code + tests):** DSCSA GS1 AI 21 serial (FE+BE parsers / scan fallback); FSMA §204 lot metadata genealogy; GAAP ledger append-only + double-reversal guards; SOC 2 tenant GUC + PgBouncer `DISCARD ALL` + RFC 7807 Problem Details.

### 11.2 Seed

```
deploy.bat seed
  → docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
```

Demo login (slugless): `owner@demo.test` / `password123` (tenant inferred from email).  
Extra tenants: `ops/demo_seed_tenants_extra.sql` (manual; not in `deploy.bat seed`).

**Do not** manually insert `inventory_levels` in seed — `on_hand` is flushed from ledger deltas (`V076`); seed via ledger receives.

---

## 12. Integrations and outbox

```
 Domain mutation (e.g. PO submit, SO ship, stock change)
        │
        ▼
 OutboxService.append(OutboxEvent)
        │
        ▼
 OutboxDispatcher (poll)
        │
        ├─ EasyPostLabelHandler
        ├─ AccountingSyncHandler (QBO/Xero)
        ├─ ChannelOrderWebhookHandler / Shopify*
        ├─ PurchaseOrderSubmittedMeshHandler
        ├─ SalesOrderShippedMeshHandler
        ├─ EdiOutboxHandler
        └─ …
        │
        ▼
 External systems (or Mock* adapters in tests/dev)
```

Credentials: `CredentialVaultService` envelope encryption (`invsys.integration.vault-provider` = `LOCAL` | `AWS_KMS` | `HASHICORP_VAULT`).  
Webhooks: public controllers + signature validators (Stripe, EasyPost, Shopify) + **`WebhookReplayDriftFilter`** (300s).  
Audit cold path: `AuditLogArchivalWorker` → gzip JSONL → object storage → `archive_purge_audit_logs` only after 2xx.

---

## 13. E2E journey matrix

Playwright specs under `frontends/apps/frontend_wms/e2e/journeys/`. Helpers: `helpers.ts` (`contextForRole`, `hidScan`, `apiJson`).

| Track | Spec | Validates |
|------:|------|-----------|
| 1 | `01-onboarding-rbac.spec.ts` | Invite picker → Surface B only |
| 2 | `02-procurement-fulfillment.spec.ts` | PO receive ↔ SO pick/ship |
| 3 | `03-exception-handling.spec.ts` | Skip & Flag → office resolve |
| 4 | `04-audit-monitoring.spec.ts` | Owner audit trail |
| 5 | `05-b2b-showroom.spec.ts` | Portal order → admin confirm |
| 6 | `06-manufacturing-assembly.spec.ts` | BOM allocate → complete build |
| 7 | `07-returns-disposition.spec.ts` | RMA RESTOCK/SCRAP |
| 8 | `08-financial-boundaries.spec.ts` | Fintech OWNER-only walls |
| 9 | `09-offline-conflict.spec.ts` | Offline violation → conflict |
| 10–12 | `10-hardware-scanning.spec.ts` | HID / intent shim / haptics |
| 13 | `13-cross-docking-orchestration.spec.ts` | Backorder → staging receive → ALLOCATED |
| 19 | `19-blind-cycle-count.spec.ts` | Blind count + variance escalation |
| 20 | `20-internal-lot-mint.spec.ts` | Internal lot mint + ZPL on receive |
| 21 | `21-lpn-tote-interleave.spec.ts` | LPN mint/pack/move, MIB totes, next-best-action |
| 22 | `22-pallet-builder.spec.ts` | Build Pallet mint → pack → ship-by-LPN |
| 23 | `23-digital-twin-astar.spec.ts` | Coordinates, heatmap, A* wayfinding, mini-map |
| 24 | `24-cqrs-sse-gs1-path.spec.ts` | CQRS stats, SSE stream, client GS1 reject, hierarchical path |

Backend integration tests: `AbstractIntegrationTest` (Testcontainers Postgres + MinIO), under `backend/invsys-app/src/test/java/com/invsys`. High-signal coverage for this pillar:

| Test | Covers |
|------|--------|
| `CoreModuleWithoutChatbotIT` | `invsys.features.chatbot.enabled=false` — no `SupportChatService`; receive→allocate→pick→ship; `/api/v1/support/**` → 404 |
| `LpnMoveHttpTest` | Bulk LPN move + ledger lines |
| `LpnPalletizationHttpTest` | Mint → pack → ship → `DISPATCHED` |
| `TaskInterleavingHttpTest` | `GET /tasks/next-best-action` closest COUNT |
| `PickingWaveToteHttpTest` | Wave `toteIdentifier` assignment |
| `AStarPathfindingTest` | Wayfinding polyline over walkable graph |
| `SpatialMapHttpTest` | Coordinate PATCH, heatmap, walkable edges |
| `PathOptimizationHeuristicTest` | Hierarchical path sort |
| `DashboardKpiCqrsHttpTest` | Snapshot read model for `/dashboard/stats` |
| `DashboardStreamHttpTest` | SSE `/dashboard/stream` auth + subscribe |
| `MeshHandshakeHttpTest` | Discover (no price), request, forbid self-approve, Supplier/Customer on approve |
| `MeshSourcingSuggestionsHttpTest` | Low-stock + published partner SKU → dashboard suggestions |
| `CrossTenantMeshBridgeTest` | Submit→CONFIRMED SO; confirmOrder→UNALLOCATED + PO note; unmapped exception |
| `AuditLogArchivalWorkerIT` | LocalStack S3 + Awaitility cron + Toxiproxy chaos (no silent purge) |
| `PartitionedTelemetryIT` | Ledger/audit partition presence + immutability |

Frontend grid/scroll journeys: `18-grid-customization`, `37-column-visibility-menu`, `39-products-table-dashboard-scroll`, `40-products-customers-layout`, `products-responsive-grid-matrix` (desktop H-scroll + tablet shed + mobile cards + Show all/Ops chaos), `sticky-table-headers`, `surface-a-enterprise-grid`.

Tour / nav personas: `support-multipage-tour.spec.ts` (receiving-to-allocation step counters), `tests/e2e/admin.spec.ts` / `picker.spec.ts` (grouped rail expand), `e2e/fixtures/nav.ts`.

Decoupled module: `tests/e2e/decoupled-module.spec.ts` — Test A (FAB + tool-calling) / Test B (`VITE_ENABLE_CHATBOT=false` / `__INVSYS_CHATBOT__=false`: no FAB; picker inbound + fulfillment; zero console errors).

---

## 14. Local ops cheat sheet

```
deploy.bat deploy          Build & start full stack (WMS + POS + admin)
./deploy.sh deploy         macOS / Linux equivalent
deploy.bat --no-chatbot    Same as deploy --no-chatbot (flag-only first arg works)
deploy.bat seed            Load demo_seed.sql
deploy.bat status          Compose ps + WMS / POS / admin URLs
deploy.bat down            Stop (keeps pg volume)
deploy.bat help            Usage

WMS UI:    http://localhost:3000
POS UI:    http://localhost:3003
Admin UI:  http://localhost:3002
WMS API:   http://localhost:8080   (control-plane paths blocked)
Admin API: http://localhost:8081
Swagger:   http://localhost:8080/swagger-ui.html
MinIO:     http://localhost:9001

# Backend (from backend/)
mvn -DskipTests package -pl invsys-app -am
mvn -DskipTests package -pl invsys-admin-api -am
mvn spring-boot:run -pl invsys-app -Dspring-boot.run.profiles=dev
mvn spring-boot:run -pl invsys-admin-api -Dspring-boot.run.profiles=dev
mvn -Dtest=CoreModuleWithoutChatbotIT -Dsurefire.failIfNoSpecifiedTests=false test -pl invsys-app -am
mvn -DskipTests package -pl invsys-app -am -P"!with-chatbot"

# Frontends (pnpm workspace)
cd frontends
pnpm install
pnpm --filter frontend_wms chatbot:enable
pnpm --filter frontend_wms build
pnpm --filter frontend_admin build

# Docker deploy — both planes; chatbot toggle affects WMS only
deploy.bat chatbot-enable
deploy.bat chatbot-disable
deploy.bat deploy
deploy.bat deploy --no-chatbot
deploy.bat deploy --with-chatbot
```

JWT PEMs: `ops/jwt/dev-*.pem` (generated by deploy if missing; shared by both APIs).  
Env template: `.env.example`. Runtime chatbot off (beans): `INVSYS_CHATBOT_ENABLED=false`.

---

## 15. Onboarding checklist for new seniors

**Day 0 — run it**

1. `deploy.bat deploy` then `deploy.bat seed`  
2. Login WMS `owner@demo.test` / `password123` at `:3000`  
3. Login Control Plane same email at `:3002` (row in `platform_admins`)  
4. Open Swagger; hit `GET /api/v1/auth/me`  
5. Skim `DATABASE_GUIDE.md` § golden rules  

**Day 1 — follow one vertical**

1. Trace **SO allocate** in `SalesOrderService` + `AllocationService`  
2. Trace **PO receive** in `PurchaseOrderService` + `InventoryService.receive`  
3. Trace **floor scan** `FulfillmentController` ↔ `FulfillmentPage`  
4. Read RLS: `TenantContext` → datasource → `V009` policies  

**Day 2 — hard paths**

1. Cross-dock: §9.4 + Track 13 e2e  
2. Offline: `mutationQueue.ts` + `OfflineSyncConflictService`  
3. Outbox: one handler end-to-end  
4. Media: presign → MinIO → complete  

**When adding features**

1. Migration first (Flyway), entity, repository  
2. Service method with clear status transitions  
3. Controller with `@PreAuthorize`  
4. Frontend query key + invalidate  
5. Integration test and/or journey if multi-role  

**Anti-patterns to avoid**

- Updating `inventory_levels` as source of truth (write ledger instead)  
- Putting business rules in controllers or React  
- Storing JWT/access tokens in `localStorage`  
- Inserting tenant rows in Flyway without RLS strategy (prefer seed / SECURITY DEFINER)  
- Calling external APIs inside the request transaction (use outbox)  
- Wrapping Surface B routes in office `AppShell`  
- Absolute `translateY` row virtualization that breaks sticky table headers (use spacer rows)  
- Growing pinned identifier columns to fill the viewport (creates a sticky “canyon”; grow non-sticky cols only)  
- Putting Import back on the office rail (it lives on Products / `/import`)  
- Storing tenant secrets in `support_knowledge_*` (global, no RLS)  
- Ad-hoc `DELETE FROM audit_log` (use archival worker + security-definer purge)  
- Adding Spring AI / Support Co-Pilot code to `invsys-core` (keep it in `invsys-chatbot`)  
- Trusting `tenantId` from LLM tool arguments (always use `TenantContext`)

---

## Appendix A — Status machines (quick reference)

**Sales order**

```
DRAFT → CONFIRMED → BACKORDERED ─┐
                 ↘ ALLOCATED ←───┴─ (cross-dock fulfill / stock allocate)
                      → PARTIALLY_SHIPPED → SHIPPED → CLOSED
                 any open → CANCELLED (releases allocations)
```

**Purchase order**

```
DRAFT → SUBMITTED → IN_TRANSIT → PARTIALLY_RECEIVED → RECEIVED
```

**Allocation (selected)**

```
ACTIVE → (picked/consumed) | CROSS_DOCK_ROUTED | released on cancel
```

**Ledger movement types (common)**

`RECEIVE`, `ADJUST`, `TRANSFER`, `SHIP`, `ASSEMBLY`, compensating `ERROR_CORRECTION` / reverse entries.

---

## Appendix B — Where jOOQ vs JPA

| Prefer JPA | Prefer jOOQ |
|------------|-------------|
| CRUD entities, simple finds | Cross-dock open-demand ranking SQL |
| Standard allocate loops | Scan put-away path resolution |
| Transactional aggregate saves | Replenishment / analytics / genealogy |

---

## Appendix C — Frontend ↔ backend pairing (floor)

| UI | API |
|----|-----|
| `FulfillmentPage` scan | `POST /api/v1/fulfillment/scan` |
| Cross-dock overlay | Scan response fields `crossDock`, `stagingPath`, `crossDockInstruction` |
| Wave buttons | `/api/v1/picking/waves/*` |
| LPN Move / Build Pallet | `/api/v1/inventory/lpns/mint`, `/pack`, `/move` |
| Next interleaved task | `GET /api/v1/tasks/next-best-action` |
| Wayfinding mini-map | `GET /api/v1/picking/wayfinding` |
| Digital Twin map | `PATCH /locations/{id}/coordinates`, `GET /locations/heatmap` |
| Dashboard CQRS stats | `GET /api/v1/dashboard/stats` → `dashboard_kpi_snapshots` |
| Dashboard SSE | `GET /api/v1/dashboard/stream` (`text/event-stream`) |
| Smart sourcing card | `GET /api/v1/dashboard/mesh-sourcing-suggestions` |
| Mesh Network hub | `/api/v1/mesh/discover`, `/network`, `/catalog`, `/connections/**` |
| Pack label | `/api/v1/shipments` pack-label (+ optional `lpnBarcode`) |
| Skip & Flag | `/api/v1/fulfillment/exceptions/report` |
| Office resolve | `/api/v1/office/exceptions/*` |
| Offline conflicts | `/api/v1/offline-sync-conflicts` |

---

*Generated for senior onboarding. When behavior and this doc disagree, trust the code and update this file.*
