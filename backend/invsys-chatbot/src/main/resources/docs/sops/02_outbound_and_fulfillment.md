---
title: "Outbound Sales & Fulfillment SOP (Beginner Guide)"
slug: "sop-outbound-fulfillment"
sourcePath: "docs/sops/02_outbound_and_fulfillment.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER"]
audienceLevel: "beginner"
routeHints: ["/sales-orders", "/customers", "/invoices", "/fulfillment", "/cluster-pick", "/pallet-manifests", "/replenishments", "/dashboard", "/exceptions"]
keywords: ["sales order", "allocate", "backorder", "wave", "cluster pick", "tote", "cartonization", "pack", "ship", "shipped too early", "wrong SKU", "wrong address", "broken item", "invoice"]
---

# Outbound Sales & Fulfillment — Beginner Playbook

This guide takes a customer order from "the customer wants it" to "it left on a truck" — and explains **how to undo every common mistake** along the way. Written for people brand new to warehouses.

---

## Before you start: how an order flows (60-second picture)

1. **Sales Order created** — the promise: "Customer X gets 10 widgets."
2. **Confirm** — the order is locked in and visible to the warehouse.
3. **Allocate** — the system *reserves* real stock on the shelf for this order (oldest expiry first, so nothing goes stale — this is called FEFO).
4. **Wave released to floor** — the picking work appears on handheld scanners.
5. **Pick** — a floor worker scans bins and products into totes.
6. **Pack** — items go into cartons, get weighed, get labels (cartonization).
7. **Ship** — the carton leaves; the order chip turns **SHIPPED**.
8. **Invoice** — the customer gets billed for what actually shipped.

Status chips you will see on the Sales Orders screen: **DRAFT → CONFIRMED → ALLOCATED → (BACKORDERED) → PARTIALLY SHIPPED → SHIPPED → CLOSED / CANCELLED**.

**Who does what:**

| Role | Their part of outbound |
|---|---|
| **OWNER / ADMIN** | Everything, plus voiding invoices and overriding prices |
| **WAREHOUSE_MANAGER** | Creates/confirms orders, allocates, builds and releases waves, approves fixes |
| **PICKER** | Claims waves on the scanner, picks, packs, ships. Cannot create orders or invoices |
| **VIEWER** | Read-only |

**Remember the ledger rule:** shipped stock, picks, and invoices are permanent entries. Mistakes are fixed with *new* attributed entries (reversals, returns, credit notes) — never by deleting. When in doubt, ask the chat assistant (blue bubble, bottom-right); if it proposes an **Action Draft**, a Manager clicks **Approve** to run the fix.

---

### How to Set Up a Customer

**What is this?**
The customer record holds the bill-to and ship-to address and payment terms. Every packing slip, shipping label, and invoice copies from here — a typo here becomes a typo on the box.

**Who can do this? (Privileges Required)**
OWNER, ADMIN, or WAREHOUSE_MANAGER (the **Manage Customers** permission). VIEWER read-only. PICKERs don't see this screen.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Outbound** → **Customers**

**Step-by-Step Instructions:**
1. Open **Customers** and **search first** so you don't create a duplicate.
2. Create the customer with the exact legal name, email, ship-to address, and payment terms.
3. Double-read the address — street number, unit, ZIP. Say it out loud if it helps; wrong addresses are the #1 outbound mistake.

**⚠️ What if I make a mistake?**
- **Misspelled name or wrong address, order not shipped yet:** Fix the customer record, and check any open sales order for that customer — correct the ship-to on the order before it reaches packing.
- **Misspelled address and the box ALREADY shipped:** You cannot edit history. Act fast in the real world: contact the carrier for an address correction/intercept, and log the incident. If it bounces back, receive it as a return (RMA) so stock re-enters the ledger honestly.
- **Duplicate customer:** Point new orders at the correct record and ask an ADMIN to deactivate the duplicate. Old orders keep their history.

---

### How to Create and Confirm a Sales Order

**What is this?**
The Sales Order (SO) records what a customer wants to buy. Until you **Confirm**, it's just a draft the warehouse ignores.

**Who can do this? (Privileges Required)**
OWNER, ADMIN, or WAREHOUSE_MANAGER. Changing a price below list may require the **Override Pricing** permission. PICKERs and VIEWERs cannot create orders.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Outbound** → **Sales Orders** (shortcut: **New sales order** on the Dashboard)

**Step-by-Step Instructions:**
1. Open **Sales Orders**. Use the search box to check the customer doesn't already have an identical open order (prevents duplicates).
2. Click **New sales order**.
3. Pick the customer and warehouse.
4. Add lines: SKU, **quantity** (read it twice), and unit price.
5. Click **Create order**. Status: **DRAFT**.
6. Re-check lines, then click **Confirm**. Status: **CONFIRMED** — the warehouse can now see it.

**⚠️ What if I make a mistake?**
- **Wrong quantity or price, still DRAFT:** Just edit the line. Drafts are freely editable.
- **Wrong quantity/price, already CONFIRMED but not allocated/shipped:** Open the order and use **Cancel** (or edit if your screen allows), then recreate correctly. A cancelled order stays visible as **CANCELLED** — that's normal.
- **Duplicate order (clicked create twice):** **Cancel** the twin while nothing is allocated. If it was already allocated, un-allocate first (ask the assistant: *"Un-allocate SO-1042"* — it drafts the action for a manager to **Approve**), then cancel.
- **Wrong customer selected:** Cancel and recreate. Don't ship to the wrong customer and "fix it later."
- **Price below list is blocked:** You need someone with **Override Pricing** — that's a control, not a bug.

---

### How to Allocate Stock (reserve inventory)

**What is this?**
**Allocate** means "put a hold on real shelf stock for this order." If there isn't enough stock, the order (or part of it) shows **BACKORDERED** — an honest "we owe the customer" flag, not an error.

**Who can do this? (Privileges Required)**
OWNER, ADMIN, or WAREHOUSE_MANAGER. VIEWERs and PICKERs cannot allocate.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Outbound** → **Sales Orders** → open the order

**Step-by-Step Instructions:**
1. Open a **CONFIRMED** order.
2. Click **Allocate**. The system reserves stock, oldest-expiry-first (FEFO).
3. Watch the chip: **ALLOCATED** = fully reserved; **BACKORDERED** = short.

**⚠️ What if I make a mistake?**
- **Allocated the wrong order first (stole stock from a more urgent order):** Un-allocate this order (release the reservation via the on-screen control or an assistant Action Draft approved by a Manager), then allocate the urgent one.
- **Allocate is greyed out:** Common causes — order still **DRAFT** (Confirm it), the customer is on **Credit Hold** (an OWNER/billing person must clear the hold; do not try to force it), no stock (receive inbound first), or wrong warehouse context.

---

### How to Build, Release, and Pick a Wave

**What is this?**
A "wave" is a batch of orders bundled into one efficient walking route. The manager builds it at a desk; the picker claims it on a handheld scanner and follows the screen bin-by-bin. In **Cluster Picking**, you pick for several orders at once into separate totes on one cart — the scanner always tells you *which tote* each item goes into.

**Who can do this? (Privileges Required)**
WAREHOUSE_MANAGER builds and releases waves (**Generate draft wave**, **Release to floor**). PICKER claims and scans. VIEWERs are blocked from floor actions.

**Where to go in weGrowStock:**
🖥️ Manager: Sidebar Navigation → **Outbound** → **Fulfillment**
🖥️ Picker: **Outbound** → **Fulfillment** (or **Cluster pick** for multi-order carts) on the handheld

**Step-by-Step Instructions:**
1. *Manager:* open **Fulfillment**, click **Generate draft wave**.
2. *Manager:* optionally click **Optimize pick path** (sorts the route so pickers don't zig-zag).
3. *Manager:* click **Release to floor**.
4. *Picker:* on the scanner, click **Claim wave (device lock)** — this locks the wave to your device so two people can't fight over it.
5. *Picker:* choose the work mode the screen directs (**Single** / **Batch** / **Pack**) and make sure scan mode says **Pick**.
6. *Picker:* the screen names a bin → walk there → scan the **bin barcode** → scan the **product barcode** → confirm quantity → **Place in tote** (in batch/cluster mode it names the exact tote).
7. Repeat until the wave is done. If the screen shows **Replenishments Needed**, a pick face is empty — see Replenishments below.

**⚠️ What if I make a mistake?**
- **Picked the wrong SKU into a tote:** If you notice immediately, put it back in its bin and rescan the correct item. If you already confirmed the scan, tell your manager — do **not** just swap items between totes by hand, because the system believes what was scanned, not what your hands did. The manager corrects the pick before packing.
- **Dropped and broke an item mid-pick:** Do not scan the broken unit into the tote and do not hide it. Use **Skip & Flag Barcode** (or your site's damage flag), set the broken item aside for quarantine, and pick a replacement unit if the bin has one. A manager posts the breakage as an attributed stock correction — the ledger records that the company lost one unit, honestly.
- **Barcode won't scan / label ruined:** Tap **Skip & Flag Barcode**. **Never type digits from memory** — a wrong-but-plausible barcode is the hardest error to find later.
- **Scanner shows "Offline - Caching Scans":** Keep going carefully; scans are saved and sync later. If one conflicts (someone else touched the same bin), it parks in **Inventory → Exceptions → Sync Conflicts** for a manager to **Approve & Re-process** or **Discard Transaction**.
- **Claimed the wrong wave:** Ask the manager to release the device lock so the right picker can claim it.

---

### How to Pack and Ship (Cartonization)

**What is this?**
Packing turns picked totes into sealed, weighed, labeled cartons. The system suggests carton sizes (cartonization), verifies weight on a scale to catch missing/extra items, and prints the shipping label. **Ship** is the moment the order legally leaves — it's the step you must never click early.

**Who can do this? (Privileges Required)**
PICKER or WAREHOUSE_MANAGER at a pack station. Reopening a completed pack requires a WAREHOUSE_MANAGER.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Outbound** → **Fulfillment** → work mode **Pack**
(pallet shipments: **Outbound** → **Pallet manifests**)

**Step-by-Step Instructions:**
1. Switch the station to **Pack** mode.
2. Click **Connect packing scale** (or **Connect Bluetooth scale**).
3. Scan the tote, follow the suggested carton, and place items in.
4. Put the carton on the scale. If the weight looks wrong (too light = something missing; too heavy = extra item), **stop and recount before continuing**.
5. Click **Complete Pack**. Labels/manifest generate.
6. For pallets: **Mint New LPN** to start a pallet, stack cartons, then **Finish / new pallet**.
7. Mark shipped **only when the carton is physically on/committed to the truck**. The order chip turns **PARTIALLY SHIPPED** or **SHIPPED**.
8. Click **Disconnect** when leaving the scale.

**⚠️ What if I make a mistake?**
- **Clicked "Shipped" too early (it's still on the dock):** Tell a WAREHOUSE_MANAGER *immediately* — before invoicing runs. The ship event is a ledger entry, so it can't be deleted, but a manager can post the reversing correction and restore the order's real state. The worst thing you can do is stay quiet: an early "Shipped" triggers customer emails and invoicing on goods that haven't left.
- **Packed the wrong item (scale caught it):** Reopen the carton, fix contents, re-weigh. This is exactly why the scale step exists.
- **Completed Pack, then found a wrong item:** A manager reopens the pack. Do not tear open a sealed, system-completed carton without telling anyone.
- **Wrong shipping address on the label:** If not shipped: fix the address on the order/customer and reprint. If shipped: carrier intercept + incident log (see Customers section above).

---

### How to Handle Replenishments (empty pick face)

**What is this?**
Pick bins are small and go empty. Replenishment is a directed move that refills them from bulk/reserve shelves so waves don't stall.

**Who can do this? (Privileges Required)**
PICKER or WAREHOUSE_MANAGER.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inventory** → **Replenishments** (the Fulfillment screen links you there when it shows **Replenishments Needed**)

**Step-by-Step Instructions:**
1. Open **Replenishments**.
2. Follow the directed move: scan the **from** bin, move the stock, scan the **to** bin.
3. Go back to **Fulfillment** and continue picking.

**⚠️ What if I make a mistake?**
- **Moved stock to the wrong bin:** Stop and request a manager correction/move. Don't invent a reverse scan unless the screen offers one.
- **No tasks but the shelf is empty:** Reserve stock may be gone — the fix is inbound (receive a PO), not a workaround.

---

### How to Invoice a Shipped Order

**What is this?**
Invoicing turns "we shipped it" into "the customer owes us money." It should reflect exactly what shipped — which is why the system only lets you bill shipped progress.

**Who can do this? (Privileges Required)**
OWNER or ADMIN (per company policy, WAREHOUSE_MANAGER may also invoice). **Voiding an invoice requires the Void Invoices permission (ADMIN/OWNER).** VIEWERs can only look.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Outbound** → **Invoices** (or the **Invoice** / **Invoice remaining** buttons on the Sales Order)

**Step-by-Step Instructions:**
1. Open the shipped (or partially shipped) Sales Order.
2. Click **Invoice** (first bill) or **Invoice remaining** (bill the rest after a partial shipment).
3. Check the amounts and customer, confirm.

**⚠️ What if I make a mistake?**
- **Invoiced the wrong amount / wrong price:** An ADMIN/OWNER with **Void Invoices** voids or credits it and re-issues correctly. Money documents follow the same ledger rule as stock: correct forward, never delete. (Full detail in SOP 05.)
- **Invoiced before it really shipped (because of an early "Shipped" click):** Fix the ship status first (manager reversal, previous section), then void/credit the invoice.
- **Invoice remaining is greyed out:** Nothing left to bill, or the order hasn't shipped far enough. That's the system protecting you from billing air.

---

## Quick reference: "I messed up" cheat sheet (Outbound)

| Mistake | Can I fix it myself? | The fix |
|---|---|---|
| Typo in customer name/address, not shipped | Yes (office roles) | Edit customer + open order ship-to |
| Address wrong, already shipped | No — Manager/office | Carrier intercept; return (RMA) if it bounces |
| Wrong qty/price on DRAFT order | Yes | Edit the line |
| Wrong qty/price after Confirm | Order creators | **Cancel** + recreate (un-allocate first if needed) |
| Duplicate sales order | Order creators | Cancel the twin; ask assistant for an un-allocate **Action Draft** if reserved |
| Picked wrong SKU (confirmed) | No — Manager | Manager corrects the pick before pack |
| Broke an item during pick | Picker flags | **Skip & Flag Barcode**, quarantine, manager posts loss |
| Clicked **Shipped** too early | No — Manager | Manager posts a reversing correction; then fix any invoice |
| Wrong item found at pack (scale) | Yes at station | Reopen carton, recount, re-weigh |
| Scan parked after offline work | No — Manager | **Exceptions → Sync Conflicts** → **Approve & Re-process** / **Discard** |
| Allocate greyed out | Depends | Confirm the order / clear Credit Hold (Owner) / receive stock |

**Golden rule:** the truck and the ledger must always tell the same story. If they ever disagree — you clicked something the truck didn't do, or did something you didn't scan — say so immediately. Every fix is a new signed entry; silence is the only unfixable mistake.

**Still stuck?** Click the chat bubble and describe it plainly (e.g. *"I marked SO-1042 shipped but it's still on the dock"*). If the assistant offers an **Action Draft**, a Manager reviews and clicks **Approve** — nothing executes without a human.
