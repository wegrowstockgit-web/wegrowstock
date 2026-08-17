# frontend_wms — Tenant WMS (data plane)

React 19 + TypeScript + Vite SPA for warehouse / office / showroom operations.

Part of the `frontends/` pnpm workspace. Prefer workspace commands from `frontends/`:

```bash
cd ../../   # → frontends/
pnpm --filter frontend_wms install   # or: pnpm install at workspace root
pnpm --filter frontend_wms dev       # http://localhost:5173 → API :8080
pnpm --filter frontend_wms build
pnpm --filter frontend_wms test
pnpm --filter frontend_wms test:e2e  # needs docker stack on :3000
```

Standalone (this directory only, npm):

```bash
npm ci
npm run dev
npm run build
```

## Environment

```
VITE_API_URL=          # leave empty to use same-origin / Vite proxy
VITE_ENABLE_CHATBOT=true
VITE_ENABLE_MESH=true  # omit or false to drop /mesh-network at build time
```

## Stack

- React 19 + TypeScript + Vite
- Tailwind design tokens (Office + Warehouse themes)
- Zustand + TanStack Query (IndexedDB persist)
- Optional `modules/chatbot` + `modules/training` via resolve scripts
- Playwright e2e under `e2e/`

## Not in this app

Control-plane Super Admin UI lives in `../frontend_admin` and talks to `invsys-admin-api` on `:8081`. Retail POS lives in `../frontend_pos` (`:3003` / Vite `:5175`) and syncs through the same data-plane API. When the tenant has `RETAIL_POS`, OWNER/ADMIN see **Settings → Retail POS** (`PosSettingsPanel`, `/settings?tab=retailPos`) for receipt header/footer, USD/MXN, CFDI 4.0, and blind closeout (`PATCH /api/v1/settings`). Playwright: `e2e/journeys/65-retail-pos-settings.spec.ts`.

This app has **no** admin routes. The only control-plane touch is login: `?impersonateToken=` is exchanged with `POST /api/v1/auth/impersonation/accept` (15-minute support God Mode JWT minted by the admin API).
