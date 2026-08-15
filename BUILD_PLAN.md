# InventorySystem — Master Build Plan

**Product:** Multi-tenant Inventory / WMS / Supply Chain B2B SaaS with embedded payments.
**Status:** IMPLEMENTED — MVP + composer evolution tracks shipped. See completion notes at end of `composer_evolution.md`.
**Audience:** The Cursor agent executing the build, phase by phase, in order.

---

## Part 1 — Analysis of the Original Spec

### 1.1 What the spec gets right (keep as-is, non-negotiable)

- Shared-schema multi-tenancy enforced by **PostgreSQL RLS**, with `tenant_id` injected into the DB session per transaction (defense in depth beyond `WHERE` clauses).
- **Append-only inventory ledger** — inventory as a financial ledger, never `UPDATE`d quantities.
- Separate **allocations** (soft commitment) from ledger movements (hard fact).
- `SELECT ... FOR UPDATE SKIP LOCKED` for contention-free allocation.
- JSONB for tenant settings and variant attributes; JSONB/polymorphic isolation of external system IDs.
- Stripe Connect destination charges with an application-fee spread as the monetization engine.
- Java 21 virtual threads, jOOQ for reads / JPA for simple writes, Flyway, React 19 + TanStack stack, HID + WASM-worker camera scanning with haptic/audio feedback.

### 1.2 Gaps found — and the fixes adopted in this plan

**G1. No inbound supply chain (CRITICAL).** The spec has `sales_orders` and `invoices` but no way for stock to *enter* the system except raw adjustments. A WMS competing with Fishbowl/Katana is unusable without purchasing.
→ **Added:** `suppliers`, `purchase_orders`, `purchase_order_lines`, and a Receiving workflow that writes `RECEIVE` ledger entries against a PO line.

**G2. No auth issuance story (CRITICAL).** "Asymmetric JWTs" were specified with no issuer. Someone must mint the tokens.
→ **Added:** a self-hosted auth module: signup/tenant-onboarding, login, RS256 access tokens (short-lived) + rotating opaque refresh tokens (DB-backed, revocable), JWKS endpoint, key material via env/secret. No external IdP dependency for MVP; the token format stays standard so an IdP can replace it later.

**G3. Materialized view for stock levels is technically wrong.** PostgreSQL does **not** support RLS policies on materialized views (`CREATE POLICY` works only on tables), so a mat view would silently bypass tenant isolation, and `REFRESH` is a full-recompute with staleness.
→ **Fixed:** a regular `inventory_levels` summary table (RLS-enabled like everything else), maintained transactionally by AFTER-triggers on `inventory_ledger` and `allocations`. Always consistent, always tenant-isolated, indexed for O(1) reads.

**G4. `SET LOCAL` + connection pooling landmines.**
- `SET LOCAL` is a no-op outside a transaction — silently. Must be issued *inside* the transaction on the *same connection* JPA/jOOQ will use.
- String-concatenated `SET` is an injection vector → use parameterized `SELECT set_config('app.current_tenant', ?, true)`.
- RLS is **skipped for the table owner** unless `FORCE ROW LEVEL SECURITY` is set; and any role with `BYPASSRLS`/superuser skips it entirely.
→ **Fixed:** two DB roles (migration owner vs. runtime `app_user` with no ownership, no BYPASSRLS), `FORCE ROW LEVEL SECURITY` on every table, tenant context set via a `Connection` customizer bound to Spring transaction synchronization, and a fail-closed default (`current_setting('app.current_tenant', true)` returning NULL matches no rows).

**G5. Lot/batch/serial/expiry buried in variant JSONB.** Recalls, FEFO picking, and expiry alerts need lot/expiry as *queryable ledger dimensions*, not opaque JSON.
→ **Fixed:** first-class `lots` table; `inventory_ledger` and `allocations` carry nullable `lot_id`. Variant JSONB remains for display attributes (size/color).

**G6. Flat `locations`.** Warehouse work happens at bin level (put-away, picking paths, cycle counts).
→ **Fixed:** `locations` is a self-referencing hierarchy (`WAREHOUSE > ZONE > AISLE > BIN`); ledger rows reference the leaf location.

**G7. No order lifecycle / partial fulfillment.** Real B2B orders ship partially, get backordered, get cancelled after partial shipment.
→ **Added:** explicit state machines (documented in §3.4), quantity tracking per line (`ordered / allocated / shipped`), and `shipments` + `shipment_lines` tables — which also carry EasyPost label references, enabling the shipping-arbitrage revenue stream the spec mentions but never modeled.

**G8. Payments domain is half-modeled.** `payment_intents` alone can't reconcile money. Webhooks arrive out of order and are retried; fees must be tracked to measure the platform spread.
→ **Added:** `payments` (settled money, application fee amount, Stripe balance-txn ref), `webhook_events` inbox (idempotent, signature-verified, processed on virtual threads), and `stripe_accounts` per tenant (Connect onboarding state).

**G9. No idempotency or event infrastructure.** API-first + integrations demands it.
→ **Added:** `idempotency_keys` table honoring an `Idempotency-Key` header on all mutating endpoints; `outbox_events` (transactional outbox) so integrations/real-time updates never require dual writes; `external_references` polymorphic table (entity_type, entity_id, system, external_id) for Shopify/Stripe/EasyPost IDs.

**G10. Tenant-scoped document numbering.** Invoice/order numbers must be gapless-enough and per-tenant (`INV-2026-00042`), which global sequences can't do.
→ **Added:** `document_sequences` table incremented under `SELECT ... FOR UPDATE`.

**G11. Cycle counting / stock takes.** Ledger purism is meaningless if physical counts can't correct it. → **Added:** `cycle_counts` + lines producing `ADJUST` ledger entries with reason codes.

**G12. Offline reality was under-specified.** "Cache queries" isn't enough for spotty warehouse Wi-Fi.
→ **Fixed:** PWA service worker, persisted TanStack Query cache (IndexedDB), and an offline **mutation queue** for scan events (queued locally, replayed with idempotency keys on reconnect).

**G13. Camera-scanner library licensing.** ZXing-based html5-qrcode is unmaintained; the fastest WASM decoders (e.g. barcode-scanning SDKs) are commercial. → **Decision:** native `BarcodeDetector` API where available, fallback to `zxing-wasm` (MIT-compatible fork) inside a WebWorker. Abstracted behind one interface so the decoder is swappable.

**G14. Missing operational table stakes:** UoM on products, money as `NUMERIC(19,4)` + ISO currency (minor units at Stripe boundary only), `timestamptz` everywhere, soft-delete via `deleted_at` on catalog entities (never on ledger), audit log for non-ledger mutations, tenant_id in log MDC, Testcontainers-based tests including cross-tenant leak tests and allocation race tests.

**G15. No tenant administration surface (CRITICAL for a self-serve SaaS).** The `tenant_settings` table existed, but no API or UI let a business actually manage itself: no way to add users after signup, assign roles, or change its own settings.
→ **Added:** an `invitations` table and flow (admin invites by email with a role; tokenized accept link creates the user), user management API + UI (invite, change role, deactivate), and a full Settings area per tenant — company profile, default currency, barcode prefix rules, negative-inventory toggle, document numbering format, payment fee visibility. See Phase 3 (API) and Phase 9 (UI).

**G16. No end-to-end test layer.** Integration tests prove the backend; nothing proved the *product*.
→ **Added:** a Playwright E2E suite that runs against the full docker-compose stack, plus an explicit test pyramid (JUnit 5 + Testcontainers, Vitest + React Testing Library, Playwright). See Part 6.

**G17. No design system — "intuitive and attractive" left to chance.** Fishbowl's dated UI and Cin7's cluttered interface are the top usability complaints in the market (see Part 5); we must not repeat them by improvising screens.
→ **Added:** §2.6 defines two deliberate design surfaces (Office vs. Warehouse), shadcn/ui + Tailwind design tokens, and mandatory empty/loading/error state patterns.

**G18. Deliberately OUT of MVP scope** (schema must not preclude them, but do not build): manufacturing BOM/kitting, multi-currency conversion, returns/RMA workflow (table stub only), Shopify sync implementation (webhook inbox + external_references make it a bolt-on), demand forecasting, label purchase implementation (shipments table ready).

---

## Part 2 — System Architecture

### 2.1 Repository layout (monorepo)

```
InventorySystem/
├── BUILD_PLAN.md                 # Original phased MVP plan (this file)
├── docker-compose.yml            # db, both APIs, both UIs, gateway, LGTM
├── deploy.bat                    # Windows deploy / seed / status (both planes)
├── backend/                      # Maven aggregator — Java 25, Spring Boot 4.1
│   ├── pom.xml
│   ├── Dockerfile                # -pl invsys-app -am → WMS fat jar (:8080)
│   ├── Dockerfile.admin          # -pl invsys-admin-api -am → Admin fat jar (:8081)
│   ├── invsys-core/              # Domain, repos, services, Flyway (head V108)
│   ├── invsys-app/               # Data-plane runner (artifact invsys-api)
│   ├── invsys-admin-api/         # Control-plane runner
│   ├── invsys-chatbot/           # Optional Support Co-Pilot
│   └── invsys-training/          # Optional Flight Simulator
├── frontends/                    # pnpm workspace
│   ├── apps/frontend_wms/        # Tenant WMS — Vite + React 19 + Playwright
│   ├── apps/frontend_admin/      # Super Admin control plane
│   └── packages/                 # shared-types, shared-ui
└── ops/
    ├── api-gateway/nginx.conf    # :8080 blocks CP; :8081 is admin
    ├── jwt/                      # Dev RS256 PEMs (shared by both APIs)
    └── postgres/init/            # role bootstrap (app_owner / app_user)
```

Historical phases below still use the original Java 21 / Spring Boot 3 wording — that was the MVP target. The shipped stack is Java 25 / Spring Boot 4.1 with a **dual-plane** split (WMS vs Super Admin). See `DEVELOPER_ARCHITECTURE.md` for the living map.
### 2.2 Tenancy enforcement chain (every request)

1. `JwtAuthFilter` validates RS256 token → extracts `tenant_id`, `user_id`, roles → stores in `TenantContext` (ThreadLocal; safe with virtual threads since each request = one thread).
2. Service method opens a Spring transaction.
3. A `TransactionSynchronization`/connection customizer executes `SELECT set_config('app.current_tenant', :tenantId, true)` on that connection **before any query**. (`true` = transaction-local; resets on commit/rollback, so pooled connections can't leak context.)
4. Every RLS policy: `USING (tenant_id = current_setting('app.current_tenant', true)::uuid)` + identical `WITH CHECK`. Missing setting → NULL → zero rows → **fail closed**.
5. Runtime DB role `app_user`: not table owner, `NOBYPASSRLS`; all tables get `ENABLE` + `FORCE ROW LEVEL SECURITY`. Flyway migrates as `app_owner`.
6. Application repositories *additionally* filter by tenant where natural (belt and suspenders), but correctness never depends on it.

### 2.3 Inventory math (invariants, enforced by triggers + service layer)

```
on_hand(variant, location, lot)  = Σ inventory_ledger.quantity_delta      (append-only)
allocated(variant, location)     = Σ allocations.quantity WHERE status='ACTIVE'
available_to_promise             = on_hand − allocated
```

- `inventory_ledger`: INSERT-only. A DB trigger raises on UPDATE/DELETE. Corrections are compensating entries (`ADJUST` with `reason_code`, `reversal_of_ledger_id`).
- `movement_type ∈ {RECEIVE, SHIP, ADJUST, TRANSFER_IN, TRANSFER_OUT}`; transfers are two rows sharing a `transfer_group_id` (both sides always visible).
- `inventory_levels` (summary table): AFTER-trigger on ledger/allocations upserts `(tenant_id, variant_id, location_id, lot_id) → on_hand, allocated`. A `CHECK (on_hand >= 0)` is toggled per tenant setting `allow_negative_inventory` at the service layer (DB check stays permissive; service enforces).
- Allocation flow (pessimistic, race-free):
  ```sql
  SELECT * FROM inventory_levels
   WHERE variant_id = :v AND location_id = ANY(:locs)
     AND (on_hand - allocated) > 0
   ORDER BY lot_expiry NULLS LAST          -- FEFO when lots exist
   FOR UPDATE SKIP LOCKED;
  ```
  then insert `allocations` rows within the same transaction. Two workers fulfilling concurrently never block on nor double-allocate the same level row.

### 2.4 Money & payments flow (mocked Stripe Connect for MVP)

- `Money = NUMERIC(19,4) amount + CHAR(3) currency`. Convert to minor units only at the Stripe adapter boundary.
- Invoice → `StripeConnectGateway` (interface) → MVP `MockStripeGateway` returns deterministic fake `pi_…` IDs and simulates webhook delivery into the real webhook inbox path, so the reconciliation code is production-shaped from day one.
- `payment_intents` stores `application_fee_amount` (platform spread) and `on_behalf_of` connected account → the split-funds model is queryable for revenue reporting.
- Webhook processing: signature check → insert into `webhook_events` (unique on `(source, external_event_id)` = idempotent) → process async on virtual-thread executor → mark `processed_at`.

### 2.5 Frontend architecture

- **State split:** TanStack Query owns all server data (persisted to IndexedDB, `networkMode: 'offlineFirst'`); Zustand owns session, active warehouse, scan buffer, UI flags. No server data in Zustand.
- **`useBarcodeScanner` (HID):** window-level `keydown` listener; keystrokes with inter-key gap < 35 ms accumulate into a buffer; `Enter` terminates → emit scan. Human typing (slower cadence) is ignored; listener suspends when focus is in an editable element unless `captureAll` is set. Configurable prefix/suffix strip from tenant settings.
- **Camera scanning:** `<CameraScanner>` streams frames via `OffscreenCanvas`/`ImageBitmap` to `decode.worker.ts` (BarcodeDetector → zxing-wasm fallback). Main thread only receives decode results. Throttled to ~10 fps decode attempts; debounced duplicate suppression.
- **Feedback contract (single `useScanFeedback` hook):** success = 50 ms vibration + 880 Hz beep + 150 ms green flash overlay; error = 200 ms double vibration + 220 Hz buzz + red flash. Audio via a pre-unlocked `AudioContext` (unlocked on first user gesture).
- **Offline:** mutation queue in IndexedDB; each queued mutation carries a client-generated `Idempotency-Key`; replay on `online` event in FIFO order; conflicts surface as review items, never silent drops.
- **Warehouse UX rules:** min tap target 56×56 px on scanner views, WCAG AAA contrast, no hover-dependent affordances, works in landscape and portrait.

### 2.6 Design system — two surfaces, one product

The #1 recurring complaint against incumbents (Part 5) is UI: Fishbowl "looks like 2015", Cin7 is "cluttered with toggle switches", warehouse staff face "ERP-level complexity". We win this by designing two deliberately different surfaces instead of one compromise UI:

**Surface A — Office (desktop-first: dashboards, catalog, orders, invoices, settings).**
- **shadcn/ui + Tailwind** component base; a single `tokens.css` defines color scale, radius, spacing, typography (Inter). Light theme default, clean density, generous whitespace.
- Every list screen follows one pattern: toolbar (search + filters + primary action) → virtualized table → detail drawer (no full-page navigations for viewing a record).
- Every screen ships all four states: loading (skeletons, never spinners on tables), empty (illustration + one-line explanation + primary CTA, e.g. "No products yet → Add your first product / Import CSV"), error (retry affordance), and populated.
- Command palette (`Ctrl+K`) for navigation and quick actions — power users never touch the mouse.
- Dashboard home after login: stock value, low-stock alerts, open orders, unpaid invoices — the business owner sees their operation in 5 seconds.

**Surface B — Warehouse (mobile/tablet-first: receiving, picking, cycle counts).**
- High-contrast dark theme, 56 px+ targets, one primary action per screen, progress always visible ("Item 3 of 12"), zero required text inputs on happy paths.
- Feedback contract from §2.5 applied uniformly.

**Shared rules:** no more than one accent color per screen; destructive actions always behind confirm; all times in tenant's timezone; numbers right-aligned and monospaced in tables; forms validate inline on blur, never only on submit.

### 2.7 Tenant administration (per-business self-service)

Each tenant company manages itself entirely in-product — no operator intervention:

- **Users & roles:** list users, invite by email (`invitations` table: email, role, token_hash, expires_at, accepted_at; accept link → set password → user created under that tenant), change role, deactivate (revokes refresh tokens). OWNER/ADMIN only. Last-OWNER demotion is blocked.
- **Company settings (persisted to `tenant_settings.settings` JSONB, schema-validated server-side):** company profile + logo, default currency, timezone, barcode prefix/suffix strip rules, `allow_negative_inventory`, default reorder point/quantity, document number formats (`INV-{YYYY}-{seq:5}`), invoice payment terms, platform-fee display.
- **Warehouse & location management:** create warehouses, edit zone/aisle/bin hierarchy, print bin barcode labels (client-side code128 rendering).
- Settings UI = tabbed page (Profile, Users, Warehouses, Inventory Rules, Documents, Billing) under `/settings`, ADMIN-gated by route guard *and* API `@PreAuthorize`.

---

## Part 3 — Data Model (complete table inventory)

Every table: `id UUID PK DEFAULT gen_random_uuid()`, `tenant_id UUID NOT NULL` (except `tenants` itself, where `id` *is* the tenant id and RLS uses `id`), `created_at/updated_at timestamptz`. RLS ENABLE + FORCE on all.

### 3.1 Identity & tenancy
| Table | Key columns beyond standard |
|---|---|
| `tenants` | name, slug (unique), status |
| `tenant_settings` | settings JSONB (currency, barcode prefix rules, allow_negative_inventory, reorder defaults…), one row per tenant, GIN index |
| `users` | email (unique per tenant), password_hash (bcrypt), display_name, status |
| `roles` | code (`OWNER, ADMIN, WAREHOUSE_MANAGER, PICKER, VIEWER`), seeded per tenant |
| `user_roles` | user_id, role_id |
| `refresh_tokens` | token_hash, user_id, expires_at, revoked_at, replaced_by |
| `invitations` | email, role_id, token_hash, invited_by, expires_at, accepted_at — powers the "add users" flow (§2.7) |

### 3.2 Catalog & locations
| Table | Key columns |
|---|---|
| `locations` | parent_location_id (self-FK), type (`WAREHOUSE/ZONE/AISLE/BIN`), code, path materialized for display |
| `products` | sku_root, name, description, uom, deleted_at |
| `product_variants` | product_id, sku (unique per tenant), barcode (indexed — scan hot path), attributes JSONB (GIN), price NUMERIC(19,4), currency, reorder_point, reorder_qty |
| `lots` | variant_id, lot_number, expires_at, received_at |

### 3.3 Inventory core
| Table | Key columns |
|---|---|
| `inventory_ledger` | variant_id, location_id, lot_id NULL, movement_type, quantity_delta NUMERIC(19,4) NOT NULL ≠ 0, reason_code, reference_type/reference_id (PO line, SO line, cycle count…), transfer_group_id, reversal_of_ledger_id, created_by. **INSERT-only trigger guard.** Index: (tenant_id, variant_id, location_id, created_at) |
| `allocations` | sales_order_line_id, variant_id, location_id, lot_id, quantity, status (`ACTIVE/RELEASED/CONSUMED`), expires_at NULL |
| `inventory_levels` | (tenant_id, variant_id, location_id, lot_id) UNIQUE, on_hand, allocated. Trigger-maintained; never written by app code |
| `cycle_counts` / `cycle_count_lines` | location_id, status; expected_qty, counted_qty → posts ADJUST entries on approval |

### 3.4 Purchasing & sales
| Table | Key columns |
|---|---|
| `suppliers` | name, contact JSONB, payment_terms |
| `purchase_orders` | supplier_id, number, status (`DRAFT→SUBMITTED→PARTIALLY_RECEIVED→RECEIVED→CLOSED / CANCELLED`), expected_at |
| `purchase_order_lines` | variant_id, qty_ordered, qty_received, unit_cost |
| `customers` | name, email, billing/shipping address JSONB, stripe_customer_ref |
| `sales_orders` | customer_id, number, status (`DRAFT→CONFIRMED→ALLOCATED→PARTIALLY_SHIPPED→SHIPPED→CLOSED / CANCELLED`), channel |
| `sales_order_lines` | variant_id, qty_ordered, qty_allocated, qty_shipped, unit_price, tax JSONB |
| `shipments` / `shipment_lines` | sales_order_id, carrier, tracking_number, label_ref (EasyPost id), status; qty per SO line |

### 3.5 Billing & payments
| Table | Key columns |
|---|---|
| `invoices` | sales_order_id NULL, customer_id, number, status (`DRAFT→OPEN→PARTIALLY_PAID→PAID / VOID`), subtotal, tax, total, currency, due_at |
| `invoice_lines` | description, qty, unit_price, amount |
| `payment_intents` | invoice_id, provider (`STRIPE`), external_id, amount, currency, application_fee_amount, connected_account_ref, status, raw_payload JSONB |
| `payments` | payment_intent_id, amount, fee_amount, balance_txn_ref, settled_at |
| `stripe_accounts` | connected account id, onboarding status, capabilities JSONB — one per tenant |

### 3.6 Platform infrastructure
| Table | Key columns |
|---|---|
| `document_sequences` | (tenant_id, doc_type, period) UNIQUE, next_value — bumped under `FOR UPDATE` |
| `idempotency_keys` | (tenant_id, key) UNIQUE, request_hash, response_status, response_body JSONB, expires_at |
| `webhook_events` | source, external_event_id (UNIQUE with source), signature_valid, payload JSONB, received_at, processed_at, error. *Tenant resolved during processing; RLS policy permits service-role insert pre-resolution via a dedicated policy* |
| `outbox_events` | aggregate_type, aggregate_id, event_type, payload JSONB, published_at NULL |
| `external_references` | entity_type, entity_id, system (`SHOPIFY/STRIPE/EASYPOST`), external_id — UNIQUE(system, external_id, tenant_id) |
| `audit_log` | actor_user_id, action, entity_type, entity_id, diff JSONB |

### 3.7 Canonical RLS SQL (template applied to every table)

```sql
ALTER TABLE {t} ENABLE ROW LEVEL SECURITY;
ALTER TABLE {t} FORCE  ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON {t}
  USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON {t} TO app_user;  -- ledger: no UPDATE/DELETE grant
```

---

## Part 4 — Execution Phases

Rule for the executing agent: complete a phase, run its acceptance checks, then proceed. Do not interleave phases.

### Phase 0 — Scaffolding & Dev Loop
- Init monorepo dirs; backend Maven project (Spring Boot 3.x, Java 21, deps: web, security, data-jpa, validation, flyway, postgres, jooq, springdoc-openapi, testcontainers); `spring.threads.virtual.enabled=true`.
- `docker-compose.yml` with Postgres 16 only (API/frontend join in Phase 10); `ops/postgres/init` creates `app_owner` (migrations) and `app_user` (runtime, `NOBYPASSRLS`).
- Frontend: Vite + React 19 + TS + Tailwind; ESLint/Prettier; `.editorconfig`.
- **Accept:** `docker compose up db` healthy; backend boots against it; `npm run dev` renders shell page.

### Phase 1 — Database Foundation (Flyway)
- Migrations `V001…V0NN` covering **all** tables in Part 3, in dependency order; every constraint, index, RLS block, the ledger INSERT-only guard trigger, and the `inventory_levels` maintenance triggers (ledger + allocations).
- Seed migration: role codes, movement/reason enums (as Postgres enums or check constraints — choose check constraints for evolvability).
- Dev seed (separate Flyway location `db/dev-seed`, applied only when `SPRING_PROFILES_ACTIVE=dev`): one demo tenant with users for every role (`owner@demo.test` / `picker@demo.test`, known passwords), a warehouse with bins, ~50 products with barcodes, open POs/SOs — so **every subsequent phase always has realistic data to test against**. The full 10k-row demo dataset lands in Phase 11.
- **Accept (Testcontainers SQL tests):** (1) with `app.current_tenant = A`, tenant B rows invisible and un-insertable; (2) UPDATE/DELETE on ledger raises; (3) inserting ledger + allocation rows updates `inventory_levels` correctly, including lot dimension; (4) missing tenant setting returns zero rows on every table.

### Phase 2 — Data Access & Tenancy Layer
- jOOQ codegen via `testcontainers-jooq-codegen-maven-plugin` (spins ephemeral PG, applies Flyway, generates) — schema and code can never drift.
- `TenantContext` + servlet filter; transaction-synchronized `set_config` customizer (§2.2); verify it fires for both JPA and jOOQ paths (shared `DataSource`/transaction manager).
- Runtime datasource connects as `app_user`.
- **Accept:** integration test hitting a repo method without tenant context gets zero rows; with context, only that tenant's rows; jOOQ + JPA in one transaction see identical context.

### Phase 3 — Auth, RBAC & Tenant Onboarding
- RSA keypair from env; RS256 access tokens (15 min; claims: sub, tenant_id, roles) + refresh rotation (§3.1 tables); JWKS endpoint; bcrypt.
- Endpoints: `POST /api/v1/auth/signup` (creates tenant + OWNER user + default settings + default warehouse, single transaction — note: runs under a bootstrap path since tenant doesn't exist yet), `/login`, `/refresh`, `/logout`.
- Tenant administration API (§2.7): `GET/PATCH /api/v1/settings` (JSONB schema-validated), user management (`GET /users`, `POST /invitations`, `POST /invitations/accept`, `PATCH /users/{id}/role`, `POST /users/{id}/deactivate` — deactivation revokes refresh tokens; last-OWNER demotion blocked). Invite emails are logged to console in dev (mail adapter interface for later).
- Method security: `@PreAuthorize("hasRole('WAREHOUSE_MANAGER')")` etc.; role hierarchy OWNER > ADMIN > WAREHOUSE_MANAGER > PICKER > VIEWER.
- **Accept:** full signup→login→refresh→revoke test; invite→accept→login as new user test; PICKER calling a manager endpoint gets 403; forged/expired tokens get 401; deactivated user's refresh token is dead.

### Phase 4 — Core Inventory Domain
- `InventoryService`: `receive(poLine|adhoc)`, `adjust(reason)`, `transfer(from,to)` (two ledger rows, one transfer_group), `ship(allocation)` — all append-only, all validating `allow_negative_inventory` from tenant settings.
- `AllocationService`: FEFO `FOR UPDATE SKIP LOCKED` flow (§2.3), release, consume-on-ship.
- Receiving workflow against PO lines (updates qty_received, closes PO when complete). Cycle count post/approve → ADJUST entries.
- **Accept:** concurrency test — 2 threads × allocate 10 units from a level of 15 → exactly one succeeds fully, one gets partial/backorder, never an oversell (assert via ledger sum); FEFO test picks earliest expiry lot; negative-inventory blocked/allowed per setting.

### Phase 5 — Orders, Invoicing & Payments
- Sales order lifecycle service enforcing the §3.4 state machine; partial shipment updates `qty_shipped` and order status; shipment creation writes SHIP ledger entries and consumes allocations atomically.
- `InvoicingService`: generate invoice from SO (tenant-scoped numbering via `document_sequences`), `StripeConnectGateway` interface + `MockStripeGateway`, application-fee computation from tenant settings (default 0.4%).
- Webhook inbox endpoint `POST /api/v1/webhooks/stripe`: signature verify (mock secret), idempotent insert, async processing on virtual-thread executor → payment settlement → invoice status transitions.
- **Accept:** end-to-end test: SO confirm → allocate → ship → invoice → mock payment intent → replayed webhook (delivered twice) settles exactly once → invoice PAID.

### Phase 6 — REST API Surface
- Controllers for all domains under `/api/v1`; springdoc OpenAPI with full schemas; RFC 7807 problem-detail errors; cursor-based pagination (`?cursor=&limit=`) on list endpoints; `Idempotency-Key` header honored on all POSTs (replay returns stored response).
- Barcode lookup hot path: `GET /api/v1/scan/{barcode}` → variant + levels at active warehouse, single jOOQ query, target p95 < 30 ms.
- Export OpenAPI JSON to `frontends/apps/frontend_wms/openapi.json` for type generation.
- **Accept:** OpenAPI validates; duplicate `Idempotency-Key` POST returns identical response without double side effects; scan endpoint returns levels.

### Phase 7 — Frontend Infrastructure
- Axios instance: JWT injection, single-flight 401→refresh→retry queue; generated API types from OpenAPI.
- Zustand stores (session, activeWarehouse persisted to localStorage, scan buffer); TanStack Query with IndexedDB persister, `offlineFirst` network mode.
- `useBarcodeScanner` HID hook per §2.5 (timing-based, Enter-terminated, input-focus aware, prefix stripping from tenant settings) + `useScanFeedback` (vibration/audio/flash).
- Offline mutation queue with idempotency keys and reconnect replay; PWA manifest + service worker (app-shell precache).
- Set up the design system (§2.6): shadcn/ui, `tokens.css`, base primitives (Button, Input, Table, Drawer, Dialog, Toast, Skeleton, EmptyState, BigButton).
- **Login page** (polished, branded, per §2.6 — this is the product's first impression), signup/tenant-onboarding wizard (company name → currency/timezone → first warehouse), invitation-accept page; app shell with sidebar nav, warehouse switcher, and `Ctrl+K` command palette.
- **Accept:** simulated 30-keystroke burst + Enter fires one scan event and human-speed typing fires none (unit tests with fake timers); token refresh under parallel 401s issues one refresh call; airplane-mode mutation replays on reconnect in dev test.

### Phase 8 — High-Performance Warehouse UI
- **Product Master grid:** TanStack Virtual over cursor-paginated infinite query; 10k+ rows at 60 fps; columns: SKU, name, attributes, on-hand, allocated, ATP; instant filter; row → variant detail with ledger history.
- **Fulfillment Scanner (mobile-first):** pick-list for an SO → scan-to-verify each item (HID hook + `<CameraScanner>` with the decode worker per §2.5) → success/error feedback contract → over-scan and wrong-item errors → complete shipment (queued offline if needed). 56 px+ tap targets, high-contrast dark theme, zero text inputs required on the happy path.
- **Accept:** grid scroll DOM node count stays bounded (~30 rows rendered); scanner view Lighthouse mobile a11y ≥ 95; wrong barcode produces red flash + buzz and no mutation.

### Phase 9 — Office UI & Tenant Administration
- **Dashboard** (post-login home): stock value, low-stock alerts (reorder-point breaches), open orders by status, unpaid invoices, recent activity feed — all from existing endpoints.
- **Settings area** (`/settings`, ADMIN-gated route guard + API RBAC) per §2.7: Company Profile, **Users** (list, invite by email with role, change role, deactivate — with pending-invitation states), Warehouses & bin hierarchy editor with printable bin labels, Inventory Rules (negative inventory, reorder defaults, barcode prefix rules), Documents (number formats, payment terms), Billing (fee display).
- CRUD screens for the remaining domains following the §2.6 list-screen pattern: purchase orders (+ receiving), sales orders (+ allocate/ship actions), invoices (+ record-payment via mock gateway), customers, suppliers. Every screen has real empty/loading/error states.
- **Accept:** a brand-new tenant can, through UI alone: sign up → configure settings → invite a second user with PICKER role → that user logs in and sees only picker-permitted navigation; a full PO→receive→SO→allocate→ship→invoice cycle is completable without touching an API client.

### Phase 10 — Containerization & DevOps
- Backend Dockerfile: maven dependency-cache stage → build → `eclipse-temurin:21-jre` runtime, non-root user, `HEALTHCHECK` on actuator.
- Frontend Dockerfile: node build → nginx with SPA fallback + gzip + immutable asset caching; API proxy.
- `docker-compose.yml`: postgres (volume + init roles), backend (Flyway on boot, depends_on healthy), frontend; `.env.example` for secrets (JWT keys, DB creds, mock Stripe secret).
- **Accept:** `docker compose up --build` from clean clone → signup → receive stock → scan → ship → invoice paid, entirely inside containers.

### Phase 11 — E2E Suite, Demo Data & Hardening
- Full demo dataset (extends the Phase 1 dev seed): demo tenant with users for every role, 2 warehouses with bin hierarchy, 500 products / 2,000 variants with scannable barcodes, historical ledger activity, open POs/SOs, paid + unpaid invoices — every screen and chart is demonstrable on first login with `owner@demo.test`.
- **Playwright E2E suite** against the compose stack — journeys defined in Part 6.3.
- Observability: tenant_id + request_id in MDC, JSON logs, Micrometer + actuator.
- Full test sweep gate: RLS leak matrix (every table), allocation race harness (16 threads), webhook replay, offline replay, complete E2E run. README with runbook and demo credentials.
- **Accept:** all suites green (Part 6 coverage gates met); README quick-start verified from scratch: clone → `docker compose up` → log in with seeded credentials → demo every feature.

---

## Part 5 — Competitive Analysis & Win Conditions (researched Jul 2026)

### 5.1 Landscape

| Competitor | 2026 pricing | Strengths | Verified weaknesses (reviews/analyses, 2026) |
|---|---|---|---|
| **Cin7 (Core/Omni)** | $349–$999/mo + per-user fees; opaque quoting | 700+ integrations, EDI, breadth | Steep implementation (60–90 days typical, mandatory paid onboarding), "ERP-level complexity for warehouse staff", support response 18–36 h and declining post-acquisition, sync bugs, opaque pricing (SMB reviewers score it ~2.0/5 on price, ~2.5/5 on ease of use) |
| **Fishbowl** | $229–$729/mo (annual); Advanced quoted only | Deepest QuickBooks integration, mature WMS (wave/zone picking), gold-standard lot/serial traceability | "UI looks like 2015", clunky navigation, spartan mobile app ("wouldn't run a busy warehouse from it"), basic reporting that costs extra to customize |
| **Katana** | Free tier; ~$99–$799/mo | Clean modern UI, fast onboarding, visual manufacturing/BOM | Weak for wholesalers/B2B order management, shallow WMS (no bin-level workflows at depth), limited EDI/multi-channel |
| **inFlow** | ~$110–$1,300/mo, scales on order count | Fastest receiving flow in comparative testing, approachable | Mobile apps are "companions, not standalone tools", basic assemblies only, pricing scales aggressively with order volume |
| **Zoho Inventory** | Free–$299/mo | Cheapest entry, Zoho ecosystem | Slow operations in testing (34 s stock update vs. inFlow's 4 s), shallow warehouse features, weak outside Zoho stack |

Cross-cutting market findings: ~65% of inventory-system rollout failures are attributed to data migration and staff training, not software bugs; every incumbent charges pure subscription (none monetizes the payment flow); none offers a genuinely offline-capable warehouse UI.

### 5.2 Our win conditions (each maps to something already in this plan)

1. **Warehouse UX nobody else has.** Fishbowl is dated, Cin7 overwhelms floor staff, inFlow/Zoho mobile is an afterthought. Our Surface B (§2.6) — glove-ready targets, scan-first flows, haptic/audio feedback, **offline mutation queue** — is a category differentiator, not a feature checkbox. No incumbent works through a Wi-Fi dead zone.
2. **Self-serve onboarding measured in minutes, not months.** Cin7's mandatory paid onboarding and 60–90 day setup is the market's biggest pain. Our signup wizard (Phase 7) + CSV import + seeded example flows target "first scan within 15 minutes of signup." Ship a guided checklist on the dashboard (add product → receive stock → first order).
3. **Structural price advantage via embedded payments.** Incumbents need $229–$999/mo subscriptions because that's their only revenue. Our 0.2–0.5% payment spread (already in the schema: `application_fee_amount`) lets us undercut on subscription and grow revenue with tenant GMV instead of seat count.
4. **Trustworthy inventory math.** Cin7 reviews cite an "$80k unexplained inventory discrepancy for 12 months." Our append-only ledger makes every quantity provable to its history (`SUM(quantity_delta)` invariant, Part 7.2) — "your books always reconcile" is a sales weapon against every mutable-quantity incumbent.
5. **Transparent pricing.** Publish it. Cin7's quote-only opacity is explicitly resented in reviews.
6. **API-first with real OpenAPI**, where incumbents bolt on partner-only APIs.

### 5.3 Honest gaps vs. incumbents (post-MVP roadmap, in priority order)
1. **Accounting sync (QuickBooks Online first)** — Fishbowl's moat; table stakes for the mid-market. Outbox + external_references make this an adapter, not a rewrite.
2. **Shopify/channel sync** — Katana/Cin7 strength; webhook inbox is already built for it.
3. **Manufacturing BOM/kitting** — Katana's moat; deferred deliberately (G18).
4. **Reporting/BI depth** — jOOQ analytical layer is the foundation; ship saved reports later.
5. **EDI** — enterprise-only; ignore until pulled by customers.

**Positioning sentence:** *"Enterprise-grade inventory truth with a warehouse app your floor staff actually likes, live in an afternoon, at half the subscription — because we make money when you get paid, not by charging you more."*

---

## Part 6 — Test Strategy (the answer to "is it tested?")

### 6.1 Backend — JUnit 5
- **Unit tests** (plain JUnit + Mockito): domain logic — state machines, money math, fee computation, FEFO ordering, barcode prefix parsing, settings validation. Fast, no containers.
- **Integration tests** (JUnit + **Testcontainers PostgreSQL**, real Flyway migrations, running as `app_user`):
  - RLS leak matrix: for *every* table, tenant A cannot SELECT/INSERT/UPDATE/DELETE tenant B rows; no tenant context ⇒ zero rows.
  - Ledger immutability (UPDATE/DELETE raises), `inventory_levels` trigger correctness incl. lot dimension.
  - Allocation race harness: 16 threads competing over constrained stock ⇒ ledger sum invariant holds, zero oversell.
  - Auth: signup/login/refresh-rotation/revocation, invitation accept, RBAC 403s, deactivation kills sessions.
  - Idempotency replay, webhook duplicate delivery, document sequence under concurrency.
- Gate: `mvn verify` green is a phase-exit requirement from Phase 1 onward; new service code lands with its tests in the same phase, never "later".

### 6.2 Frontend — Vitest + React Testing Library
- `useBarcodeScanner`: fake-timer keystroke bursts vs. human typing; prefix stripping; focus suppression.
- Axios refresh logic: parallel 401s ⇒ single refresh; offline mutation queue: enqueue/replay/ordering with idempotency keys.
- Zustand stores and critical components (scanner feedback states, settings forms validation, role-gated navigation rendering).

### 6.3 End-to-end — Playwright (runs against `docker compose up` stack, seeded DB)
Journeys (each a spec file, run in CI order):
1. **Onboarding:** signup wizard → dashboard checklist visible → add product → receive stock → level visible.
2. **Team:** OWNER invites PICKER → accept invite → PICKER logs in → sees warehouse surface only, `/settings` blocked.
3. **Settings:** change currency + negative-inventory toggle → verify effect on invoice creation and over-shipment attempt.
4. **Fulfillment (mobile viewport):** create SO → allocate → scan-verify pick (simulated HID keystrokes) → wrong barcode shows error feedback → complete shipment → ledger history shows SHIP.
5. **Money:** invoice from SO → mock payment → webhook settles → invoice PAID → fee amount recorded.
6. **Isolation smoke:** two tenants in one browser session (two contexts) — no data bleed in any list screen.
- Plus: Product Master grid scroll performance assertion (bounded DOM nodes) and Lighthouse a11y ≥ 95 on scanner view.

### 6.4 CI order
Lint → backend unit → backend integration (Testcontainers) → frontend unit → build images → compose up → Playwright E2E. A phase is not "done" until its slice of this pipeline is green.

---

## Part 7 — Definition of Done (whole project)

1. No query path can read or write another tenant's rows — proven by tests, enforced by Postgres, not by discipline.
2. `SELECT SUM(quantity_delta)` over the ledger always equals `inventory_levels.on_hand` for every key — proven under concurrent load.
3. The ledger has zero UPDATE/DELETE grants and a trigger guard; git history contains no mutable-quantity shortcut.
4. Every mutating endpoint is idempotent under retry.
5. A warehouse worker can complete a pick with gloves on, offline, using only scans and one giant confirm button.
6. The platform fee on every mocked payment is computed, stored, and reportable — the monetization model is in the schema, not a TODO.
7. A new business can self-serve everything: sign up on the login page, configure its own settings, invite and manage its own users with roles — no operator involvement, ever.
8. The full test pyramid (Part 6) is green: JUnit unit + Testcontainers integration, Vitest frontend, and all Playwright E2E journeys against the containerized stack with seeded demo data (`owner@demo.test`).
9. Every screen looks intentionally designed (§2.6): consistent tokens, real empty/loading/error states, no raw unstyled views anywhere.

---

## Part 8 — Shipped evolution (post-MVP, not a rewrite of Phases 0–11)

The numbered phases above delivered the tenant WMS. What shipped after that, and must stay accurate in docs/`deploy.bat`:

| Track | What it is |
|-------|------------|
| Dual plane | `invsys-app` + `frontend_wms` (`:8080` / `:3000`) vs `invsys-admin-api` + `frontend_admin` (`:8081` / `:3002`) |
| Super Admin identity | `platform_admins` (V106) — not `users.is_super_admin` |
| Entitlements | `tenant_subscriptions` (V104/V105) + `@RequireModule` on WMS APIs |
| Control-plane Day-2 | Impersonation (15-min WMS JWT), suspend, billing estimates, RAG ingest, integration kill-switch, `platform_audit_logs`, shard routing, DLQ retry, rate-limit multipliers, sandbox clone, compliance broadcasts (V107–V108) |
| Deploy | `deploy.bat` starts **both** planes; `--no-chatbot` / `--with-chatbot` / `--clean-frontend` are valid as the first argument |
