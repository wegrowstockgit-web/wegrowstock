# InventorySystem Frontends (pnpm workspace)

Decoupled **Control Plane** / **Data Plane** UIs under this pnpm workspace.

| Package | Role | Local | Production host |
|---------|------|-------|-----------------|
| `apps/frontend_wms` | Tenant WMS (data plane) | `:3000` / Vite `:5173` | `app.invsys.com` |
| `apps/frontend_admin` | Super Admin portal (tenants, billing, RAG, kill-switch, audit, shards, DLQ, telemetry, compliance) | `:3002` / Vite `:5174` | `admin.invsys.com` |
| `apps/frontend_pos` | Offline-first retail POS register | `:3003` / Vite `:5175` | store / kiosk |
| `packages/shared-types` | `AppModule`, `CommercialTier`, `ControlPlaneTenant` | — | — |
| `packages/shared-ui` | Shared Button / Table / Modal / Drawer / Input | — | — |

```bash
cd frontends
pnpm install
pnpm --filter frontend_wms dev      # proxies /api → :8080
pnpm --filter frontend_admin dev    # proxies /api → :8081
pnpm --filter frontend_pos dev      # proxies /api → :8080
pnpm --filter frontend_wms build
pnpm --filter frontend_admin build
pnpm --filter frontend_pos build
pnpm --filter frontend_wms test
pnpm --filter frontend_admin test
pnpm --filter frontend_pos test:coverage
```

**Hard rule:** `frontend_wms` production bundles must not contain control-plane strings (`TenantEntitlementsDrawer`, `/control-plane`, etc.). CI enforces this in `.github/workflows/ci-frontends.yml`.

See also: root `README.md`, `DEVELOPER_ARCHITECTURE.md` §2–3.
