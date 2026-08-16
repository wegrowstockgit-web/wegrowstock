# frontend_pos — Offline-first Retail POS

Standalone Vite + React PWA for store registers (`:3003` / Vite `:5175`).

```bash
cd ../../   # → frontends/
pnpm --filter frontend_pos dev
pnpm --filter frontend_pos test
pnpm --filter frontend_pos test:coverage
pnpm --filter frontend_pos test:e2e
pnpm --filter frontend_pos build
```

| | |
|--|--|
| **UI** | http://localhost:3003 (Docker) or http://localhost:5175 (dev) |
| **API** | Data-plane `POST /api/v1/pos/sync-receipts` via gateway `:8080` |
| **Module** | Commercial addon `RETAIL_POS` (Enterprise). `GET /api/v1/pos/session` returns `posEnabled=false` when the tenant/tier does not include it. |
| **Demo login** | `owner@demo.test` / `password123` (optional — checkout is local) |
| **Language** | English / Español / Français from WMS **Settings → Profile → Workspace language**, then the cashier profile, then the browser locale where the register is opened |
| **Currency** | WMS **base currency** is authoritative for tenders. The register also detects local currency/tax from the browser locale and timezone, and shows a hint if they differ |

Checkout writes Dexie `outbox_receipts` immediately and never waits on the network. A background worker flushes the outbox when `navigator.onLine` is true. Language and currency from WMS are applied only when Retail POS is enabled for that tenant.
