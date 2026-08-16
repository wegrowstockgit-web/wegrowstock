# Product

## Register

product

## Users

Two distinct user groups inside each tenant company:

- **Office staff (Surface A):** owners, admins, and managers working at a desk on a laptop or desktop in normal lighting. They manage products, purchase/sales orders, invoices, customers, suppliers, the cross-tenant **Mesh Network** marketplace, manufacturing, returns, and settings. Their job: keep inventory accurate, orders flowing, and money reconciled.
- **Warehouse operators (Surface B):** pickers and warehouse managers on the floor, often with a handheld scanner or tablet, in bright/variable warehouse lighting, sometimes wearing gloves. Their job: scan, pick, pack, receive, count — fast, with minimal reading.
- **Retail cashiers (POS):** store associates on a touch register (Oxxo / Walmart-style split pane). Checkout must feel instant and work with the network down; sales drain store inventory later through the WMS async delta queue. Sold as the **Retail Point of Sale (POS)** Enterprise addon.
- **B2B customers (portal):** wholesale buyers invited by the tenant, browsing a showroom catalog and placing orders. They never see internal tooling.

**Platform operators (outside any tenant UI):** Super Admins use a separate **Control Plane** app (`admin.invsys.com` / local `:3002`) for Day-2 SaaS operations — commercial tiers and module entitlements, platform billing/dunning, tenant suspend, support impersonation (15-minute WMS JWT), Copilot SOP ingest, integration kill-switch, SOC 2 audit trail, shard routing, dead-letter retry, noisy-neighbor throttling, UAT sandbox clone, and global compliance broadcasts. That surface is not part of the tenant WMS shell.

## Product Purpose

Multi-tenant Inventory / WMS / Supply-Chain B2B SaaS. It exists so small-to-mid wholesale and light-manufacturing companies can run warehouse + office from one system instead of spreadsheets plus three disconnected tools. Success: inventory counts users trust (append-only ledger, no oversells), orders that move without babysitting, payments/accounting that reconcile themselves, and — when **MESH_NETWORK** is entitled — tenants that can discover, connect, and buy from each other without leaving the WMS.

## Brand Personality

Dependable, calm, operational. The tool disappears into the task. Confidence comes from data users trust, not visual flourish. Three words: **trustworthy, fast, unfussy**.

## Anti-references

- Consumer-app playfulness (confetti, mascots, gamification) — money and stock are on the line.
- Dense legacy ERP grayness (SAP-era walls of identical gray tables with no hierarchy).
- Marketing-site gloss inside the app (hero gradients, oversized display type in the shell).

## Design Principles

1. **Two surfaces, one system.** Office (Surface A) is light, information-dense, mouse-first. Warehouse (Surface B) is dark, high-contrast, tap-first with oversized targets. The retail POS is a third vocabulary: landscape split-pane, barcode-first, massive totals. Never blend the vocabularies on one screen.
2. **State is always visible.** Every quantity shows where it came from; every async action shows loading/success/failure; empty states teach the next step.
3. **Numbers earn trust.** Tabular numerals, consistent currency formatting, deltas with direction. If a metric can't be explained, it doesn't ship.
4. **Role-shaped UI.** Users see only what their role can act on; read-only roles get read-only affordances, not disabled buttons everywhere.
5. **Keyboard and scanner first-class.** Office flows work without the mouse; warehouse flows work from a barcode scanner alone.

## Accessibility & Inclusion

- WCAG 2.1 AA: body text ≥4.5:1 contrast on both surfaces; warehouse surface targets higher contrast for variable lighting.
- Minimum 44px tap targets on Surface B.
- `prefers-reduced-motion` respected everywhere; motion conveys state only.
- All forms keyboard-navigable with visible focus rings.
