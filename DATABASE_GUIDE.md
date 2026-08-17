# WMS Database Guide

A plain-language map of how InventorySystem stores warehouse data in PostgreSQL 16.

**Who this is for:** new developers, product owners, and anyone who needs to know *what* tables exist and *why* — without reading every Flyway migration.

**Companion docs:** `DEVELOPER_ARCHITECTURE.md` (how the app uses this schema), `USER_GUIDE.md` (day-to-day product use), `README.md` (run the stack).

Schema is owned by Flyway (`backend/invsys-core/src/main/resources/db/migration/`). Current head is **V121**. Hibernate runs with `ddl-auto: validate` — never invent columns only in JPA. Retail POS register prefs are JSONB keys on `tenant_settings.settings` (no dedicated migration).

---

## The two rules that never bend

### 1. Apartment building — multi-tenant RLS

Many companies share one database. Every business row carries a `tenant_id`. PostgreSQL **Row-Level Security (FORCE RLS)** only returns rows for the tenant bound in session setting `app.current_tenant`.

- Runtime JDBC user: `app_user` (cannot bypass RLS).
- Migrations / seed: `app_owner`.
- Application binds the tenant on each connection via `TenantAwareDataSource`.

A user from Company A cannot query Company B’s stock, orders, or audit trail — even with a buggy API filter.

### 2. Bank statement — append-only inventory

Inventory truth is not “overwrite quantity 100 → 90”. Every movement **appends** a row to `inventory_ledger` (`RECEIVE`, `SHIP`, `ADJUST`, `TRANSFER`, `ASSEMBLY`, LPN moves, corrections, …). Mistakes are fixed with a compensating entry, never by editing history.

`inventory_levels` is a **read model gauge** (on-hand / allocated), not the source of truth.

```
Available to Promise ≈ on_hand − allocated
```

---

## How stock numbers stay fast

1. A ledger insert fires a trigger that appends a small row to **`inventory_level_deltas`** (Flyway **V076**).
2. **`InventoryLevelFlushWorker`** (virtual threads + `FOR UPDATE SKIP LOCKED`) batches those deltas into **`inventory_levels`**.
3. **Allocations** still update `allocated` synchronously so sales reservations stay correct under load.

Do **not** hand-edit `inventory_levels` in seed or admin SQL. Seed stock by writing ledger receives (see `ops/demo_seed.sql`).

---

## Hot tables that grow forever — monthly partitions

`inventory_ledger` and `audit_log` are **RANGE-partitioned by `month` of `created_at`** (Flyway **V087** / **V088**).

| Topic | Detail |
|-------|--------|
| Partition key | `created_at` (monthly children like `inventory_ledger_y2026m07`) |
| Primary key | `(id, created_at)` on the parent |
| Forward / back coverage | **V088** ensures ~12 months historical + 6 months ahead |
| Maintenance | SQL function `ensure_monthly_partitions(...)` + Java `PartitionMaintenanceWorker` (runs monthly) |
| Entities | JPA marks these tables `@Immutable` — Hibernate must not UPDATE them |

Indexes stay compound with `(tenant_id, created_at, …)` so tenant queries prune partitions.

---

## Audit trail: hot DB → cold object storage

| Layer | What happens |
|-------|----------------|
| Hot | Every sensitive change lands in **`audit_log`** (append-only; DB triggers in **V085** block casual UPDATE/DELETE). |
| Cold | **`AuditLogArchivalWorker`** (nightly cron, default retention **90 days**) gzip-compresses aged rows to **JSONL** in S3/MinIO (`archives/{tenantId}/audit/...jsonl.gz`). |
| Purge | Rows are deleted **only** via security-definer `archive_purge_audit_logs(uuid[])` (**V086**/**V087**) **after** a successful upload. Failed uploads leave rows in Postgres and increment metric `wms.audit.archive.failures`. |
| Office UI | Settings → Operations can list/download cold archives for compliance. |

---

## Domain map (tables by job)

### 1. Identity & access

| Table | Purpose |
|-------|---------|
| `tenants` | Company workspace (`status` includes `ACTIVE` / `SUSPENDED`) |
| `tenant_settings` | Currency, negative-stock rules, barcode masks, density prefs, plus Retail POS JSONB keys (`pos_receipt_header`, `pos_receipt_footer`, `pos_default_currency`, `pos_require_blind_closeout`, `pos_enable_cfdi_invoicing`) — no extra Flyway columns |
| `tenant_subscriptions` | Commercial **tier** + `enabled_modules` JSON (**V104** / **V105**) — control-plane writable via `app_owner` |
| `tenant_domains` | Verified corporate domains (CORS / email / Home Realm Discovery). **V115** adds `dns_verification_token` + `is_verified` |
| `tenant_sso_configs` | SAML / OIDC enterprise login. **V115** adds `sso_provider`, `acs_url`, `saml_certificate`, `corporate_cidr_ips` |
| `users` | Tenant people (email, password hash, profile fields) — **not** Super Admins |
| `platform_admins` | Control-plane Super Admin identities (**V106**, no tenant RLS) |
| `platform_admin_refresh_tokens` | Admin session refresh (**V106**) |
| `roles` / `user_roles` | `OWNER`, `ADMIN`, `WAREHOUSE_MANAGER`, `PICKER`, `VIEWER`, `B2B_CUSTOMER`, plus retail POS roles `RETAIL_CASHIER` / `RETAIL_MANAGER` (**V118**). Multi-role is additive — one user may hold several rows |
| `user_warehouses` | **LBAC** — which warehouses a user may operate |
| `invitations` | Time-limited invite hashes; `additional_roles` CSV (**V120**) carries extra role codes for multi-role invites |
| `refresh_tokens` | Rotating WMS session refresh; `app_context` (**V119**) preserves the POS/WMS JWT sandbox across refresh |
| `magic_login_tokens` | Passwordless / supplier-portal magic links |

### 2. Catalog

| Table | Purpose |
|-------|---------|
| `products` / `product_variants` | Family vs sellable SKU (barcode, cost, enterprise fields: HS tariff, hazmat, ABC, lifecycle — **V080**) |
| `variant_uom_conversions` | Case ↔ each math |
| `lots` | Batch / expiry / serial (FIFO/FEFO, DSCSA/FSMA paths) |
| `tax_rates` | Invoice tax |

### 3. Physical space & digital twin

| Table | Purpose |
|-------|---------|
| `locations` | `WAREHOUSE → ZONE → AISLE → BIN`; optional `coord_x/y/z`, lat/long, putaway constraints |
| `walkable_edges` | Graph for **A\*** pick wayfinding |
| `license_plates` | Pallet/tote LPNs (`OPEN`, `IN_TRANSIT`, `CLOSED`, `DISPATCHED`) |
| `rtls_tags` / `rtls_position_events` | Real-time location tags (**V084**) |
| `vehicle_assignments` | Van / tech mobile stock |
| `dashboard_kpi_snapshots` | CQRS KPI read model (not live ledger scans) |

### 4. Inventory core

| Table | Purpose |
|-------|---------|
| `inventory_ledger` | Append-only movements (partitioned) |
| `inventory_level_deltas` | Async on-hand queue |
| `inventory_levels` | Gauge keyed by `(tenant, variant, location, lot, lpn)` |
| `allocations` | Soft reservations for sales / waves |
| `cycle_counts` / `cycle_count_lines` | Blind counts + variance escalation |

### 5. Inbound (purchasing)

| Table | Purpose |
|-------|---------|
| `suppliers` | Vendors |
| `purchase_orders` / `purchase_order_lines` | PO lifecycle + qty received; optional `notes` (mesh SO link text) |
| `demand_forecasts` | Restock suggestions |
| `ap_invoice_ingestions` | Supplier invoice OCR / AP ingest |

### 6. Outbound (sales & fulfillment)

| Table | Purpose |
|-------|---------|
| `customers` | Buyers |
| `sales_orders` / `sales_order_lines` | Includes status **`BACKORDERED`**, **`NEEDS_REVIEW`** |
| `shipments` / `shipment_lines` | Pack / carrier / optional LPN ship |
| `picking_waves` / `picking_batches` / `picking_tasks` | Wave pick; `tote_identifier` for multi-order batches |
| `wave_replenishment_triggers` | Predictive pick-face replenishment |
| `returns` / `return_lines` | RMA + disposition (`RESTOCK` / `SCRAP` / …) |

### 7. B2B portal & mesh

| Table | Purpose |
|-------|---------|
| `customer_user_mappings` | Login ↔ B2B customer |
| `customer_price_tiers` / `customer_credit_lines` | Pricing & NET terms |
| `tenant_mesh_partners` | Cross-tenant handshake + PO ↔ SO bridge (`PENDING` / `REQUESTED` / `CONNECTED` / `DISCONNECTED`; `supplier_id` / `customer_id` nullable until approve) |
| `mesh_catalog_listings` | Per-variant publish-to-network flag + mesh wholesale price |

### 8. Manufacturing

| Table | Purpose |
|-------|---------|
| `boms` / `bom_lines` | Recipe |
| `manufacturing_operations` / `bom_operations` | Steps & standard cost |
| `production_orders` | Build jobs |
| `team_labor_rates` | Labor into landed cost |

### 9. Billing & money

| Table | Purpose |
|-------|---------|
| `invoices` / `invoice_lines` | AR bills |
| `stripe_accounts` / `payment_intents` / `payments` | Stripe Connect |
| `currency_rates` | **Global** FX table (no tenant RLS) |

### 10. Integrations & safety plumbing

| Table | Purpose |
|-------|---------|
| `outbox_events` | Reliable outbound jobs (Shopify, accounting, mesh, labels, …) |
| `webhook_events` | Inbound webhook inbox |
| `idempotency_keys` | Anti double-submit |
| `external_references` | Our id ↔ Shopify/Amazon id |
| `integration_credentials` | Encrypted OAuth / API secrets (**envelope encryption**) |
| `integration_channels` | Channel connection rows (**V083**; prefer this over older wording “channel_integrations”) |
| `integration_sync_logs` | Sync success/fail history |
| `account_mappings` | Ledger event → QBO/Xero GL codes |
| `edi_trading_partners` | EDI/AS2 partners |
| `offline_sync_conflicts` | Floor offline replay failures |
| `document_sequences` | Gapless INV-/SO- numbers |
| `audit_log` | Security / compliance trail (partitioned; cold-archived) |

### 11. Ops extensions (cartons, 3PL, views, workstations)

| Table | Purpose |
|-------|---------|
| `shipping_cartons` | Cartonization / pack dimensions (**V065**) |
| `workstation_settings` | Pack-station printers & device prefs (**V066**) |
| `billing_slas` / `billing_accruals` | 3PL SLA billing (**V067**) |
| `user_saved_views` | Named filter presets for office grids (**V070**) |
| `picking_batches.claimed_at` / `completed_at` | LMS labor timing (**V069**) |

### 12. Support RAG & GraphRAG (**global** — not tenant-scoped)

These power the in-app support copilot (`/api/v1/support/chat`). They deliberately have **no `tenant_id` and no RLS** — they hold product manuals / runbooks, not customer secrets. Same pattern as `currency_rates`.

| Table | Flyway | Purpose |
|-------|--------|---------|
| `support_knowledge_chunks` | **V089** / **V092** | Embedded doc chunks: `slug`, `title`, `body`, `audience_roles[]`, `route_hints[]`, `embedding vector(768)` with **HNSW** cosine index (`pgvector`) |
| `support_knowledge_nodes` | **V090** | GraphRAG nodes (`ZONE`, `FLOW`, `DOC`, `ENTITY`, `ROLE`) optionally linked to a chunk slug |
| `support_knowledge_edges` | **V090** | Typed relationships (`from_slug` → `to_slug`, e.g. Procurement → Fulfillment) |
| `platform_knowledge_documents` | **V107** | Super Admin–ingested SOP markdown (source of additional chunks) |

Extension `vector` is created in Postgres init (`ops/postgres/init`), not by `app_owner` migrations.

### 13. Control-plane governance (**global** — Super Admin / `app_owner`)

These tables back `invsys-admin-api`. Tenant users must not treat them as WMS CRUD.

| Table | Flyway | Purpose |
|-------|--------|---------|
| `tenant_subscriptions` | **V104** | `BASIC` / `INTERMEDIATE` / `ENTERPRISE` + `enabled_modules` |
| `platform_admins` | **V106** | Super Admin login (same email as a WMS user is a different row) |
| `tenant_shard_routing` | **V107** | Tenant → shard / Aurora / region dictionary |
| `tenant_integration_controls` | **V107** | Outbox **kill-switch** (`sync_paused`) |
| `tenant_rate_limit_overrides` | **V107** | Capacity multiplier + per-path limits |
| `platform_compliance_broadcasts` | **V107** | Global tax / hazmat / regulatory fan-out |
| `platform_sandbox_credentials` | **V107** | One-time API key for cloned UAT tenants |
| `platform_audit_logs` | **V108** | Append-only Super Admin mutation trail (`action`, `diff_json`, IP) |

---

## How tenancy is bound (runtime)

1. JWT filter authenticates the user and resolves `tenantId`.
2. `TenantAwareDataSource` / connection wrapper runs  
   `SET LOCAL app.current_tenant = '<uuid>'` on the borrowed connection.
3. RLS policies compare `tenant_id` to  
   `nullif(current_setting('app.current_tenant', true), '')::uuid` (fail-closed when unset).
4. PgBouncer transaction pooling uses `DISCARD ALL` so the next checkout never inherits another tenant’s GUC.

Bootstrap / migration SQL uses `app_owner`. Application runtime uses `app_user`.

---

## Security helpers (SECURITY DEFINER)

These run with elevated privileges for specific, audited jobs — not for general CRUD:

| Function | Role |
|----------|------|
| `ensure_monthly_partitions(...)` | Create ledger/audit month partitions |
| `archive_purge_audit_logs(uuid[])` | Delete audit rows **after** S3 archive upload |
| `invsys_audit_trigger()` | Harden append-only audit behavior |

---

## Credential vault (not a table shape — app layer)

`integration_credentials` stores ciphertext. `CredentialVaultService` uses envelope encryption (`ENV1` + AES-GCM DEKs). Wrap provider is configured as:

- `LOCAL` (dev / default)
- `AWS_KMS`
- `HASHICORP_VAULT`

Property: `invsys.integration.vault-provider`.

---

## Webhook replay protection (app filter)

`WebhookReplayDriftFilter` rejects Stripe/Shopify webhook deliveries whose timestamp is more than **300 seconds** off wall clock (HTTP 401). This is not a table — it sits in front of public webhook controllers. Nginx also rate-limits magic-login and fulfillment scan paths (see `ops/api-gateway/nginx.conf`).

---

## Recent Flyway head (V080–V121)

| Version | Purpose |
|---------|---------|
| **V080** | ProductVariant enterprise trade/handling/lifecycle fields |
| **V081** | Two-tier user profile + UI density preferences |
| **V082** | Sales order status `NEEDS_REVIEW` |
| **V083** | `integration_channels` + richer sync logs |
| **V084** | Geo coords on locations; `rtls_tags` / `rtls_position_events` |
| **V085** | Append-only audit trigger hardening |
| **V086** | `archive_purge_audit_logs` SECURITY DEFINER |
| **V087** | RANGE partition `inventory_ledger` + `audit_log` |
| **V088** | Ensure ~12 months back + 6 months forward partitions |
| **V089** | `support_knowledge_chunks` + HNSW (pgvector; later 768-d in **V092**) |
| **V090** | `support_knowledge_nodes` / `support_knowledge_edges` (GraphRAG) |
| **V091** | Offline sync conflict metadata |
| **V092** | Support RAG 768-d embeddings + tickets |
| **V093** | Training sandbox bindings |
| **V094**–**V095** | Hybrid FTS + hierarchical RAG metadata |
| **V096** | Invoice document URL |
| **V097** | Enterprise feature matrix |
| **V098**–**V099** | RBAC permission matrix + seed |
| **V100** | Tenant business automations |
| **V101** | RTV + supplier chargebacks |
| **V102** | Dock-door scheduling |
| **V103** | Floor labor time tracking |
| **V104** | `tenant_subscriptions` (commercial entitlements) |
| **V105** | AppModule catalog expand |
| **V106** | `platform_admins` (Super Admin off `users`) |
| **V107** | Shard routing, kill-switch, rate overrides, compliance, knowledge docs, sandbox creds |
| **V108** | `platform_audit_logs` |
| **V109** | `platform_tier_definitions` |
| **V110** | Security hardening |
| **V111** | `pos_synced_receipts` (POS idempotency) + ENTERPRISE `RETAIL_POS` |
| **V112** | B2B RFQ statuses + allocation policy on sales orders |
| **V113** | `wholesale_applications` + `customers:manage` |
| **V114** | Mesh hub: `REQUESTED` status, nullable pairing FKs, `mesh_catalog_listings`, `purchase_orders.notes` |
| **V115** | Home Realm Discovery: domain TXT token + `is_verified`; SSO provider / ACS / cert / corporate CIDRs |
| **V116** | `app_owner` INSERT policy on `tenants` so Control Plane clone-sandbox / training UAT can provision rows under FORCE RLS |
| **V117** | Backfill ENTERPRISE `tenant_subscriptions` with `B2B_SHOWROOM` + `MESH_NETWORK` (BASIC/INTERMEDIATE unchanged) |
| **V118** | Retail POS roles `RETAIL_CASHIER` / `RETAIL_MANAGER` seeded per tenant (widened `roles_code_check`); grants `pos.operate` / `pos.supervise` permission keys |
| **V119** | `refresh_tokens.app_context` — POS/WMS JWT audience scoping survives refresh rotation |
| **V120** | `invitations.additional_roles` — multi-role invites assign every role on accept |
| **V121** | `roles.network_access_level` (LAN / ANY) — not POS settings; those stay in `tenant_settings.settings` JSONB |

---

## Summary for developers

1. New **tenant** business tables need `tenant_id` + FORCE RLS policies (copy a recent migration such as V083/V084). Global platform tables (`currency_rates`, `support_knowledge_*`, `platform_admins`, `platform_audit_logs`) are the rare exception — document why they skip RLS.
2. Never treat `inventory_levels` as writable truth — append to `inventory_ledger`.
3. External side effects belong in `outbox_events`, not in the request transaction.
4. Do not DELETE from `audit_log` by hand — use the archival worker + `archive_purge_audit_logs`.
5. When adding history-heavy tables, plan partition strategy early (ledger/audit are the template).
6. Prefer seed scripts (`ops/demo_seed.sql`) over Flyway INSERTs for tenant demo data under FORCE RLS.
7. Support RAG embeddings require the `vector` extension at Postgres init time; do not `CREATE EXTENSION` from Flyway as `app_owner`.

---

## Schema dictionary (quick index)

Global exceptions (no tenant RLS): `currency_rates`, `support_knowledge_*`, `platform_admins`, `platform_admin_refresh_tokens`, `platform_audit_logs`, `platform_compliance_broadcasts`, `platform_knowledge_documents`. Almost everything else is tenant-scoped.

See the domain map tables above for the living index. For column-level detail, open the Flyway file that introduced the table (`V001`…`V121`) or the matching JPA entity under `backend/invsys-core/src/main/java/com/invsys/domain/` (support entities may live under `com.invsys.support`).
