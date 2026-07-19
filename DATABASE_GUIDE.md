# WMS Database Guide

A plain-language map of how InventorySystem stores warehouse data in PostgreSQL 16.

**Who this is for:** new developers, product owners, and anyone who needs to know *what* tables exist and *why* — without reading every Flyway migration.

**Companion docs:** `DEVELOPER_ARCHITECTURE.md` (how the app uses this schema), `USER_GUIDE.md` (day-to-day product use), `README.md` (run the stack).

Schema is owned by Flyway (`backend/src/main/resources/db/migration/`). Current head includes **V088**. Hibernate runs with `ddl-auto: validate` — never invent columns only in JPA.

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
| `tenants` | Company workspace |
| `tenant_settings` | Currency, negative-stock rules, barcode masks, density prefs, … |
| `tenant_domains` | Verified domains (CORS / email) |
| `tenant_sso_configurations` | SAML / OIDC enterprise login |
| `users` | People (email, password hash, profile fields) |
| `roles` / `user_roles` | `OWNER`, `ADMIN`, `WAREHOUSE_MANAGER`, `PICKER`, `VIEWER`, `B2B_CUSTOMER`, … |
| `user_warehouses` | **LBAC** — which warehouses a user may operate |
| `invitations` | Time-limited invite hashes |
| `refresh_tokens` | Rotating session refresh |
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
| `purchase_orders` / `purchase_order_lines` | PO lifecycle + qty received |
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
| `tenant_mesh_partners` | Cross-tenant PO ↔ SO bridge |

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

## Summary for developers

1. New business tables need `tenant_id` + RLS policies (copy a recent migration such as V083/V084).
2. Never treat `inventory_levels` as writable truth — append to `inventory_ledger`.
3. External side effects belong in `outbox_events`, not in the request transaction.
4. Do not DELETE from `audit_log` by hand — use the archival worker + `archive_purge_audit_logs`.
5. When adding history-heavy tables, plan partition strategy early (ledger/audit are the template).
6. Prefer seed scripts over Flyway INSERTs for tenant demo data under FORCE RLS.

---

## Schema dictionary (quick index)

Global exception: `currency_rates` is not tenant-RLS’d. Almost everything else is.

See the domain map tables above for the living index. For column-level detail, open the Flyway file that introduced the table (`V001`…`V088`) or the matching JPA entity under `backend/src/main/java/com/invsys/domain/`.
