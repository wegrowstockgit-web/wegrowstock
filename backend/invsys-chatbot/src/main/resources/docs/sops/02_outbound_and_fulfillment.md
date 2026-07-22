---
title: "Outbound Sales & Fulfillment SOP"
slug: "sop-outbound-fulfillment"
sourcePath: "docs/sops/02_outbound_and_fulfillment.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER"]
routeHints: ["/sales-orders", "/customers", "/invoices", "/fulfillment", "/replenishments", "/dashboard"]
---

# Outbound Sales & Fulfillment — Operations Playbook

Cover customer orders, FEFO-minded allocation, wave release, handheld picking, packing, and shipping visibility. Use exact on-screen labels only.

---

### Sales Orders (create, confirm, allocate, invoice)

- **Target Audience & Roles:** OWNER, ADMIN, WAREHOUSE_MANAGER; VIEWER reviews; PICKER fulfills waves, does not build office sales orders.
- **Route Location:** Outbound → **Sales Orders** (also **New sales order** on Dashboard)
- **Primary Operational Goal:** Turn a customer need into a confirmed order, reserve FEFO-appropriate stock, and prepare invoicing after ship progress.

#### 1. Step-by-Step Action Plan
1. Open **Sales Orders** or click **New sales order** / **New order** on the Dashboard.
2. Enter customer, warehouse, and lines; click **Create order** / **Create sales order**.
3. While status is **DRAFT**, edit carefully, then click **Confirm** so the chip moves to **CONFIRMED**.
4. Click **Allocate** to reserve inventory (FEFO-aware lots when the product requires dating).
5. Watch chips: **ALLOCATED**, **BACKORDERED**, **PARTIALLY SHIPPED**, **SHIPPED**, **CLOSED**, **CANCELLED**.
6. Filter the grid with **All** / **Open** / **Allocated** / **Shipped** as needed.
7. When ready to bill, use **Invoice** or **Invoice remaining**.
8. To stop an order that should not ship, use **Cancel** while policy still allows it.

#### 2. Correlated Flow & Downstream Ripple Effect
- **Pickers:** After managers **Release to floor**, handheld tasks appear under **Fulfillment**.
- **Managers:** Dashboard cards such as **Needs allocation** and **Ready to invoice** update.
- **ATP:** Allocation reserves sellable stock for that order; other orders may show **BACKORDERED** if supply is short.
- **Finance / credit:** Invoicing creates receivable pressure; a customer on **Credit Hold** (proactive banner/copy) can block progress until billing clears the hold.

#### 3. Safety, Reversal & Undo Rules
- Prefer **Cancel** before the order is far into shipping.
- If reservations must be released before ship, use the on-screen release/cancel controls available on that order—never delete history.
- After ship mistakes, use returns / RMA paths and attributed corrections.
- Core rule: past stock history is permanent; fix with attributed corrections.

#### 4. Troubleshooting Common Blockers
- **Why is Allocate greyed out?** Common causes: order still **DRAFT**, customer **Credit Hold**, insufficient received stock, or wrong warehouse context.
- **Why is the order BACKORDERED?** Not enough available stock after FEFO rules—receive inbound or free other reservations.
- **VIEWER cannot Allocate?** Expected—ask a Warehouse Manager.

---

### Customers

- **Target Audience & Roles:** OWNER, ADMIN, WAREHOUSE_MANAGER; VIEWER read-only.
- **Route Location:** Outbound → **Customers**
- **Primary Operational Goal:** Keep bill-to / ship-to and commercial terms accurate before sales orders and showroom checkout.

#### 1. Step-by-Step Action Plan
1. Open **Customers**.
2. Confirm the customer record before **Create sales order**.
3. Coordinate with billing owners when credit posture changes (watch Dashboard / support banners mentioning **Credit Hold**).

#### 2. Correlated Flow & Downstream Ripple Effect
- Bad customer data breaks packing slips and invoices.
- Credit posture affects whether **Allocate** / checkout can proceed.
- Pickers see customer only as stop/ship labels on the wave—not master-data screens.

#### 3. Safety, Reversal & Undo Rules
- Correct customer details before **Confirm** when possible.
- Do not “fix” a shipped order by editing history—use returns and credit memos with finance.

#### 4. Troubleshooting Common Blockers
- **Cannot select customer on New order?** Customer may be inactive—ask Admin.
- **B2B buyer asking for warehouse bins?** Direct them to Showroom only—never share bin maps.

---

### Invoices

- **Target Audience & Roles:** OWNER, ADMIN, WAREHOUSE_MANAGER (per policy); VIEWER may review.
- **Route Location:** Outbound → **Invoices** (also invoice actions on Sales Orders)
- **Primary Operational Goal:** Turn shipped/fulfillment progress into customer billing documents.

#### 1. Step-by-Step Action Plan
1. From **Sales Orders**, click **Invoice** or **Invoice remaining**, or open **Invoices**.
2. Review amounts and customer.
3. Complete the on-screen confirmations until the order shows invoicing progress (for example **Invoiced** cues on the sales order).

#### 2. Correlated Flow & Downstream Ripple Effect
- Finance AR and Dashboard **Open AR** / **Ready to invoice** queues update.
- Floor picking is unchanged by invoicing itself.
- Customer credit utilization rises when invoices post.

#### 3. Safety, Reversal & Undo Rules
- Reverse billing mistakes through finance-approved credit/re-invoice steps—not by deleting stock movements.
- Shipping mistakes still need RMA / receive-return flows.

#### 4. Troubleshooting Common Blockers
- **Invoice remaining unavailable?** Nothing left to bill, or order not far enough through ship.
- **Credit Hold banner?** Clear hold with Owner/billing before forcing allocation or new invoices.

---

### Fulfillment waves, picking, packing

- **Target Audience & Roles:** WAREHOUSE_MANAGER builds/releases waves; PICKER claims and scans; VIEWER typically blocked from floor actions.
- **Route Location:** Floor → **Fulfillment**
- **Primary Operational Goal:** Turn allocated orders into physical picks, totes, packs, and ship-ready cartons.

#### 1. Step-by-Step Action Plan
1. Manager opens **Fulfillment** and clicks **Generate draft wave**.
2. Optionally click **Optimize pick path** to sequence bins.
3. Click **Release to floor** when the wave is ready.
4. On the device, click **Claim wave (device lock)** so only that scanner owns the work.
5. Choose work mode **Single** / **Batch** / **Pack** as directed.
6. Set scan mode **Pick** (use **Receive**, **LPN Move**, or **Build Pallet** only when that is the task).
7. Scan location and product barcodes as prompted; in batch, follow **Place in tote**.
8. For packing: **Connect packing scale** or **Connect Bluetooth scale**, then **Complete Pack**; **Disconnect** when finished.
9. For pallet builds: **Mint New LPN**, then **Finish / new pallet** when the pallet is complete.
10. If a barcode is unreadable, tap **Skip & Flag Barcode**—do not invent digits.
11. If a next-action card appears, use **Dismiss** when done reading; use **Retry** after fixing a transient device issue.
12. Watch **Replenishments Needed** and jump to **Replenishments** when pick faces are empty.

#### 2. Correlated Flow & Downstream Ripple Effect
- **Office:** Sales order chips move through **PARTIALLY SHIPPED** / **SHIPPED**; Dashboard fulfillment KPIs refresh.
- **Other pickers:** Device lock prevents two scanners fighting the same wave.
- **ATP:** Picked/shipped stock leaves sellable availability; short picks can resurface backorder risk.
- **Finance:** Completing pack/ship unblocks **Invoice** / **Invoice remaining**.

#### 3. Safety, Reversal & Undo Rules
- Wrong pick: use **Skip & Flag Barcode** or ask a manager—do not silently overstate quantities.
- Do not erase prior scans; managers post stock corrections when the ledger must move.
- Packing mistakes: stop before **Complete Pack** if the scale weight is wrong; reopen with a manager if already completed.

#### 4. Troubleshooting Common Blockers
- **No tasks after Release to floor?** Confirm you **Claim wave (device lock)** and that orders were **Allocate**d.
- **Why did my scan park in Sync Conflicts?** Bin quantity likely changed while the badge showed **Offline - Caching Scans**—resolve under **Exceptions → Sync Conflicts**.
- **What if an item is damaged on the floor?** Use **Skip & Flag Barcode**, photograph/escalate per site rules, never type a guessed barcode.

---

### Replenishments

- **Target Audience & Roles:** WAREHOUSE_MANAGER, PICKER.
- **Route Location:** Inventory / Floor → **Replenishments**
- **Primary Operational Goal:** Move stock from reserve into pick faces before waves stall.

#### 1. Step-by-Step Action Plan
1. Open **Replenishments** when **Fulfillment** shows **Replenishments Needed**.
2. Follow directed moves: scan from-bin and to-bin as prompted.
3. Return to **Fulfillment** and continue **Pick**.

#### 2. Correlated Flow & Downstream Ripple Effect
- Pickers regain bin quantity for the wave.
- Office sees fewer short picks and **BACKORDERED** surprises.
- ATP in pickable locations improves even if total warehouse on-hand was already there.

#### 3. Safety, Reversal & Undo Rules
- Wrong bin move: stop and request a manager correction—do not invent a reverse scan sequence unless the screen offers one.

#### 4. Troubleshooting Common Blockers
- **No replenishment tasks?** Reserve may already be empty—trigger inbound receive or purchase.
- **Offline parking?** Resolve parked moves in **Sync Conflicts** before releasing more waves.

---

### Dashboard outbound cues

- **Target Audience & Roles:** Office roles; pickers use **Start scanning** / **Open fulfillment scanner**.
- **Route Location:** **Dashboard**
- **Primary Operational Goal:** Jump to allocation, invoicing, or exception work in one tap.

#### 1. Step-by-Step Action Plan
1. Open **Dashboard**.
2. Use **New sales order** when taking demand.
3. In the work queue, follow **Do this next** cards (**Needs allocation**, **Ready to invoice**, **Open AR**, **Low stock**).
4. For exceptions, click **Open queue (N)** or **Resolve**.
5. Pickers: **Open fulfillment scanner** / **Start scanning**.

#### 2. Correlated Flow & Downstream Ripple Effect
- Same destinations as sidebar routes; clears blockers faster for floor and finance.

#### 3. Safety, Reversal & Undo Rules
- Dashboard never skips confirmation dialogs on the destination page.

#### 4. Troubleshooting Common Blockers
- **Credit Hold messaging?** Resolve billing hold before hammering **Allocate**.
- **Picker sees office cards?** Rare mixed-role sessions—switch to fulfillment scanner workflow.
