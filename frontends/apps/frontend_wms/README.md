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
```

## Stack

- React 19 + TypeScript + Vite
- Tailwind design tokens (Office + Warehouse themes)
- Zustand + TanStack Query (IndexedDB persist)
- Optional `modules/chatbot` + `modules/training` via resolve scripts
- Playwright e2e under `e2e/`

## Not in this app

Control-plane Super Admin UI lives in `../frontend_admin` and talks to `invsys-admin-api` on `:8081`.

This app has **no** admin routes. The only control-plane touch is login: `?impersonateToken=` is exchanged with `POST /api/v1/auth/impersonation/accept` (15-minute support God Mode JWT minted by the admin API).
