# frontend_admin — Super Admin Control Plane

Standalone Vite + React 19 app for platform operators (`admin.invsys.com`).

```bash
cd ../../   # → frontends/
pnpm --filter frontend_admin dev     # http://localhost:5174 → Admin API :8081
pnpm --filter frontend_admin build
pnpm --filter frontend_admin test
pnpm --filter frontend_admin test:e2e
```

## Demo credentials & tiers

| | |
|--|--|
| **Super Admin** | `owner@demo.test` / `password123` |
| **UI** | http://localhost:3002 (Docker) or http://localhost:5174 (dev) |
| **API** | http://localhost:8081 — cookies `invsys_admin_*` + CSRF `XSRF-TOKEN` |

Commercial tenants seeded for entitlement testing:

| Tenant | Tier | Modules |
|--------|------|---------|
| Demo Corp | ENTERPRISE | CORE, B2B_SHOWROOM, FINTECH, AI_COPILOT |
| Acme Wholesale | BASIC | CORE |
| Northwind Logistics | INTERMEDIATE | CORE, SHOPIFY, ADVANCED_FULFILLMENT |

Platform admins are stored in `platform_admins`, not `users`. The WMS owner with the same email is a separate tenant user.

## Features

| Route | Screen | What it does |
|-------|--------|----------------|
| `/` | Tenant manager | Tier + module entitlements; **Impersonate**, **Suspend**, **Provision sandbox** |
| `/billing` | Platform Billing | Estimated MRR, card/dunning status, usage limits |
| `/copilot/knowledge` | Copilot Knowledge | Drag-and-drop `.md` SOP ingest into global PGVector |
| `/integrations` | Webhooks & Integrations | Per-tenant outbox traffic + integration **kill-switch** |
| `/audit` | Audit Trail | Super Admin mutations (`platform_audit_logs`) |
| `/shards` | Shard Routing | Tenant → shard / Aurora cluster dictionary |
| `/operations/dlq` | Dead Letter Queue | Failed `outbox_events` grouped by tenant; inspect + retry |
| `/telemetry` | Concurrency | Latency/thread placeholders + rate-limit multiplier sliders |
| `/compliance` | Global Compliance | Broadcast tax/hazmat/regulatory updates |
| `/reports/commercial` | Commercial Reports | Tier distribution, module adoption, GMV |
| `/reports/health` | Health Reports | Webhook failures, rate limits, ledger growth |

Impersonate opens `VITE_WMS_APP_URL` (default `http://localhost:3000`) at `/login?impersonateToken=…`. The WMS login page exchanges that token via `POST /api/v1/auth/impersonation/accept`. Shared `ErrorBoundary` / `ToastProvider` / `PageSkeleton` come from `@invsys/shared-ui`.

Uses `@invsys/shared-types` and `@invsys/shared-ui`. Does not include warehouse floor UX.
