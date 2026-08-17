# User Manual — weGrowStock (InventorySystem)

**Who this is for:** Anyone using the product who is **not** a warehouse specialist — office staff, new hires, viewers, cashiers, wholesale buyers, and suppliers. You do not need warehouse experience to follow this guide.

If you already know warehouse operations, see [`USER_GUIDE.md`](USER_GUIDE.md) for setup, migration, and floor workflows.

---

## 1. What this software is (in everyday words)

Imagine a company that **buys** goods, **stores** them, and **sells** them.

Without software, people track that in spreadsheets, emails, and memory. That breaks when:

- two people sell the same last box
- nobody knows what arrived on the truck
- a customer asks “where is my order?”
- the store register sold something the warehouse still thinks is on the shelf

**weGrowStock** is one shared system for that whole loop. Your company has a private workspace. Other companies cannot see your products, customers, or orders.

You do **not** have to work in a warehouse to use it. Many people only ever:

- look up a product
- enter a customer order
- send an invoice
- ring a sale at a store register
- shop the wholesale catalog
- check whether an order has shipped

The warehouse team uses a different, simpler scanning screen. This manual focuses on **you** — the everyday user.

---

## 2. The big picture: goods in, goods out

Think of a grocery store.

| Real life | In this system |
|-----------|----------------|
| You order more cereal from the brand | **Purchase order** (a buy from a supplier) |
| The delivery truck arrives | **Receive** (someone checks the delivery in) |
| Boxes sit on the back-room shelf | **On hand** (how many you physically have) |
| A customer orders 12 cases | **Sales order** (a customer buy) |
| Staff reserve those 12 so nobody else sells them | **Allocated** (reserved, not free to sell again) |
| Staff walk the aisle, grab the cases, pack them | **Fulfillment / picking** (warehouse teammates) |
| The order leaves and you bill the customer | **Shipped** + **Invoice** |

You only need to remember this sequence:

**Buy from supplier → goods arrive → sell to customer → ship → invoice.**

Everything else (bins, barcodes, lots, pallets) is extra detail for people who handle the physical boxes. Skip it until you need it.

---

## 3. Which screen am I supposed to use?

The product is several apps. Use the one that matches your job.

| If you are… | You use | What you see |
|-------------|---------|--------------|
| Office staff, manager, owner, or viewer | **Office** (browser on a laptop) | Left menu: Dashboard, products, orders, customers, invoices |
| Store cashier or store supervisor | **Retail register** (touch screen) | Split-screen checkout: products on one side, cart and pay on the other |
| Wholesale customer of this company | **Wholesale portal** (Showroom) | Catalog, cart, orders, billing — no warehouse tools |
| Supplier sending goods to this company | **Supplier portal** | The purchase orders they sent you, and delivery dates |
| Warehouse picker (not this manual’s focus) | **Floor / scanner** | Large buttons, barcode scanning, almost no menus |

If a page looks nothing like what this manual describes, you are probably in the wrong app. Sign out and open the link your admin sent you.

---

## 4. Words you will see (plain English)

You will see short labels. They are not secret warehouse codes. Here is what they mean.

| Word on screen | What it actually means |
|----------------|------------------------|
| **SKU** | The product’s ID. “Blue medium shirt” and “red medium shirt” are two different SKUs. |
| **Barcode** | The stripes on the pack. A scanner reads this instead of typing. |
| **On hand** | How many are physically here right now. |
| **Allocated** | Already promised to an open customer order. Do not sell them twice. |
| **Available** (sometimes **ATP**) | Roughly: on hand minus allocated. This is what you can still promise a customer. |
| **Purchase order (PO)** | “We are buying this from a supplier.” |
| **Sales order (SO)** | “A customer is buying this from us.” |
| **Draft** | Started, not finished. Safe to edit. |
| **Confirmed** | The order is real. Next step is reserving stock. |
| **Allocated** (on an order) | Stock is reserved for this order. Ready for the warehouse to pick. |
| **Backordered** | The customer wants it, but you do not have enough free stock yet. |
| **Shipped** | It left the building. |
| **Invoice** | The bill you send the customer. |
| **Supplier** | Who you buy from. |
| **Customer** | Who you sell to. |
| **Warehouse** | A building / storage site. Some companies have more than one. Always check which one is selected at the top of the screen. |
| **Location / bin** | An exact shelf spot. You can ignore this unless you work with physical stock. |
| **Lot / serial** | A batch or unit ID (useful for expiry or recalls). Ignore unless your products need it. |
| **Workspace / tenant** | Your company account. |

If you forget a term, click the **Page info** (ℹ) button in the header. It explains the page you are on in ordinary language.

---

## 5. Sign in

1. Open the address your company gave you (local demo: `http://localhost:3000`).
2. Enter your **work email**.
3. Click **Continue**.
4. Enter your **password**, or follow **Sign in with SSO** / **Email magic link** if those buttons appear.

You never type a “warehouse code” at login. The system finds your company from your email (or from company single sign-on).

### First time

Someone with Owner or Admin access invites you. You get an email with a link (`/invite/...`). Open it, set a password, and you are in.

### If sign-in fails

- Check caps lock and that you used your work email, not a personal one.
- Ask an admin to confirm you were invited and that your account is active.
- If the company **enforces SSO**, there will be no password field — use the company sign-in button.

### Language

English, Español, and Français are available. Open **Profile** (footer of the left menu) and set **Language**.

---

## 6. Finding your way around (office)

After sign-in, office users land on the **Dashboard**.

### Left menu

Click a **category** to expand it, then click a page:

| Menu | Everyday meaning | Typical pages |
|------|------------------|---------------|
| **Dashboard** | Today’s snapshot | Numbers and “needs attention” cards |
| **Inbound** | Buying / goods coming in | Purchase Orders, Suppliers, Returns |
| **Outbound** | Selling / goods going out | Sales Orders, Customers, Invoices |
| **Inventory** | What you stock | Products (and some extra tools for managers) |
| **Manufacturing** | If you assemble products | Recipes (BOMs) and production orders |
| **Field** | If technicians take stock on trucks | Issue supplies, technician truck |
| **Admin** | Company setup | Reports, Organization (owners/admins) |
| **Profile** | You | Photo, password, language |

You will **not** see every item. The menu hides pages your role cannot use. That is normal.

On a phone, tap **Open navigation** to show the same menu as a drawer.

### Search

Use the search box at the top: pages, product IDs, orders, customers.

### Page info (ℹ)

On any screen, click **Page info** for:

- what this page is for
- step-by-step
- how to undo a mistake
- what the status chips mean

### Support assistant (optional)

Some companies show a blue chat button in the bottom-right. Ask it “what do I do next?” in plain language. If you do not see it, your company turned it off — use Page info or ask your admin.

---

## 7. What you can do depends on your role

Your admin assigns one or more roles. They add together (you get the combined access).

| Role | In plain English | Typical work |
|------|------------------|--------------|
| **Owner** | The account holder | Everything, including billing |
| **Admin** | Office administrator | Users, settings, orders, catalog |
| **Warehouse manager** | Operations lead | Orders, receiving rules, floor oversight |
| **Viewer** | Look, don’t change | Dashboards, products, orders (read-only) |
| **Picker** | Floor scanner user | Scan receive / pick / count — not this manual |
| **Retail cashier** | Store checkout | Ring sales on the register |
| **Retail manager** | Store supervisor | Checkout plus voids / overrides |
| **B2B customer** | Wholesale buyer | Showroom catalog and checkout only |
| **Supplier** | Vendor contact | See purchase orders and promised dates |

If a button is missing, you probably do not have that role. Ask an Owner or Admin — do not share logins.

---

## 8. Start here: the Dashboard

The Dashboard is a **read-only overview**. Clicking it does not move stock.

Glance at:

- **Stock value** — roughly what inventory is worth
- **Low stock** — products that are running out
- **Open orders** — customer orders not finished yet

Then work the cards, for example:

- **Needs allocation** — confirmed orders waiting for reserved stock
- **Ready to invoice** — orders that finance can bill

Open a card to jump to the right list. Fix the work **on that list**, not on the Dashboard.

If you are a Viewer, this may be most of what you need each day.

---

## 9. Everyday office jobs

These are the jobs most non-warehouse users actually do.

### 9.1 Look up a product

1. Open **Inventory → Products**.
2. Type part of the name or SKU in **Search**.
3. Read **On hand**, **Allocated**, and **Available**.

On a phone you see product **cards** instead of a wide table. That is the same information.

**You cannot “fix” a wrong quantity by typing over it.** Quantities change when goods are received, sold, counted, or officially adjusted. If a number looks wrong, tell a manager.

Useful table tools (desktop):

- **Columns** — show or hide fields. **Ops only** keeps the daily columns. **Show all** adds extras (weight, size, and so on).
- **Density** — Compact / Cozy / Spacious row height.
- Your layout is remembered in this browser.

### 9.2 Keep the customer list accurate

1. Open **Outbound → Customers**.
2. Add or edit name, contacts, and addresses **before** you create an order.

Wrong addresses show up on packing slips and invoices. Fix the customer record first; do not try to rewrite a shipped order’s history.

### 9.3 Take a customer order

1. Open **Outbound → Sales Orders**.
2. Click **New** / **Create**.
3. Choose the customer, the warehouse, and the product lines (SKU + quantity).
4. Save. Status starts as **Draft** — still editable.
5. Click **Confirm** when the order is real.

What happens next (often done by a manager, not by you):

6. **Allocate** reserves stock. If there is not enough, the order may show **Backordered**.
7. The warehouse team picks and packs.
8. Status moves toward **Shipped**.
9. Someone creates an **Invoice**.

You can also receive orders automatically from a web store (for example Shopify) or from the wholesale portal. They land in the **same** Sales Orders list.

**Stuck?**

- **Allocate** greyed out — order still Draft, customer on credit hold, not enough stock, or wrong warehouse selected at the top.
- **Backordered** — wait for a delivery, or ask purchasing to order more.

To stop an order that should not ship, use **Cancel** while it is still allowed. After it has shipped, use **Returns**, not “delete.”

### 9.4 Send an invoice

1. Open **Outbound → Invoices**, or use **Invoice** on a sales order that is ready.
2. Review the customer and amounts.
3. Send / record the invoice per your company’s process.

Invoices are bills. They do not move boxes. If the goods were wrong, handle a **return** as well as a credit.

### 9.5 Order stock from a supplier (purchasing)

You do not need to stand on a loading dock to do this.

1. Open **Inbound → Suppliers** and make sure the supplier exists.
2. Open **Inbound → Purchase Orders → New PO**.
3. Choose supplier, warehouse, and lines.
4. Save as **Draft**, then submit when the buy is firm.

When the truck arrives, **warehouse staff** check the goods in. After they receive, **On hand** and **Available** go up, and backordered sales can move forward.

Do not skip receiving and type stock in by hand. The receive step is what makes the numbers trustworthy.

### 9.6 Handle a customer return (office side)

1. Open **Inbound → Returns** (Owners, Admins, Warehouse managers).
2. Create or approve the return (RMA) for the original order.
3. Warehouse staff later receive the physical goods into restock or scrap.

Until the warehouse receives the return, do not assume the item is back on the shelf.

---

## 10. Wholesale buyers (Showroom)

If you were invited as a **B2B customer**, you are a buyer, not warehouse staff. After sign-in you only see the **Wholesale Portal**.

1. Open **Catalog**.
2. Add items to the cart (your prices may be wholesale / tier prices).
3. Open **Checkout**, review ship-to and quantities, and **Place order**.
4. Track status under **Orders**.
5. See bills under **Billing**.

You will never see bins, scanners, or internal purchase orders. That is intentional.

If an item shows unavailable, stock is not free to sell. Contact your sales rep. Do not ask warehouse staff for shelf codes.

To fix a cart, remove lines **before** placing the order. After placement, use **Orders** / return flows.

---

## 11. Store cashiers (Retail register)

This is a separate app (local demo: `http://localhost:3003`). It looks like a supermarket checkout: products on one side, cart and payment on the other.

Typical flow:

1. Sign in with your cashier email (not the office warehouse login, even if you have both jobs — each sign-in is for one app).
2. Unlock the register with your **4-digit PIN** if asked.
3. Scan barcodes or tap products into the cart.
4. Take payment.
5. Hand the receipt to the customer.

The sale is saved **immediately**, even if the internet drops. When the network returns, sales sync and warehouse quantities catch up.

**Voids and overrides** usually need a **Retail manager**. If a manager PIN is required, call your supervisor. Do not share PINs.

Owners/Admins set receipt header/footer, currency (USD or MXN), Mexican CFDI invoicing, and “blind closeout” (count the drawer without seeing the expected total) under office **Organization → Retail POS**. Cashiers do not change those settings.

If the register stays locked, your company may not have the Retail POS feature turned on. Ask an Owner.

---

## 12. Suppliers

If you log in as a **supplier**, you see a small portal:

- purchase orders this company sent you
- promised ship / delivery dates you can acknowledge or update

You do not manage their warehouse. Keep dates honest so their receiving dock knows what to expect.

---

## 13. Optional: what the warehouse team is doing

You can skip this section. It exists so office users are not surprised by status changes.

When a purchase order is submitted, floor staff **receive** it: scan the PO, scan each product, scan the shelf location. That is the moment stock becomes real in the system.

When a sales order is allocated, floor staff **pick** it: a list of “go here, take this.” They scan so the wrong item cannot go out quietly. Then they pack and ship.

If Wi‑Fi drops, their scanner can keep working and sync later. Rare conflicts show on the office Dashboard for a manager — not something a Viewer should “fix.”

**Shift PIN:** shared warehouse devices lock after idle time. That PIN is **not** your login password. Office screens never ask for it.

---

## 14. Simple rules that keep numbers honest

1. **Do not overwrite quantities in a spreadsheet and re-import to “fix” stock.** Use receive, return, official adjust, or a cycle count.
2. **Confirm the warehouse** in the header before you create an order. The wrong building means the wrong stock.
3. **Draft is safe; shipped is history.** Edit drafts. After ship, use cancel (if allowed), returns, or credits.
4. **Do not share passwords or register PINs.**
5. **Missing button ≠ broken app.** It usually means your role cannot do that action.
6. **Clicking twice should not ship twice.** If something looks duplicated, stop and ask a manager instead of repeating the click.

---

## 15. If something goes wrong

| What you see | What to try |
|--------------|-------------|
| “You do not have permission” | You are signed in, but your role cannot do that. Ask an Owner/Admin. |
| Session expired / please sign in | Sign in again. |
| Cannot see a warehouse’s stock | Ask an admin to grant you that warehouse. |
| Product search finds nothing | Check spelling, SKU, and that you are in the right company account. |
| Order stuck in Draft | Finish required fields, then **Confirm**. |
| Order stuck Backordered | Not enough available stock. Check Products, or wait for a purchase order to be received. |
| Allocate will not run | Confirm the order, check credit hold, check warehouse, check available qty. |
| Invoice missing | The sales order may not be far enough along (not allocated/shipped). |
| Register locked | PIN, or Retail POS not enabled for this company. |
| Wholesale catalog empty | Your account may lack catalog access, or nothing is published for your tier. Contact your rep. |
| Numbers look “a few minutes behind” | Floor scans and store sales sync in near real time, but a short delay is normal. Refresh. |
| Blue chat button missing | Optional helper is off. Use **Page info** (ℹ) or your admin. |

---

## 16. Getting help

1. **Page info (ℹ)** on the current screen — always available.
2. **Support assistant** (blue button), if your company enabled it.
3. Your **Owner / Admin** for access, invitations, and settings.
4. [`USER_GUIDE.md`](USER_GUIDE.md) — if you later need warehouse setup, imports, or floor scanning.
5. [`README.md`](README.md) — install, demo logins, and technical URLs (for IT).

---

## 17. A 10-minute practice path (office)

Use this on a training / demo workspace, not live customer data.

1. Sign in and open the **Dashboard**. Read the three headline numbers.
2. Open **Products**. Search for one item. Note On hand vs Available.
3. Open **Customers**. Pick one name you recognize.
4. Create a **Sales Order** as **Draft**, then **Cancel** it (so you learn undo safely).
5. Open **Purchase Orders** and read statuses: Draft, Submitted, Received.
6. Click **Page info** on each of those pages once.

When that feels familiar, you know enough to work as a normal office user. Warehouse scanning, manufacturing, and advanced settings can wait until someone trains you on those jobs.

Welcome. You do not need to “know warehouse management” to be useful here — you need to know which list you are on, whether the document is still a draft, and who to ask when a button is hidden.
