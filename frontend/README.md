# InventorySystem Frontend

React 19 + TypeScript + Vite frontend for the InventorySystem WMS.

## Setup

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```

## Environment

Create `.env` (optional):

```
VITE_API_URL=http://localhost:8080
```

## Stack

- React 19 + TypeScript + Vite
- Tailwind CSS with `tokens.css` design system (Office + Warehouse themes)
- Zustand (session, activeWarehouse, scanBuffer)
- TanStack Query with IndexedDB persistence (`offlineFirst`)
- TanStack Virtual for product grid
- Axios with JWT injection and single-flight token refresh
- PWA with manifest + service worker app shell
- Offline mutation queue with idempotency keys
