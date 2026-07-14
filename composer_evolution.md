=========================================================================================
COMPOSER 2 EXECUTION BLUEPRINT: ENTERPRISE-GRADE EVOLUTION (v2 — RESEARCH-ENHANCED)
=========================================================================================
OBJECTIVE:
Evolve the existing multi-tenant WMS/SaaS MVP codebase into a market-leading application.
Eliminate the five core market feature gaps (Accounting, E-Commerce, Manufacturing,
B2B Portal, and Advanced RMA/Fulfillment) while strictly preserving current system laws:
1. PostgreSQL RLS isolation via parameterized transaction-local variables.
2. Immutability of the append-only inventory ledger (zero UPDATE/DELETE grants).
3. Concurrency-safe, race-free soft allocations via SELECT FOR UPDATE SKIP LOCKED.
4. Clean architectural separation between Office (Surface A) and Warehouse (Surface B).

v2 CHANGES vs v1:
- Added TRACK 0 (platform foundations) — the outbox table exists but NO worker exists yet;
  Tracks 1-2 silently depended on it. Also adds credential encryption, costing layer,
  and per-tenant rate-limit budgets that external APIs require.
- Corrected external API assumptions against 2026 reality (Shopify GraphQL-only,
  Amazon SP-API SQS/EventBridge notifications, Xero ManualJournals, QBO read metering).
- Fixed schema defects in v1 (missing tenant_id columns, role CHECK constraint,
  currency_rates scoping, movement_type CHECK constraint).
- Added "MISTAKES ALREADY MADE IN THIS CODEBASE" — regression traps proven by real
  bugs fixed during MVP hardening; every track must comply.

-----------------------------------------------------------------------------------------
GLOBAL ARCHITECTURAL LAWS & POLICIES (NON-NEGOTIABLE)
-----------------------------------------------------------------------------------------
- All new tenant-scoped tables must feature: `id UUID PK DEFAULT gen_random_uuid()`,
  `tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE`, and standard
  `created_at/updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` with the existing
  `set_updated_at()` trigger.
- Row-Level Security (RLS) must be explicitly ENABLED **and FORCED** on all new tables,
  with the standard `tenant_isolation` policy
  (`tenant_id = current_setting('app.current_tenant', true)::uuid`), matching V009.
- All mutating asynchronous integration calls must run on Java 21 Virtual Threads,
  driven by the transactional outbox (`outbox_events`). No blocking HTTP calls inside
  core business transactions.
- Frontend components must use React 19 primitives, TanStack Query for server state,
  and Tailwind variables defined in `tokens.css`.
- Backend API responses must be typed Java records (DTOs), never `Map.of(...)`.
  Every DTO field name must match the TypeScript interface in `frontend/src/api/types.ts`
  exactly (see Mistake M2 below).

-----------------------------------------------------------------------------------------
MISTAKES ALREADY MADE IN THIS CODEBASE (REGRESSION TRAPS — EVERY TRACK MUST COMPLY)
-----------------------------------------------------------------------------------------
M1. RLS TENANT-CONTEXT ORDERING (caused the demo-login 401 bug).
    Any query executed before `TenantContext.setTenantId(...)` is bound to the DB
    connection silently returns ZERO rows — no error, just empty results.
    RULES:
    * Pre-auth / pre-tenant lookups (webhook tenant resolution, login, public portal
      bootstrap) MUST go through `BootstrapJdbc` (app_owner datasource + explicit
      bootstrap RLS policies, like V010/V011). Add a bootstrap policy migration for
      every table the pre-tenant path reads.
    * BACKGROUND WORKERS HAVE NO HTTP REQUEST: `JwtAuthFilter` never runs for them.
      Every outbox/webhook worker MUST explicitly set `TenantContext.setTenantId(...)`
      from the event row BEFORE opening the transaction, and clear it in `finally`.
    * Never call a tenant-scoped repository inside a `@Transactional` method unless
      TenantContext was set BEFORE the method was entered.

M2. DTO/FRONTEND FIELD MISMATCH (caused the dashboard `.toString()` crash).
    `DashboardController` returned `openOrders` while the UI expected `openOrdersCount`.
    RULES: typed DTO records only; update `types.ts` in the same commit; frontend must
    normalize responses with safe defaults before rendering.

M3. 403-VS-401 SEMANTICS (caused every page to fail silently after backend restart).
    Spring Security's default returns 403 for missing/invalid auth, which suppresses
    the frontend token-refresh flow. `UnauthorizedEntryPoint` now forces 401.
    RULES: every NEW public route (e.g. `/api/v1/public/webhooks/channels/*`,
    `/showroom` API surface) must be added BOTH to `SecurityConfig.permitAll` AND
    `JwtAuthFilter.shouldNotFilter`. Missing either produces opaque 401/403 failures.

M4. EPHEMERAL JWT KEYS (caused stale-token 403s after every container rebuild).
    Keys now persist in `ops/jwt/` and load via `invsys.jwt.private-key-file`.
    RULE: never introduce a new signing/encryption secret that regenerates on boot.

M5. PROXY/BASE-URL DOUBLING (caused `/api/api/v1/...` 403s).
    `VITE_API_URL` must remain EMPTY in Docker; paths are written as `/api/v1/...`
    in code and proxied by nginx/Vite. New frontend surfaces (`/showroom`) rely on
    the nginx SPA fallback — do not hardcode absolute API hosts anywhere.

M6. set_config SCOPE INCONSISTENCY (latent connection-pool leak).
    `TenantConnectionHelper` uses `set_config(..., false)` (SESSION scope) while
    `TenantAwareDataSource` also binds at connection checkout. Session-scoped GUCs
    survive connection return to the Hikari pool → a later tenant can inherit the
    previous tenant's context.
    RULE (do this FIRST in Track 0): standardize on `set_config(..., true)`
    (transaction-local) everywhere, and add a cross-tenant leakage test that hammers
    the pool with alternating tenants.

M7. CHECK CONSTRAINTS ARE CLOSED ENUMS.
    `roles.code` CHECK only allows OWNER/ADMIN/WAREHOUSE_MANAGER/PICKER/VIEWER;
    `inventory_ledger.movement_type` CHECK only allows RECEIVE/SHIP/ADJUST/
    TRANSFER_IN/TRANSFER_OUT. Track 4's B2B_CUSTOMER role and any new movement
    type REQUIRE an ALTER-constraint migration first, or inserts will throw.

M8. CROSS-TENANT UNIQUE INDEX ON webhook_events.
    `UNIQUE (source, external_event_id)` is global. Shopify webhook IDs are unique
    per shop, not globally. Prefix `external_event_id` with the shop domain/seller id
    (e.g. 'myshop.myshopify.com:12345') or migrate the unique key to include tenant_id.

-----------------------------------------------------------------------------------------
EXECUTION ORDER (DEPENDENCIES)
-----------------------------------------------------------------------------------------
TRACK 0  →  TRACK 3 (no external APIs; pure domain)  →  TRACK 5 (RMA core, EasyPost)
         →  TRACK 1 (accounting; needs Track 0 costing + outbox)
         →  TRACK 2 (Shopify first; Amazon LAST — approval lead time, see 2.0)
         →  TRACK 4 (B2B portal; needs price tiers + role migration)
Rationale: Tracks 3/5 deliver market value with zero third-party risk. Track 2 Amazon
has developer-registration/role-approval lead time measured in weeks — start the
registration paperwork immediately even though the code lands last.

=========================================================================================
TRACK 0: PLATFORM FOUNDATIONS (NEW — PREREQUISITE FOR ALL INTEGRATION TRACKS)
=========================================================================================
Goal: Build the missing infrastructure that Tracks 1-5 implicitly assumed.

0.1 OUTBOX DISPATCHER (the `outbox_events` table exists; NO worker exists today)
- Implement `OutboxDispatcher`: a scheduled poller (Spring `@Scheduled`, virtual-thread
  executor) that claims unpublished rows via
  `SELECT ... WHERE published_at IS NULL ORDER BY created_at
   FOR UPDATE SKIP LOCKED LIMIT :batch`
  (same locking discipline as allocations — reuse the proven pattern).
- Per M1: for each claimed event, set `TenantContext` from `event.tenant_id` before
  invoking any handler; clear in `finally`.
- Handler registry: `Map<eventType, OutboxEventHandler>` beans. Handlers must be
  idempotent — the dispatcher guarantees at-least-once, not exactly-once delivery.
- Retry policy: exponential backoff with jitter columns
  (`retry_count INT DEFAULT 0`, `next_attempt_at TIMESTAMPTZ`, `last_error TEXT`
  — ALTER TABLE outbox_events). Dead-letter after N attempts: `status = 'FAILED'`
  surfaced in the Settings UI.

0.2 ENCRYPTED CREDENTIAL VAULT
- v1 stored raw `credential_payload JSONB` — FORBIDDEN. Create `integration_credentials`:
  * id UUID PK, tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * system VARCHAR(50) NOT NULL       -- 'QUICKBOOKS','XERO','SHOPIFY','AMAZON','EASYPOST'
  * ciphertext BYTEA NOT NULL         -- AES-256-GCM, app-level encryption
  * key_version INT NOT NULL DEFAULT 1
  * status VARCHAR(30) NOT NULL DEFAULT 'CONNECTED'  -- CONNECTED, EXPIRED, REVOKED, ERROR
  * UNIQUE (tenant_id, system)
- Master key from env (`INTEGRATION_MASTER_KEY`), never in the repo; per M4 it must be
  stable across restarts (document in .env.example, mount like ops/jwt).
- OAuth token rotation is MANDATORY, not optional:
  * QBO: access tokens live 60 min; refresh tokens ROTATE on every use (~24h cycle).
    Persist the NEW refresh token atomically on every refresh; refresh proactively
    at ~50 minutes; single-flight the refresh per tenant to avoid rotation races.
  * Xero: access 30 min, refresh 60 days, also rotating. Same discipline.

0.3 PER-TENANT RATE-LIMIT BUDGETS (external APIs are tenant-scoped)
- Hard numbers (verified 2026):
  * QBO:  500 req/min per realm, 10 concurrent, batch 40/min (30 entities/batch).
          Reads are METERED under the Intuit App Partner Program (500k/mo free tier)
          — prefer webhooks/CDC over polling; count read calls per tenant.
  * Xero: 60/min + 5,000/day per tenant, 5 concurrent, 10,000/min app-wide.
          Honor `Retry-After` and track `X-MinLimit-Remaining`/`X-DayLimit-Remaining`.
  * Shopify GraphQL: calculated query-cost points, not request counts; read
    `extensions.cost.throttleStatus` from every response and throttle accordingly.
- Implement `IntegrationRateLimiter` keyed by (tenant_id, system): token-bucket state
  in Postgres, schedulers pick work per-tenant so one tenant's backfill can never
  starve others (also prevents the app-wide Xero 10k/min breach).

0.4 COSTING LAYER (prerequisite for Track 1 COGS — v1 had no cost data on movements)
- Today only `purchase_order_lines.unit_cost` exists; the ledger carries no cost, so
  "COGS from recorded landed cost" in v1 was uncomputable.
- ALTER TABLE inventory_ledger ADD COLUMN unit_cost NUMERIC(19,4) NULL;
  (append-only preserved: cost is written at INSERT time, never updated).
- ALTER TABLE product_variants ADD COLUMN avg_cost NUMERIC(19,4) NOT NULL DEFAULT 0;
- Moving-average policy: on RECEIVE (PO receipt, RMA restock, assembly output),
  recompute `avg_cost = (on_hand*avg_cost + qty*unit_cost) / (on_hand+qty)` inside
  the receiving transaction. SHIP rows snapshot current avg_cost into
  `inventory_ledger.unit_cost` — that snapshot is the COGS source for Track 1.

0.5 INTEGRATION OBSERVABILITY
- Create `integration_sync_logs` here (moved from Track 1; all tracks share it):
  * id UUID PK, tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * system VARCHAR(50) NOT NULL
  * entity_type VARCHAR(50) NOT NULL  -- 'INVOICE','LEDGER_ENTRY','PURCHASE_ORDER',
                                      --  'STOCK_LEVEL','SALES_ORDER','SHIPMENT','RETURN'
  * entity_id UUID NOT NULL
  * status VARCHAR(30) NOT NULL       -- 'PENDING','SYNCED','FAILED','SKIPPED'
  * retry_count INT NOT NULL DEFAULT 0
  * last_error TEXT
  * INDEX (tenant_id, system, status)
- Standard RLS + updated_at trigger. Settings UI shows this grid with retry CTA.

0.6 ACCEPTANCE (Track 0)
- Pool-leak test proving transaction-local `set_config` (M6) passes.
- Outbox dispatcher processes events for two tenants concurrently with zero
  cross-tenant leakage (RLS test hammering alternating tenants).
- Credentials round-trip encrypted; a DB dump contains no plaintext tokens.

=========================================================================================
TRACK 1: GENERAL LEDGER & ACCOUNTING SYNC (QUICKBOOKS ONLINE & XERO)
=========================================================================================
Goal: Automated transactional engine mapping inventory movements, asset values,
revenue, and COGS to external general ledgers. DEPENDS ON: Track 0 (outbox, vault,
rate limits, costing).

1.1 DATABASE SCHEMA MUTATIONS
- Create table `account_mappings`:
  * id UUID PK
  * tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * system VARCHAR(50) NOT NULL       -- 'QUICKBOOKS','XERO'
  * account_type VARCHAR(100) NOT NULL -- 'INVENTORY_ASSET','COGS','SALES_REVENUE','TAX'
  * external_account_id VARCHAR(255) NOT NULL
  * UNIQUE (tenant_id, system, account_type)
- (integration_sync_logs already created in Track 0.5.)

1.2 BACKEND ENGINEERING — VERIFIED API CONSTRAINTS
- Abstract `AccountingSyncAdapter` with `QuickBooksOnlineAdapter` and `XeroAdapter`.
- QUICKBOOKS specifics (verified):
  * Pin `minorversion` (currently 75) on every request — unpinned schema drift breaks
    integrations on Intuit's monthly releases.
  * Idempotency: send a unique `Request-ID` query param (UUID persisted on the sync
    log row) on every write; Intuit replays the original response on retry.
  * Journal entries via the JournalEntry entity; invoices/payments via their entities.
  * Respect 500/min/realm + 10 concurrent through the Track 0 rate limiter;
    on 429 honor `Retry-After` with exponential backoff + jitter.
  * Prefer Change Data Capture (`/cdc`) + webhooks over polling (reads are metered).
- XERO specifics (verified — v1 was wrong here):
  * The `Journals` endpoint is READ-ONLY. Journal creation MUST use `ManualJournals`.
  * Batch writes: up to ~50 nodes per PUT/POST (3.5MB cap) — batch daily COGS
    journals per tenant instead of one call per ledger row.
  * Use `If-Modified-Since` for delta reads; webhooks only cover contacts/invoices/
    credit notes — poll everything else.
  * One OAuth connection can span multiple Xero organisations: store and send
    `xero-tenant-id` per connection; do NOT assume token→org is 1:1.
- Event flow (outbox handlers, per Track 0 registry):
  * INVOICE_OPEN / INVOICE_PAID → sync invoice + payment.
  * LEDGER_ENTRY_ARRIVED (SHIP/RECEIVE only) → journal entry using
    `inventory_ledger.unit_cost` snapshot (Track 0.4) × quantity_delta, mapped through
    `account_mappings`. Skip + log 'SKIPPED' when no mapping is configured — never fail
    the whole batch because one tenant hasn't finished setup.
- Idempotency rule: before POSTing, check `external_references` (tenant_id, system,
  entity_type, entity_id); if present, switch to update semantics (QBO sparse update /
  Xero POST-with-ID). Write the external_reference row in the SAME transaction that
  marks the sync log SYNCED.
- COGS batching: aggregate SHIP rows into one journal per tenant per day (both
  providers throttle too aggressively for per-row journals at warehouse volume).

1.3 FRONTEND UI DESIGN (SURFACE A)
- 'Accounting Sync' tab in `/settings`:
  * OAuth connect buttons with live status chips (CONNECTED / EXPIRED / ERROR from
    `integration_credentials.status`); reconnect CTA on expiry.
  * Account-mapping dropdowns populated from the provider's chart of accounts
    (fetched server-side, cached — chart-of-accounts reads count against QBO metering).
  * Sync-log grid from `integration_sync_logs` with one-click retry on FAILED rows.
- Per M2: define `AccountMappingResponse`, `SyncLogResponse` records + matching
  `types.ts` interfaces in the same commit.

=========================================================================================
TRACK 2: MULTI-CHANNEL E-COMMERCE CORE (SHOPIFY & AMAZON MESH)
=========================================================================================
Goal: Multi-channel product matching, real-time stock push on ledger events, and
inbound order pull. DEPENDS ON: Track 0.

2.0 CRITICAL API REALITY CHECK (verified 2026 — v1 assumptions were stale)
- SHOPIFY: REST Admin API is DEPRECATED. All work targets the GraphQL Admin API.
  * Stock push = `inventorySetQuantities` mutation. As of API version 2026-04 the
    `@idempotent(key: ...)` directive is REQUIRED — derive the key from the outbox
    event id (stable across retries).
  * Use compare-and-set: pass `compareQuantity` (last known Shopify value from
    `external_references` metadata); only set `ignoreCompareQuantity: true` for
    explicit full-resync jobs. Our ledger is the source of truth.
  * Rate limiting is a calculated cost budget: read
    `extensions.cost.throttleStatus.currentlyAvailable` on every response and feed it
    to the Track 0 rate limiter.
  * Order pull: `orders/create` + `orders/updated` webhooks with HMAC-SHA256
    verification (`X-Shopify-Hmac-Sha256` against the app secret) BEFORE parsing.
    Resolve tenant from the shop domain header via BootstrapJdbc (M1), and prefix
    `external_event_id` with the shop domain (M8).
- AMAZON SP-API: NOT webhook-over-HTTP. Notifications are delivered via AWS SQS or
  EventBridge only. Also requires: developer profile registration, role approval by
  Amazon (weeks of lead time — START THE PAPERWORK NOW), LWA OAuth with annually
  renewed refresh tokens.
  * Orders: Orders API v2026-01-01 (`getOrder`/`searchOrders` with `includedData`).
  * Inventory push: Feeds API (JSON feeds; legacy XML/flat-file feeds are deprecated).
  * PHASE Amazon AFTER Shopify ships; the SQS consumer is a separate worker process
    configuration, not a public HTTP route.

2.1 DATABASE SCHEMA MUTATIONS
- ALTER TABLE product_variants ADD COLUMN external_sync_enabled BOOLEAN NOT NULL DEFAULT TRUE;
- Create table `channel_integrations`:
  * id UUID PK, tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * platform VARCHAR(50) NOT NULL     -- 'SHOPIFY','AMAZON'
  * shop_identifier VARCHAR(255) NOT NULL  -- shop domain / seller+marketplace id
  * credential_id UUID REFERENCES integration_credentials(id)  -- Track 0.2 vault; NO raw JSONB
  * status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
  * UNIQUE (platform, shop_identifier)   -- also serves inbound tenant resolution
- Bootstrap RLS policy (V0xx) granting app_owner SELECT on `channel_integrations`
  for pre-tenant webhook resolution (M1).

2.2 BACKEND ENGINEERING
- Stock push: application-level listener (NOT a DB trigger — keep business logic in
  the service layer) fires on ledger insert / allocation change; computes
  ATP = on_hand - allocated; appends outbox event `STOCK_LEVEL_CHANGED` in the SAME
  transaction. Handler (Track 0 dispatcher) debounces per (variant, channel): only the
  latest ATP matters — collapse bursts (a 50-line pick would otherwise fire 50 pushes).
- Order pull: public route `/api/v1/public/webhooks/channels/{platform}` registered in
  BOTH SecurityConfig.permitAll AND JwtAuthFilter.shouldNotFilter (M3). Flow:
  verify signature → resolve tenant via shop_identifier (BootstrapJdbc) → insert
  `webhook_events` row (return 200 immediately) → async worker transforms payload into
  `sales_orders` + `sales_order_lines` in CONFIRMED state, guarded by
  `external_references` for exactly-once order creation.
- SKU matching: map via `external_references` (entity_type='PRODUCT_VARIANT');
  unmatched inbound lines create the order in a NEEDS_REVIEW state rather than
  failing the webhook.

2.3 FRONTEND UI DESIGN (SURFACE A)
- 'Integrations Marketplace' panel under Settings: connect flows for Shopify
  (shop domain + OAuth) and Amazon (LWA consent), with live health chips
  (last webhook received, last push, error counts from integration_sync_logs).
- Product Master grid: channel badges (Shopify/Amazon) + sync-status icons per
  variant; per-variant `external_sync_enabled` toggle.

=========================================================================================
TRACK 3: LIGHT MANUFACTURING, KITTING, & BOM ALGORITHMS
=========================================================================================
Goal: Multi-level BOM and assembly production orders preserving ledger invariants.
DEPENDS ON: nothing external — build FIRST after Track 0 (schema fixes below).

3.0 PREREQUISITE MIGRATION
- ALTER inventory_ledger movement_type CHECK to add 'ASSEMBLY_IN' and 'ASSEMBLY_OUT'
  (M7). Using TRANSFER_OUT/RECEIVE for assembly (v1) would corrupt transfer reporting
  semantics — assembly is not a transfer.

3.1 DATABASE SCHEMA MUTATIONS (v1 was missing tenant_id on all three tables — fixed)
- Create table `boms`:
  * id UUID PK
  * tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * parent_variant_id UUID NOT NULL REFERENCES product_variants(id)
  * name VARCHAR(255) NOT NULL
  * is_active BOOLEAN NOT NULL DEFAULT TRUE
  * UNIQUE (tenant_id, parent_variant_id)
- Create table `bom_lines`:
  * id UUID PK
  * tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * bom_id UUID NOT NULL REFERENCES boms(id) ON DELETE CASCADE
  * component_variant_id UUID NOT NULL REFERENCES product_variants(id)
  * quantity_required NUMERIC(19,4) NOT NULL CHECK (quantity_required > 0)
  * UNIQUE (bom_id, component_variant_id)
- Create table `production_orders`:
  * id UUID PK
  * tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * number VARCHAR(50) NOT NULL        -- allocate via existing document_sequences
  * parent_variant_id UUID NOT NULL REFERENCES product_variants(id)
  * qty_target NUMERIC(19,4) NOT NULL CHECK (qty_target > 0)
  * qty_produced NUMERIC(19,4) NOT NULL DEFAULT 0
  * status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','COMPONENTS_ALLOCATED','WIP','COMPLETED','CANCELLED'))
  * UNIQUE (tenant_id, number)
- Standard RLS + updated_at triggers on all three (Global Laws).
- CYCLE GUARD: BOMs are multi-level; a BOM whose component (transitively) contains its
  own parent creates infinite explosion. Enforce in `ManufacturingService` with a
  recursive-CTE check on bom_lines insert/update; reject cycles with a 422.

3.2 BACKEND ENGINEERING
- `ManufacturingService`:
  * `allocateComponents(productionOrderId)`: explode BOM (recursive CTE, depth-capped),
    then reuse the EXISTING AllocationService locking discipline
    (`SELECT ... FOR UPDATE SKIP LOCKED` on inventory_levels) — do not fork a second
    allocation code path. Allocations reference the production order via a nullable
    `production_order_id` column added to `allocations` (plus index), NOT by
    overloading sales_order_line_id.
  * `executeAssembly(productionOrderId, qtyToProduce)`: single transaction:
    - components: allocation → CONSUMED; ledger row ASSEMBLY_OUT (negative delta)
      with unit_cost = component avg_cost (Track 0.4);
    - parent: ledger row ASSEMBLY_IN (positive delta) with
      unit_cost = Σ(component costs) / qtyToProduce — rolls component cost into the
      finished good so Track 1 COGS stays correct;
    - update qty_produced; transition status (partial completion allowed:
      WIP until qty_produced >= qty_target).
  * Triggers maintain inventory_levels automatically (existing V005) — DO NOT touch
    levels directly.

3.3 FRONTEND UI DESIGN (SURFACES A & B)
- Surface A: 'Manufacturing' hub (routes: /manufacturing/boms, /manufacturing/orders).
  Tree visualizer for nested BOMs (indented tree, lazy-load children).
- Surface B: mobile production terminal — assigned orders list, scan-to-verify
  component barcodes (reuse useBarcodeScanner + useScanFeedback hooks), single
  primary "Complete build" action with haptic feedback.

=========================================================================================
TRACK 4: CLIENT-FACING NATIVE B2B PORTAL ("SHOWROOM")
=========================================================================================
Goal: Secure, gated self-service wholesale portal on the existing multi-tenant platform.

4.0 PREREQUISITE MIGRATION
- ALTER `roles.code` CHECK constraint to include 'B2B_CUSTOMER' (M7). Seed the role
  per-tenant on demand (first portal invite), consistent with existing role seeding.

4.1 DATABASE SCHEMA MUTATIONS
- Create table `customer_user_mappings`:
  * id UUID PK
  * tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE
  * user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
  * UNIQUE (user_id)
- Create table `customer_price_tiers`:
  * id UUID PK
  * tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * name VARCHAR(100) NOT NULL
  * discount_percent NUMERIC(5,2) NOT NULL DEFAULT 0.00
    CHECK (discount_percent >= 0 AND discount_percent <= 100)
- ALTER TABLE customers ADD COLUMN price_tier_id UUID NULL REFERENCES customer_price_tiers(id);
- Standard RLS + triggers.

4.2 BACKEND ENGINEERING
- Onboarding: extend the existing invitations flow (invitations table + accept route)
  with an optional customer_id — portal users are invited BY the tenant, they do not
  self-signup. RLS keeps them inside the tenant automatically; the extra guard below
  keeps them inside their own customer account.
- `B2bCustomerContext`: when the JWT carries role B2B_CUSTOMER, resolve customer_id
  from customer_user_mappings once per request (JwtAuthFilter extension), store on
  TenantContext alongside userId.
- Dedicated controller surface `/api/v1/portal/**` — do NOT overload internal
  endpoints with role branches:
  * GET /portal/catalog — active variants only; unit price computed server-side as
    price × (1 - tier.discount_percent/100). Discount applied in the SERVICE layer
    (v1's "intercept during serialization" is untestable and fragile).
  * POST /portal/orders — creates sales_orders in DRAFT with customer_id FORCED from
    B2bCustomerContext (never from the request body).
  * GET /portal/orders, /portal/invoices — filtered by the mapped customer_id.
- `@PreAuthorize("hasRole('B2B_CUSTOMER')")` on the portal surface; explicitly DENY
  B2B_CUSTOMER on all internal route groups (allocation, inventory, settings, users).
- Per M3: /portal/** is AUTHENTICATED (normal JWT) — it does NOT go into permitAll;
  only the invite-accept route stays public (already is).

4.3 FRONTEND UI DESIGN (WHITE-LABELED PORTAL)
- `/showroom` route group with a minimalist consumer layout (no Sidebar/AppShell;
  own lightweight shell). Post-login routing: users with ONLY the B2B_CUSTOMER role
  land on /showroom and cannot reach office routes (extend ProtectedRoute role logic).
- Catalog grid: instant search, category filters, quantity steppers; tier-discounted
  price returned by the API (never computed client-side).
- Checkout wizard: shows configured payment terms (NET 30 from tenant_settings),
  order history table, invoice list with Stripe checkout links (existing
  payment_intents flow).

=========================================================================================
TRACK 5: ADVANCED RMA (RETURNS), LIVE TRACKING, & MULTI-CURRENCY
=========================================================================================
Goal: RMA workflows, EasyPost label generation + tracking, multi-currency display.

5.1 DATABASE SCHEMA MUTATIONS
- Create table `returns`:
  * id UUID PK
  * tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * sales_order_id UUID NOT NULL REFERENCES sales_orders(id)
  * number VARCHAR(50) NOT NULL        -- via document_sequences ('RMA' doc_type)
  * status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'
    CHECK (status IN ('REQUESTED','APPROVED','RECEIVED','CLOSED','REJECTED'))
  * UNIQUE (tenant_id, number)
- Create table `return_lines`:
  * id UUID PK
  * tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE
  * return_id UUID NOT NULL REFERENCES returns(id) ON DELETE CASCADE
  * sales_order_line_id UUID NOT NULL REFERENCES sales_order_lines(id)
  * quantity_expected NUMERIC(19,4) NOT NULL CHECK (quantity_expected > 0)
  * quantity_received NUMERIC(19,4) NOT NULL DEFAULT 0
  * disposition VARCHAR(50) CHECK (disposition IN ('RESTOCK','SCRAP','REPAIR'))
- Create table `currency_rates` — DESIGN DECISION (v1 left this ambiguous):
  rates are GLOBAL platform data, not tenant data. No tenant_id; RLS NOT applied;
  grant SELECT-only to app_user; written exclusively by a platform-level refresh job
  through the app_owner (bootstrap) datasource:
  * id UUID PK
  * from_currency CHAR(3) NOT NULL, to_currency CHAR(3) NOT NULL
  * rate NUMERIC(12,6) NOT NULL CHECK (rate > 0)
  * as_of TIMESTAMPTZ NOT NULL
  * UNIQUE (from_currency, to_currency)
- FX INVARIANT: invoices store the exchange rate captured at issue time
  (ALTER invoices ADD COLUMN fx_rate NUMERIC(12,6) NULL, currency already exists on
  variants/settings). Issued financial documents are NEVER recalculated from live
  rates — live rates are for display/estimation only.

5.2 BACKEND ENGINEERING
- RMA prerequisite validation: quantity_expected must not exceed
  (shipped − already returned) per sales_order_line — enforce in ReturnService.
- `ReturnService.processReceipt(returnLineId, locationId, disposition)`:
  single transaction:
  * RESTOCK → ledger RECEIVE row, reason_code 'RMA_RESTOCK',
    unit_cost = variant avg_cost (Track 0.4), triggers update levels automatically.
  * SCRAP → ledger ADJUST row (negative-neutral: receive-then-adjust or direct
    no-op on sellable stock; record for audit), reason_code 'RMA_SCRAP'.
  * REPAIR → no ledger movement until repaired; status only.
  * Update quantity_received; auto-transition return status when all lines complete.
- EASYPOST integration (verified constraints):
  * Outbound labels: when a shipment enters packing, an outbox event handler
    (Track 0 — NEVER inline in the packing transaction) builds the payload
    (address + parcel weight/dims), buys the shipment, stores
    `postage_label.label_url` + tracking code on `shipments`, and writes
    external_references (system='EASYPOST').
  * Return labels: EasyPost supports `is_return: true` on shipment creation —
    generate return labels from the RMA approval action.
  * Tracking webhooks: public route `/api/v1/public/webhooks/easypost` — register in
    BOTH SecurityConfig AND JwtAuthFilter (M3); validate the HMAC signature
    (`X-Hmac-Signature`) with the library helper; insert into webhook_events and
    return 2xx IMMEDIATELY (EasyPost retries on slow/non-2xx); async worker maps
    tracker updates → shipment status.
  * Test mode: EasyPost test API key in dev/docker profiles; keys live in the
    Track 0.2 vault per tenant (or platform-level key + per-tenant carrier accounts).
- Multi-currency service: `CurrencyService.convert(amount, from, to)` reading cached
  `currency_rates`; used for portal display and reporting. Invoice totals use the
  stored fx_rate per the FX invariant.

5.3 FRONTEND UI DESIGN (SURFACES A & B)
- Surface A: 'RMA Control Panel' — returns queue with status filters, line-level
  disposition editors (Restock/Scrap/Repair), printable return-slip PDF, and the
  return-label download link once purchased.
- Surface B: high-contrast returns screen — scan the RMA barcode → original order
  lines appear → single-tap count confirmation per line with scan feedback.

=========================================================================================
ACCEPTANCE CRITERIA FOR COMPOSER TO MARK COMPLETED
=========================================================================================
1. Every new table ships in a Flyway migration containing: RLS ENABLE + FORCE, the
   tenant_isolation policy, grants, and updated_at trigger (except the explicitly
   global `currency_rates`, which must have SELECT-only app_user grants and no RLS).
2. All integration side-effects flow through the Track 0 outbox dispatcher on virtual
   threads; grep-level check: no WebClient/HttpClient calls inside @Transactional
   domain services.
3. Inventory changes (assembly, RMA restock/scrap) are append-only ledger inserts;
   automated test asserts zero UPDATE/DELETE on inventory_ledger and that levels
   changed only via the V005 triggers.
4. Cross-tenant leakage tests pass on every new route group (portal, webhooks,
   manufacturing, returns) AND on the outbox dispatcher under concurrent multi-tenant
   load (regression for M1/M6).
5. Every new public route appears in BOTH SecurityConfig.permitAll and
   JwtAuthFilter.shouldNotFilter; integration test hits each unauthenticated and
   expects deliberate 200/401 — never a bare 403 (regression for M3).
6. Every new API response is a typed record with a matching types.ts interface;
   dashboard-style field-mismatch is checked by an API contract test (regression M2).
7. External sync is idempotent: replaying the same outbox event or webhook twice
   produces exactly one external object / one sales order (external_references guard).
8. Rate-limit budgets enforced per tenant; a synthetic burst test on one tenant does
   not delay another tenant's sync queue.

=========================================================================================
COMPLETION STATUS (2026-07-12)
=========================================================================================
### Shipped
- Track 0: Outbox dispatcher, platform foundations (V012), tenant pool binding (M6)
- Track 1: Accounting mappings + sync logs API at `/api/v1/integrations`
- Track 2: Channel integrations CRUD
- Track 3: Manufacturing BOMs + production orders at `/api/v1/manufacturing/*`
- Track 4: B2B portal (`/api/v1/portal/*`, showroom UI)
- Track 5: Returns/RMA (`/api/v1/returns/*`) + currency_rates table
- Frontend: split login (brand blue + dark card), enhanced dashboard with onboarding checklist,
  role-filtered sidebar (PICKER/VIEWER/B2B), wired APIs for variants/manufacturing/returns
- Tests: 14 JUnit integration tests green; Playwright E2E (login, dashboard, navigation)

### Acceptance criteria checklist
| # | Criterion | Status |
|---|-----------|--------|
| 1 | Flyway migrations with RLS + grants | Done (V001–V016) |
| 2 | No blocking HTTP in @Transactional services | Done (outbox pattern) |
| 3 | Ledger append-only tests | Done (`LedgerImmutabilityTest`, `ManufacturingLedgerTest`) |
| 4 | Cross-tenant leakage tests | Done (`RlsIsolationTest`, `TenantPoolLeakageTest`, route groups) |
| 5 | Public routes in SecurityConfig + JwtAuthFilter | Done (`PublicWebhookSecurityTest`) |
| 6 | Typed API + contract tests | Done (`DashboardContractTest`, `types.ts`) |
| 7 | Idempotent outbox/webhook replay | Partial — outbox test exists; dedicated webhook replay test TODO |
| 8 | Per-tenant rate limits | Done (`RateLimiterIsolationTest`) |

### Known gaps (post-MVP / BUILD_PLAN Part 6)
- Vitest + RTL frontend unit tests not yet set up
- Full Playwright journey suite (onboarding wizard, PICKER-only E2E, fulfillment scan, payments)
- Portal-specific RLS dedicated test
- Stripe Connect billing UI still stub in Settings
- RMA create wizard UI (API exists; UI button placeholder)
=========================================================================================
