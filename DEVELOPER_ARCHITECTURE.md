# InventorySystem — Developer & Architect Guide

> **Audience:** Senior developers and architects joining the codebase.  
> **Goal:** Understand structure, class roles, and end-to-end interaction flows quickly.  
> **Diagrams:** ASCII only (no Mermaid).  
> **Companion docs:** `DATABASE_GUIDE.md` (schema story), `README.md` (quick start), `PRODUCT.md` / `BUILD_PLAN.md` (product intent).

---

## Table of contents

1. [What this system is](#1-what-this-system-is)
2. [Repository map](#2-repository-map)
3. [Runtime topology](#3-runtime-topology)
4. [Layered architecture](#4-layered-architecture)
5. [Tenancy, security, and request path](#5-tenancy-security-and-request-path)
6. [Domain model (entities)](#6-domain-model-entities)
7. [Backend packages and classes](#7-backend-packages-and-classes)
8. [Frontend structure](#8-frontend-structure)
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
| UI | React 19.2, Vite 7, TanStack Query, Zustand, Tailwind |
| Edge | Nginx API gateway + frontend nginx |
| Infra | Redis (PIN lockout / rate limits), MinIO/S3 media |
| Auth | RS256 JWT in HttpOnly cookies (`invsys_access` / `invsys_refresh`) |

**Two golden rules** (also in `DATABASE_GUIDE.md`):

1. **Apartment building** — every row is `tenant_id`-scoped; Postgres RLS enforces isolation via `app.current_tenant`.
2. **Bank statement** — inventory never “updates a qty in place” as truth; movements append to `inventory_ledger`; levels are maintained by triggers / service logic.

---

## 2. Repository map

```
InventorySystem/
├── backend/                 Spring Boot API + Flyway migrations
│   └── src/main/java/com/invsys/
│       ├── api/             REST controllers + HTTP filters
│       ├── auth/            JWT, cookies, SSO, terminal PIN
│       ├── billing/         Stripe / capital gateways
│       ├── common/          ApiException, filters, helpers
│       ├── config/          Beans, Redis, OpenAPI, rate limit
│       ├── domain/          JPA entities
│       ├── gateway/         CORS whitelist filter
│       ├── integration/     Outbox, Shopify, EasyPost, accounting, alerts
│       ├── media/           S3/MinIO upload + attachments
│       ├── mesh/            Cross-tenant partner bridge
│       ├── repository/      Spring Data JPA (+ a few jOOQ helpers)
│       ├── service/         Business logic
│       └── tenancy/         TenantContext, datasource binding
├── frontend/                React SPA + Playwright e2e
│   └── src/
│       ├── api/             Axios client + DTOs
│       ├── components/      Shell + design system
│       ├── features/        Domain UI modules
│       ├── hooks/           Scanner, density, warehouse gate
│       ├── offline/         IDB mutation queue + query persist
│       ├── pages/           Route screens
│       ├── stores/          Zustand
│       └── styles/          Design tokens
├── ops/
│   ├── api-gateway/nginx.conf
│   ├── jwt/                 Dev RS256 PEMs
│   ├── postgres/init/       app_owner / app_user roles
│   └── demo_seed.sql        Demo Corp + Acme skeleton data
├── docker-compose.yml
├── deploy.bat               Windows deploy / seed / status
├── DATABASE_GUIDE.md        Schema narrative
└── DEVELOPER_ARCHITECTURE.md  (this file)
```

---

## 3. Runtime topology

### 3.1 Docker traffic (production-like local)

```
                         ┌─────────────────────────────────────┐
                         │            Browser                  │
                         └──────────────┬──────────────────────┘
                                        │
              ┌─────────────────────────┼─────────────────────────┐
              │ :3000                   │                         │ :8080 (optional direct)
              ▼                         │                         ▼
     ┌─────────────────┐                │              ┌──────────────────┐
     │  invsys-web     │                │              │ invsys-api-      │
     │  (nginx SPA)    │                │              │ gateway (nginx)  │
     │                 │── /api/* ──────┼─────────────►│                  │
     └─────────────────┘                │              └────────┬─────────┘
                                        │                       │ proxy_pass
                                        │                       ▼
                                        │              ┌──────────────────┐
                                        │              │  invsys-api      │
                                        │              │  Spring Boot     │
                                        │              │  (NOT published  │
                                        │              │   on host)       │
                                        │              └───┬───┬───┬──────┘
                                        │                  │   │   │
                         ┌──────────────┘                  │   │   │
                         ▼                                 ▼   ▼   ▼
                  Vite dev (:5173)                   ┌─────┐ ┌───┐ ┌─────┐
                  proxies /api → :8080               │ db  │ │rds│ │minio│
                                                     │5432 │ │637│ │9000 │
                                                     └─────┘ └───┘ └─────┘
```

| Container | Role |
|-----------|------|
| `invsys-web` | Built SPA + proxies `/api` → gateway |
| `invsys-api-gateway` | Sole public edge to Spring; forwards auth cookies, `X-Warehouse-Id`, `Idempotency-Key` |
| `invsys-api` | Business API; JDBC as `app_user`; Flyway as `app_owner` |
| `invsys-db` | Postgres 16 + volumes |
| `invsys-redis` | PIN lockout / distributed rate limits |
| `invsys-minio` | Media bucket `invsys-media` |

### 3.2 Dev vs Docker API path

```
Docker UI:   Browser → :3000 nginx → api-gateway:8080 → backend:8080
Vite UI:     Browser → :5173 Vite  → localhost:8080 (gateway) → backend
```

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
| Floor UX | `frontend/src/pages/FulfillmentPage.tsx` + `features/fulfillment/*` |
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

Frontend mirrors this in `useSessionStore.hasRole` and `ProtectedRoute` / Sidebar filters.

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
| `TenantMeshPartner` | Cross-tenant trading mesh link |
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
| `PickingWave` / `PickingBatch` / `PickingTask` | Wave picking |
| `Shipment` / `ShipmentLine` | Ship / pack-label |
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
| `InvSysApplication` | Spring Boot main |

### 7.2 `api` — HTTP surface

Controllers are thin: validate input, authorize, call services, return DTOs/entities.

| Controller | Base concern |
|------------|--------------|
| `AuthController` | Login, refresh, logout, magic, terminal switch, me |
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
| `AuthService` | Credential login, refresh rotation, warehouse PIN, terminal switch |
| `AuthCookieService` | HttpOnly cookie write/clear |
| `JwtService` | RS256 issue/validate |
| `JwtAuthFilter` | Request → SecurityContext + TenantContext |
| `SecurityConfig` | Filter chain, permitAll public routes |
| `JwksController` | `/.well-known/jwks.json` |
| `MagicLoginService` | Magic-link tokens |
| `TenantSsoResolver` / `oidc.*` / `saml.*` | Enterprise SSO |
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
| `InventoryService` | Receive / adjust / transfer / ship / quarantine / reverse; lot+serial; outbox stock events |
| `PurchaseOrderService` | Submit; receive lines; **cross-dock intercept**; landed-cost multi-receive |
| `SalesOrderService` | Confirm; allocate → `ALLOCATED` or **`BACKORDERED`**; cancel + audit |
| `AllocationService` | Reserve stock; kit explode; claim/consume/release for pick |
| `CrossDockService` | Match inbound to open SO demand; staging location; fulfill backorders |
| `PickingWaveService` / `PickingService` | Wave generate/optimize/release/claim; path optimization |
| `ShipmentService` | Create shipment; consume allocations; EasyPost labels |
| `ReturnService` | RMA lifecycle + quarantine |
| `ManufacturingService` / `ManufacturingLaborService` | BOM, production allocate/assemble, labor |
| `KitService` / `SoftKitExplosionService` | Hard kits vs soft kits |
| `ScanService` / `Gs1BarcodeParser` | Barcode resolution + put-away path |
| `FulfillmentExceptionService` | Damaged/skip shunt |
| `LandedCostService` + `service.landedcost.*` | Freight/customs allocation strategies |
| `CostingService` | Moving average / cost bumps |
| `IdempotencyService` | Store/replay keyed responses |
| `AuditService` | Append audit log |
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
| Vault | `CredentialVaultService`, rate limiter, settings |

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
```

### 8.2 Routing mental model

```
Public:     /login  /signup  /invite/:token  /supplier-portal/po/:token
B2B only:   /showroom/*  → ShowroomLayout
Office:     /*           → ProtectedRoute(officeOnly) → AppShell
Floor home: PICKER-only users land on /fulfillment
```

Key office routes: `/dashboard`, `/products`, `/fulfillment`, `/purchase-orders`, `/sales-orders`, `/manufacturing/*`, `/returns`, `/settings`, `/settings/fintech` (OWNER).

### 8.3 State split (important)

```
┌────────────────────┐     ┌─────────────────────────────┐
│ Zustand            │     │ TanStack Query              │
│ - session (user)   │     │ - server lists / details    │
│ - warehouse pick   │     │ - persisted to IndexedDB    │
│ - scan buffer      │     │ - invalidate after mutate   │
│ - offline quarantine│    │ - offlineFirst networkMode  │
│ - UI density/grid  │     └─────────────────────────────┘
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
| `usePreferencesStore` / `useGridColumnStore` / `useRailStore` | UX chrome |
| `useVariantCacheStore` | SKU → lot-tracking for GS1 UX |

### 8.4 Pages (route → purpose)

| Page | Purpose |
|------|---------|
| `LoginPage` / `SignupPage` / `InvitePage` | Auth & onboarding |
| `DashboardPage` | KPIs, work queue, ledger, conflicts |
| `ProductsPage` | Virtualized catalog |
| `FulfillmentPage` | Surface B scanner hub |
| `PurchaseOrdersPage` / `SalesOrdersPage` / `InvoicesPage` | Office order ops |
| `CustomersPage` / `SuppliersPage` | Master data |
| `ExceptionsPage` | Resolve Skip & Flag |
| `ManufacturingBomsPage` / `ManufacturingOrdersPage` / `ProductionTerminalPage` | Manufacturing |
| `ReturnsPage` / `ReturnsReceivePage` | RMA office + floor |
| `IssueSuppliesPage` / `TechnicianTruckPage` | Internal + field |
| `LotTracePage` | Genealogy |
| `ImportPage` | CSV / legacy ingest |
| `ReportsPage` | Analytics charts |
| `SettingsPage` / `BillingSettingsPage` / `FintechSettingsPage` | Admin |
| `showroom/*` | B2B portal |
| `SupplierPortalPage` | Public supplier PO |

### 8.5 Features modules

| Feature | Purpose |
|---------|---------|
| `fulfillment/ScannerView` | Scan history, GS1 fields, Skip & Flag |
| `fulfillment/CrossDockOverlay` | Bypass put-away → staging instruction |
| `fulfillment/QuarantineReview` / `ReplenishmentQueue` | Floor side panels |
| `inventory/LedgerHistoryTable` | Dashboard reverse UX |
| `offline/SyncConflictsPanel` | Office conflict resolution |
| `ingestion/ImportWizard` | Import UX |
| `compliance/LotTraceView` | Genealogy UI |
| `settings/*` | Integrations, carriers, accounting, warehouse map |

### 8.6 Design system (`components/ui`)

Buttons (`Button`, `BigButton`), forms (`Input`, `Select`), `Card`, `Modal`/`AlertDialog`, enterprise `Table` + `VirtualizedTable`, drawers, density toolbar, `ScanFlashOverlay`, `UndoToast`, media capture components. Theming via `data-theme=office|warehouse` and `data-density`.

---

## 9. Sequential business flows

### 9.1 Login → session → shell

```
[LoginPage]
    │ POST /api/v1/auth/login {email,password}
    ▼
[AuthController] → [AuthService.login]
    │ verify password, load roles + warehouseIds
    │ JwtService.createAccessToken
    │ persist RefreshToken hash
    │ AuthCookieService.writeSessionCookies
    ▼
[Browser] cookies set; Zustand setSessionFromLogin
    │ navigate by role
    ▼
[AppShell]
    │ GET /api/v1/auth/me → applyMeProfile
    │ load warehouses; WarehouseContextGate (SSID/geo)
    ▼
[Sidebar] filtered by hasRole / hideForPicker
```

### 9.2 Purchase order → receive (standard)

```
Manager (Surface A)
  POST /purchase-orders          → DRAFT + lines
  POST /purchase-orders/{id}/submit → SUBMITTED (+ outbox)
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
 inventory_ledger RECEIVE  +  inventory_levels.on_hand ↑
```

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
| Audit | `AuditService` | SO confirm/allocate/cancel; ops console |
| Outbox | `OutboxService` + dispatcher | At-least-once integration side effects |
| Append-only ledger | `InventoryLedger` grants | Reverse via compensating entry, not UPDATE |
| Virtual threads | Spring config | High concurrency I/O |
| ProblemDetail errors | `GlobalExceptionHandler` | Stable `code` field for clients |

---

## 11. Database, Flyway, and seed

### 11.1 Migration convention

Files: `backend/src/main/resources/db/migration/Vnnn__description.sql`  
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

### 11.2 Seed

```
deploy.bat seed
  → docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
```

Demo login (slugless): `owner@demo.test` / `password123` (tenant inferred from email).  
Extra tenants: `ops/demo_seed_tenants_extra.sql` (manual; not in `deploy.bat seed`).

**Do not** manually insert `inventory_levels` in seed — levels are trigger-maintained from ledger.

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

Credentials: `CredentialVaultService` (encrypted at rest).  
Webhooks: public controllers + signature validators (Stripe, EasyPost, Shopify).

---

## 13. E2E journey matrix

Playwright specs under `frontend/e2e/journeys/`. Helpers: `helpers.ts` (`contextForRole`, `hidScan`, `apiJson`).

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

Backend integration tests: `AbstractIntegrationTest` (Testcontainers Postgres + MinIO), clustered under `backend/src/test/java/com/invsys`.

---

## 14. Local ops cheat sheet

```
deploy.bat deploy          Build & start full stack
deploy.bat seed            Load demo_seed.sql
deploy.bat status          Compose ps
deploy.bat down            Stop (keeps pg volume)
deploy.bat help            Usage

Frontend: http://localhost:3000
API edge: http://localhost:8080
Swagger:  http://localhost:8080/swagger-ui.html
MinIO:    http://localhost:9001
```

JWT PEMs: `ops/jwt/dev-*.pem` (generated by deploy if missing).  
Env template: `.env.example`.

---

## 15. Onboarding checklist for new seniors

**Day 0 — run it**

1. `deploy.bat deploy` then `deploy.bat seed`  
2. Login `owner@demo.test` / `password123`  
3. Open Swagger; hit `GET /api/v1/auth/me`  
4. Skim `DATABASE_GUIDE.md` § golden rules  

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
| Pack label | `/api/v1/shipments` pack-label |
| Skip & Flag | `/api/v1/fulfillment/exceptions/report` |
| Office resolve | `/api/v1/office/exceptions/*` |
| Offline conflicts | `/api/v1/offline-sync-conflicts` |

---

*Generated for senior onboarding. When behavior and this doc disagree, trust the code and update this file.*
