# User Guide — Getting Started & Migrating to InventorySystem

This guide is for **business owners, warehouse managers, and office staff** who are new to the product or moving from spreadsheets / another ERP. It explains concepts in everyday language and walks through setup, migration, and daily work.

Technical deep-dives live in `DATABASE_GUIDE.md`, `DEVELOPER_ARCHITECTURE.md`, and `SEQUENCE_FLOW.md`.

---

## What you are looking at

InventorySystem is a **multi-tenant warehouse + light ERP** app. Your company is a private **tenant** (workspace). Nobody from another company can see your products, stock, or orders.

You will use three kinds of screens:

| Surface | Who | Typical device |
|---------|-----|----------------|
| **Office** | Owners, admins, managers, viewers | Laptop / desktop browser |
| **Warehouse floor** | Pickers, warehouse managers on the dock | Phone, tablet, or rugged scanner |
| **Retail POS** | Store cashiers (Enterprise addon) | Touch register — `http://localhost:3003`. Language follows **Organization → Company preferences** (English / Español / Français). Receipt header/footer, default register currency (USD or MXN), Mexican CFDI 4.0, and blind shift closeout are set by an Owner or Admin under **Organization → Retail POS** (`/settings?tab=retailPos`). That tab appears only when the workspace has the Retail POS addon. If the addon is off, the register stays locked. |
| **B2B showroom** | Your wholesale customers | Browser (catalog + checkout only) |
| **Super Admin portal** | InvSys platform operators only | Laptop — `http://localhost:3002` (`admin.invsys.com`) |

Pickers are steered to floor screens. Office users get the full desktop navigation. B2B logins only see the showroom. Tenant staff never see the Super Admin portal.

---

## Words you will see every day

| Term | Meaning |
|------|---------|
| **Tenant / workspace** | Your company account |
| **SKU** | One sellable item identity (e.g. blue medium shirt ≠ red medium shirt) |
| **Barcode / GTIN** | What the scanner reads on the pack |
| **Location / bin** | Exact shelf spot, often written as a path like `WH-01/Z-A/A-1/B-01` |
| **On hand** | Physical quantity at a location |
| **Allocated** | Reserved for an open customer order (not free to sell again) |
| **Available (ATP)** | Roughly on hand minus allocated |
| **Lot / serial** | Batch or unit identity for expiry, recall, or regulated goods |
| **LPN (license plate)** | Pallet or tote ID used to move many units as one |
| **Wave** | A batch of pick work released to the floor |
| **Ledger** | Permanent history of every stock movement (like a bank statement) |

---

## Office navigation (nested sidebar)

The left rail groups related screens. Click a **category** to expand it, then open a page:

| Category | Typical pages |
|----------|----------------|
| *(top)* | **Dashboard** |
| **Inbound** | Purchase Orders, Suppliers, Mesh Network, Returns |
| **Outbound** | Sales Orders, Customers, Invoices, Fulfillment |
| **Inventory** | Products, Replenishments, Cycle counts, Exceptions, Lot Trace |
| **Manufacturing** | BOMs, Production Orders |
| **Field** | Issue Supplies, Technician Truck |
| **Admin** | Reports, RTLS map, Organization (settings) |
| *(footer)* | **Profile** |

What you see depends on your **role**. Pickers do not see commercial Inbound/Outbound lists. Import is **not** a sidebar item — open it from the **Products** page (**Import** button) or go to `/import` if your role allows.

On phones/tablets, use **Open navigation** to show the same rail as a drawer.

---

## First-time setup (new company)

### 1. Create the workspace

1. Open the tenant WMS (local demo: `http://localhost:3000`). Super Admin portal is separate: `http://localhost:3002` (`owner@demo.test` / `password123` in `platform_admins`).
2. Go to **Sign up** (`/signup`).
3. Enter company name, workspace slug, your email, password, and display name.
4. Choose **Create workspace**. You land on the **Dashboard** as **OWNER**.

Demo tenants (if seeded): `owner@demo.test` / `password123` (and other role emails in the README).

### 2. Sign-in options later

- **Identifier-first:** enter your work email, then **Continue**. The system looks up your company from a verified domain or the warehouse network (corporate CIDR).
- Email + password when SSO is not enforced (slug is inferred from email for demo).
- **Magic link** login when enabled (rate-limited at the gateway for safety).
- Enterprise **SSO** (SAML/OIDC/Google Workspace) when your admin configured it under **Settings → Security**. If they **Enforce SSO**, you never see a password field.

### 3. Invite your team

1. Open **Admin → Organization** (or `/settings`) → **Users & invitations**.
2. **Invite user** → email + **one or more roles** (checkboxes — roles are additive; the person gets the combined access):

| Role | Typical access |
|------|----------------|
| **OWNER** | Everything, including billing / fintech |
| **ADMIN** | Full ops + users (billing may be limited) |
| **WAREHOUSE_MANAGER** | Orders, counts, receiving rules, floor oversight |
| **PICKER** | Floor scan flows (fulfillment, receive, counts) |
| **VIEWER** | Read-only office views |
| **B2B_CUSTOMER** | Showroom only |
| **RETAIL_CASHIER** | Retail POS register (with the POS addon) |
| **RETAIL_MANAGER** | Retail POS supervision — approves voids/overrides |

3. For floor staff, assign **which warehouses** they may use (location-based access). Someone checked only for Warehouse A cannot change Warehouse B stock.
4. Invitee opens the email link (`/invite/...`), sets a password, and joins with **all** the roles you picked. You can change a person's roles later from the same Users list — each role shows as its own badge.

> Even when someone holds both office and register roles (e.g. Warehouse Manager + Retail Cashier), each sign-in is locked to the app it started in: a POS register session can't call office APIs and vice versa.

### 3a. Configure Retail POS (addon)

If your plan includes **Retail POS**, an Owner or Admin opens **Admin → Organization → Retail POS** and sets:

- **Localization & Compliance** — default currency (USD or MXN) and **Enable CFDI 4.0 Facturación (Mexico)**
- **Receipt Configuration** — header (store name, address, tax ID) and footer (return policy, thank-you)
- **Security & Loss Prevention** — **Require Blind Closeout at Shift End** (cashiers count the drawer without seeing the expected total)

Warehouse managers and other floor roles cannot open Organization settings. Tenants without the addon never see the tab.

### 4. Secure the scanners (PIN)

**Surface B / handheld only.** Roles with floor access (`OWNER`, `ADMIN`, `WAREHOUSE_MANAGER`, `PICKER`) set a **4-digit shift PIN** the first time they open a floor route (fulfillment, inbound receive, cycle counts, issue supplies, etc.). Office screens (`/dashboard`, settings, reports) and **VIEWER** / **B2B_CUSTOMER** sessions never prompt for it.

After idle time on a floor route the screen locks (“Scanner locked”). Unlock with the PIN. Too many wrong attempts wipe the device crypto key and force re-login. Demo / E2E convention: PIN **1234** (per browser profile in IndexedDB — not the login password).

---

## Migrating from another system

You do **not** need to retype thousands of SKUs. Use the **Import** wizard.

### Export from the old system

Prepare clean **CSV** files:

1. **Catalog** — SKU, name/description, barcode, optional cost/price/UoM.
2. **On-hand stock** — SKU, quantity, location path (e.g. `WH-01/ZoneA/Bin01`).
3. **Partners** — suppliers and/or customers (name, email, terms if you have them).

Tip: one concern per file. Fix duplicates and blank SKUs in the spreadsheet first.

### Import into InventorySystem

1. Sign in as **OWNER**, **ADMIN**, or **WAREHOUSE_MANAGER**.
2. Open **Inventory → Products**, then click **Import** in the page header  
   (or go to `/import` / Settings → Import if your workspace still exposes the legacy entry).
3. Choose **CSV import**.
4. Upload a file → **map columns** (your `ProductCode` → our `SKU`, etc.).
5. Run **Preflight**. Bad rows are flagged; good rows can still import.
6. **Confirm import**. Products get profiles; opening stock creates proper **ledger** history (not a silent overwrite).
7. Repeat for partners and bin counts.

### Migration checklist

- [ ] Warehouse locations exist (or import creates the paths you use)
- [ ] Catalog imported; spot-check barcodes with a scanner
- [ ] Opening stock matches a physical cycle count on a sample aisle
- [ ] Suppliers + key customers present
- [ ] Team invited with correct roles and warehouse access
- [ ] (Optional) Shopify / accounting / carriers connected under **Integrations**
- [ ] (Optional) B2B customers invited to the showroom
- [ ] First live PO receive and first SO pick completed as a dry run

### After go-live

- Prefer **adjustments** and **cycle counts** over editing history.
- Keep imports for bulk corrections; day-to-day receiving should go through **Purchase Orders** or **Inbound receive**.
- Compliance archives of old audit activity may move to cold storage after ~90 days — owners can download archives from Settings → Operations when needed.

---

## Products grid (office)

Open **Inventory → Products**. The catalog is a high-performance grid (virtualized rows) with tools above the table:

| Control | What it does |
|---------|----------------|
| **Search** | Filter by SKU or name (typing stays snappy while results catch up) |
| **Saved views** | Quick filters such as All / Low stock |
| **Columns** | Show/hide fields; pin identifiers; presets below |
| **Density** | **Compact** / **Cozy** / **Spacious** row height |

### Columns: Show all vs Ops only

Inside **Columns**:

- **Show all** — turns on every optional field (Weight, L×W×H, HS code, Origin, Hazmat, Temp, Fragile, ABC, Lifecycle, …). The table grows wider; use **horizontal scroll** under the frozen SKU/Name columns.
- **Ops only** — back to daily pillars: SKU, Name, Barcode, On hand, Allocated, ATP, Reorder (plus UoM / Channel sync when those features apply).

Pinned columns (usually **SKU** and **Name**) stay visible when you scroll sideways. Your choices are remembered in this browser.

### Phones and tablets

| Device | Layout |
|--------|--------|
| **Phone** | Product **cards** (SKU/Name, location chip, On hand / Allocated / ATP) — not the wide table |
| **Tablet** | Table kept, but compliance/master columns are shed so stock numbers stay readable |
| **Desktop** | Full virtualized table with sticky identifiers |

---

## Daily office flow

### Buy stock (purchase order)

1. **Inbound → Purchase Orders → New PO** (or **Create**).
2. Choose supplier, warehouse, lines (SKU + qty) → submit.
3. When the truck is on the way, mark **In transit** if you use that status.
4. If the supplier is a **connected Mesh partner**, confirming the PO also drafts their sales order automatically (you will see a note like “Linked to Mesh Partner Sales Order #SO-…”).
5. When freight arrives, use **Floor receive** / open **Inbound receive** on a scanner so putaway posts to the ledger.

### Sell stock (sales order)

1. Orders appear under **Outbound → Sales Orders** (manual entry or channel sync such as Shopify).
2. **Allocate** reserves stock (FEFO/lot rules apply when lots matter). Short stock may show **Backordered** until inbound arrives (including **cross-dock** to staging).
3. **Generate wave** to release pick work to the floor.
4. After pack/ship, office updates shipment / tracking; invoices can follow under **Invoices**.

### Other office jobs

| Area | Use it for |
|------|------------|
| **Products** | Catalog, images, reorder points, columns/density, Import |
| **Customers / Suppliers** | Master data |
| **Mesh Network** | Discover other tenants’ published products, request/approve connections, publish your wholesale list (`MESH_NETWORK`) |
| **Dashboard — Smart sourcing** | Low-stock SKUs a connected partner already sells — **Draft PO** opens a prefilled purchase order |
| **Exceptions** | Resolve “skip & flag” issues from the floor |
| **Manufacturing** | BOMs and production orders |
| **Returns** | Approve RMAs; floor receives dispositions |
| **Reports** | Profit, COGS, inventory analytics (**Admin**) |
| **RTLS map** | Live tag map when enabled (**Admin**) |
| **Organization / Settings** | Warehouses, inventory rules, users, integrations, documents, SSO, and **Retail POS** (addon: receipts, USD/MXN, CFDI, blind closeout) |

### Guided tour & support assistant (optional)

Some deployments include an interactive **onboarding tour** and a blue **support assistant** button (bottom-right). When present:

- The multi-page **receiving → allocation** path walks Purchase Orders → Inbound receive → Sales Orders so the digital loop matches the dock. You can dismiss it or choose not to show again.
- The assistant answers role-aware “what next?” questions from the product manuals (and may suggest safe next actions for managers to approve).

The header **Page info** control (ℹ) always shows the playbook for the current screen — that help content stays available even when the chat assistant / training features are turned off.

If your site was built **without** the support chat module (or an admin turned it off), you will not see the blue chat button or the guided tour. Receiving, picking, shipping, office screens, and the ℹ page-info panels still work the same — ask your implementer if you expected the assistant and it is missing.

---

## Daily warehouse floor flow

Floor routes use a **warehouse shell** (large tap targets, high contrast) — not the office sidebar.

### Inbound receive & putaway

1. Open **Inbound receive** (or fulfillment receive mode).
2. Scan PO / paperwork, then product barcodes (lots/expiry when required).
3. Follow suggested putaway path; scan the **bin** to confirm.
4. Stock is written to the ledger immediately — that is what unlocks ATP for allocation and the B2B portal.

### Picking & staging

1. Open **Fulfillment**, accept the assigned wave.
2. Follow the guided path (may include a mini map / wayfinding).
3. Scan each SKU; wrong scans are blocked with a clear error.
4. Deliver to staging; pack/ship may finish on office or ship station flows.
5. Optional: **LPN / Build pallet** to move or ship a whole pallet ID.

### Counts, returns, replenishment

- **Cycle counts** — blind count a bin; large variances can escalate.
- **Returns receive** — scan RMA lines into restock or scrap.
- **Replenishments** — move reserve stock to pick faces when triggered.

Offline: if the handheld loses Wi‑Fi, scans can queue and sync later. Conflicts that cannot auto-apply appear for office review on the dashboard. The header **Connected** badge appears on warehouse/device views when the network is healthy.

---

## Super Admin portal (InvSys operators only)

This is **not** a tenant screen. Platform operators sign in at `http://localhost:3002` (`admin.invsys.com`) with a `platform_admins` account (demo: `owner@demo.test` / `password123`).

Typical jobs:

| Job | Where |
|-----|--------|
| Change a tenant’s commercial tier or modules | **Tenants** drawer |
| Open the tenant WMS as that company (support) | **Impersonate** — new tab, 15-minute session |
| Stop a non-paying tenant immediately | **Suspend** — WMS APIs return 403 until reactivated |
| Provision a scrubbed UAT copy | **Provision sandbox** |
| Review MRR / card status | **Platform Billing** |
| Upload Copilot SOP markdown | **Copilot Knowledge** |
| Pause a noisy integration | **Webhooks & Integrations** kill-switch |
| See who changed what | **Audit Trail** |
| Retry failed outbound jobs | **Dead Letter Queue** |
| Throttle a noisy tenant | **Concurrency** sliders |
| Push tax / hazmat rule updates | **Global Compliance** |

Tenant staff never see these menus. If your WMS login opened from Impersonate, you are in a short-lived support session — log out when finished.

---

## B2B showroom (your customers)

Wholesale buyers invited as **B2B_CUSTOMER**:

1. Sign in → **Catalog** (tier pricing applied).
2. Add to cart → checkout / orders / billing screens under `/showroom/...`.
3. They never see your internal PO, ledger, or settings screens.

---

## Mesh Network (buy and sell with other tenants)

When your plan includes **MESH_NETWORK** (Demo Corp does), Owners and Admins can open **Inbound → Mesh Network**:

1. **Discover** — browse products other companies published. You see name, image, and seller — not their price or stock. **Request Connection** starts a handshake.
2. **My Network** — outgoing requests show **REQUESTED**. Incoming requests show **PENDING**; **Approve** creates a Supplier on your partner’s books and a Customer on yours, then marks the link **CONNECTED**.
3. **Shared Catalog** — toggle **Publish to Network** on your own SKUs and set a **Mesh Wholesale Price** (visible only after you are connected and mapping catalogs).
4. After you are connected, Settings → **Partner Catalog** still maps your SKUs to theirs so a submitted/confirmed PO can become their sales order.
5. On the **Dashboard**, **Smart sourcing** appears when you are below a bin reorder point and a connected partner publishes that same SKU or barcode. **Draft PO** jumps to `/purchase-orders/new?meshPartnerSku=…`.

Pickers and Viewers do not see this hub.

---

## Safety & trust habits

1. **Do not “fix” stock by editing a number in a spreadsheet and re-importing casually** — use receive, adjust, transfer, or approved cycle counts so history stays honest.
2. **Double-submit protection** — fulfillment scans send an idempotency key; clicking twice should not ship twice.
3. **Warehouse context** — always confirm the active warehouse in the header before scanning.
4. **PIN lock** — never share shift PINs; treat the handheld like a shared register.
5. **Roles** — give pickers picker access only; keep billing on OWNER.

---

## Quick troubleshooting

| Symptom | What to try |
|---------|-------------|
| “Scanner locked” | Enter the 4-digit shift PIN |
| Cannot see a warehouse | Ask admin to add you on **User warehouses** |
| Cannot find Import in the sidebar | Use **Products → Import** (or `/import`) |
| Missing product columns | **Columns → Show all**; scroll horizontally under sticky SKU/Name |
| Table too wide / hard to read | **Columns → Ops only** or Compact density |
| Import row failed | Open preflight errors; fix SKU/barcode/location; re-upload |
| Order stuck backordered | Check inbound / cross-dock staging; allocate again when stock arrives |
| Channel / accounting sync failed | Settings → Integrations; check sync logs; ask an admin |
| Offline scan conflict | Dashboard / offline conflicts → dismiss or retry after fixing stock |

---

## Where to get help next

- In-app **support assistant** (chat FAB), when your deployment includes it — role-aware answers from product manuals  
- Your **warehouse manager / admin** — if the chat button is missing, the optional support module may be turned off  
- **README.md** — install, demo users, URLs  
- **DATABASE_GUIDE.md** — why the ledger and tenancy work this way  
- **DEVELOPER_ARCHITECTURE.md** / **SEQUENCE_FLOW.md** — for IT / implementers integrating or extending the product  

Welcome aboard — start with one warehouse, one SKU path (receive → allocate → pick → ship), then scale imports and channels once that loop feels solid.
