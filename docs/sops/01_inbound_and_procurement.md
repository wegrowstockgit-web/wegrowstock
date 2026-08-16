---
title: "Inbound Receiving & Procurement SOP"
slug: "sop-inbound-procurement"
sourcePath: "docs/sops/01_inbound_and_procurement.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER", "SUPPLIER"]
routeHints: ["/purchase-orders", "/suppliers", "/mesh-network", "/inbound/receive", "/supplier-portal", "/dashboard"]
---

# Inbound Receiving & Procurement — Operations Playbook

This playbook covers buying goods, coordinating supplier delivery dates, receiving on the dock, directed putaway, and keeping landed cost and office visibility aligned. Speak only in screen names, **bold** button labels, status chips, and floor steps.

---

### Purchase Orders (create & manage)

- **Target Audience & Roles:** OWNER, ADMIN, WAREHOUSE_MANAGER create and manage; VIEWER can review; PICKER does not create POs from the handheld.
- **Route Location:** Inbound → **Purchase Orders** (also **New purchase order** from Dashboard)
- **Primary Operational Goal:** Document what you ordered from a supplier so the dock knows what to expect and Available-to-Promise can improve once stock is received.

#### 1. Step-by-Step Action Plan
1. Open **Purchase Orders** from the Inbound menu (or tap **New purchase order** on the Dashboard).
2. Click **New PO** (or **Create purchase order** / **Create PO** when finishing the form).
3. Choose the supplier, warehouse, and lines (SKU, quantity, expected dates).
4. Save the draft, then move it forward until the status chip shows **SUBMITTED** (or keep **DRAFT** until ready).
5. When the truck is at the dock, use **Floor receive** or **Receive stock** / **Receive** to open receiving for that PO.
6. If you uploaded a supplier invoice for reconciliation, use **Upload invoice document**, then **Upload & reconcile** after review.
7. To stop a PO that should never arrive, use **Cancel** while it is still safe to cancel (before full **RECEIVED** / **CLOSED**).

#### 2. Correlated Flow & Downstream Ripple Effect
- **Floor pickers:** See putaway and later pick work only after lines are received into bins—nothing to scan until receive finishes.
- **Office managers:** Dashboard work queues and inbound status chips update (**PARTIALLY RECEIVED**, **RECEIVED**, **IN TRANSIT** when shown).
- **Available-to-Promise:** On-hand rises after successful receive/putaway, which can clear **BACKORDERED** pressure on sales orders.
- **Finance / credit:** Landed-cost and invoice reconciliation feed valuation and payables; customer credit limits are not changed by creating a PO.

#### 3. Safety, Reversal & Undo Rules
- Prefer **Cancel** on a PO that has not been received.
- After stock is on the floor, fix mistakes with an attributed stock correction or cycle-count approval—not by erasing history.
- Past receive history is permanent; corrections add a new, attributed movement.

#### 4. Troubleshooting Common Blockers
- **Why can’t I create a PO?** You may be a PICKER-only or VIEWER session—ask a Warehouse Manager or Admin.
- **Why is Receive unavailable?** The PO may be **CANCELLED** or already **CLOSED**; open the correct warehouse context first.
- **Why is the PO stuck in DRAFT?** Finish required supplier/line fields, then submit so dock teams see **SUBMITTED**.

---

### Suppliers master list

- **Target Audience & Roles:** OWNER, ADMIN, WAREHOUSE_MANAGER; VIEWER read-only.
- **Route Location:** Inbound → **Suppliers**
- **Primary Operational Goal:** Keep supplier names and contacts accurate so Purchase Orders and the supplier delivery portal stay trustworthy.

#### 1. Step-by-Step Action Plan
1. Open **Suppliers**.
2. Review or add supplier cards used when creating a **New PO**.
3. Confirm the supplier on each purchase order before sending or receiving.

#### 2. Correlated Flow & Downstream Ripple Effect
- Wrong supplier on a PO confuses dock receive and supplier portal delivery dates.
- Office reports for purchase spend group by the supplier you selected.
- ATP and finance are unaffected until goods are received and valued.

#### 3. Safety, Reversal & Undo Rules
- Correct supplier details before receive when possible.
- If the wrong supplier was used on an open PO, **Cancel** and recreate rather than inventing a silent rewrite after receive.

#### 4. Troubleshooting Common Blockers
- **Supplier missing on New PO?** Create or restore the supplier under **Suppliers** first.
- **VIEWER cannot edit?** Expected—ask an Admin or Warehouse Manager.

---

### Mesh Network (discover, connect, publish)

- **Target Audience & Roles:** OWNER and ADMIN when the workspace has **MESH_NETWORK**. Pickers and Viewers do not see this hub.
- **Route Location:** Inbound → **Mesh Network** (`/mesh-network`). Dashboard **Smart sourcing** card when a connected partner has a low SKU.
- **Primary Operational Goal:** Find products other tenants published, connect securely, and let a later Purchase Order become their Sales Order without retyping the catalog.

#### 1. Step-by-Step Action Plan
1. Open **Mesh Network**.
2. **Discover** — browse other companies’ published products (name, image, seller). Price and stock stay hidden. Click **Request Connection** on a product you want to buy from that seller.
3. **My Network** — outgoing requests show **REQUESTED**. Incoming requests show **PENDING**. Click **Approve** only if you are the seller and want to trade.
4. **Shared Catalog** — toggle **Publish to Network** on your own SKUs and enter a **Mesh Wholesale Price**.
5. After **CONNECTED**, map SKUs under Settings → **Partner Catalog** if the partner’s SKU codes differ from yours.
6. Create or confirm a Purchase Order against the auto-created mesh Supplier. Confirming a mesh PO writes a note **Linked to Mesh Partner Sales Order #SO-…** on your PO.
7. If the Dashboard shows **You are running low on [Product]. Your Mesh Partner [Name] has this in stock.**, click **Draft PO**.

#### 2. Correlated Flow & Downstream Ripple Effect
- **Approve** creates a Supplier on the buyer’s books and a Customer on the seller’s books, then marks the relationship **CONNECTED**.
- The seller later sees an **UNALLOCATED** (or, after ordinary Submit + outbox, **CONFIRMED**) sales order for the buyer’s PO.
- Floor receive on the buyer side is unchanged — still **Inbound receive** once freight arrives.

#### 3. Safety, Reversal & Undo Rules
- Do not approve a connection you do not recognize.
- Discover never shows another tenant’s price or on-hand.
- You cannot approve your own outgoing request.

#### 4. Troubleshooting Common Blockers
- **Mesh Network missing from Inbound?** Your plan may not include **MESH_NETWORK**, or you are not an Owner/Admin.
- **Discover empty?** No other tenant has published SKUs yet — ask them to use **Shared Catalog**.
- **Approve missing?** You are looking at an outgoing **REQUESTED** row; only the seller sees **Approve**.
- **Draft PO opened Purchase Orders but no line?** The SKU must exist in your catalog; Smart sourcing matches your low SKU to a partner listing.

---

### Supplier delivery portal (token link)

- **Target Audience & Roles:** SUPPLIER contacts using a private link; warehouse staff monitor outcomes on Purchase Orders.
- **Route Location:** Supplier portal purchase-order link (shared by your team)
- **Primary Operational Goal:** Let the supplier confirm a delivery date without logging into the warehouse app.

#### 1. Step-by-Step Action Plan
1. Supplier opens the secure link they received for that purchase order.
2. Review the PO lines and promised quantities.
3. Enter the delivery date and click **Submit delivery date**.
4. Print if needed for their dock paperwork.

#### 2. Correlated Flow & Downstream Ripple Effect
- Office sees improved inbound timing on the related Purchase Order.
- Floor can plan labor for receive day; pickers are not interrupted until stock arrives.
- ATP improves only after physical receive—not when the date is submitted.
- Finance still waits for receive/invoice matching.

#### 3. Safety, Reversal & Undo Rules
- If the date was wrong, submit an updated delivery date through the same portal when your team re-shares access.
- Never ask suppliers to invent received quantities—that stays on your dock with **Receive**.

#### 4. Troubleshooting Common Blockers
- **Link expired or blank?** Ask your Growstock Admin to resend the portal link for that PO.
- **Cannot submit?** Confirm you are on the latest link and that the PO is still open (**SUBMITTED** / **IN TRANSIT** / **PARTIALLY RECEIVED**).

---

### Directed inbound receive & putaway

- **Target Audience & Roles:** PICKER and WAREHOUSE_MANAGER on the floor; office may launch via **Floor receive**.
- **Route Location:** **Inbound → Receive** (standalone receive screen) or **Floor receive** from a Purchase Order
- **Primary Operational Goal:** Confirm what arrived and put each line into the correct bin using scanner-led **Directed Putaway**.

#### 1. Step-by-Step Action Plan
1. From the Purchase Order, click **Floor receive** / **Receive stock**, or open **Inbound Receive** directly.
2. Scan or select the purchase order and product barcodes as prompted.
3. For multi-line receipts, use **Receive All (N)** when the screen offers a bulk confirm, or receive line-by-line.
4. Follow **Directed Putaway**—scan the bin when prompted (“Confirm putaway — scan bin”).
5. Tap **Continue to Putaway** when moving from quantity confirm into bin placement.
6. Use **Receive another line** to keep working the same truck without restarting the whole PO.
7. When the line shows **Received**, move to the next carton.

#### 2. Correlated Flow & Downstream Ripple Effect
- **Handhelds:** Putaway completes bin on-hand; later waves can allocate that stock.
- **Desktop:** PO status moves toward **PARTIALLY RECEIVED** or **RECEIVED**; Dashboard inbound signals refresh.
- **ATP:** Sellable availability rises after putaway to a usable bin (not while cartons sit unconfirmed on the dock).
- **Finance:** Unit valuation and landed-cost surcharges can roll into inventory value used by invoices and margins.

#### 3. Safety, Reversal & Undo Rules
- If you scanned the wrong bin, stop and ask a manager for a stock correction or move—do not invent a second silent receive.
- Over-receive beyond tolerance may park work for manager review in **Exceptions**—do not force fake counts.
- History of the receive stays; fixes are additive corrections.

#### 4. Troubleshooting Common Blockers
- **Scan rejected?** Confirm warehouse context, PO status, and that you are in Receive mode—not Pick.
- **Network shows Offline - Caching Scans?** Finish carefully; if a scan parks later, resolve it under **Exceptions → Sync Conflicts** with **Discard Transaction** or **Approve & Re-process**.
- **Putaway bin unknown?** Follow the on-screen directed bin only; ask a manager before choosing a random location.

---

### Landed cost & invoice document upload

- **Target Audience & Roles:** WAREHOUSE_MANAGER, ADMIN, OWNER (finance-aware).
- **Route Location:** Purchase Orders → document upload on the PO
- **Primary Operational Goal:** Attach supplier invoice paperwork and reconcile it so true landed cost sits behind unit value.

#### 1. Step-by-Step Action Plan
1. Open the Purchase Order.
2. Click **Upload invoice document**.
3. Review extracted totals/lines on screen.
4. Click **Upload & reconcile** when the match looks correct.
5. Coordinate with receive so quantity and cost tell the same story.

#### 2. Correlated Flow & Downstream Ripple Effect
- Floor picking does not change; valuation behind the SKU may update for finance views.
- Reports such as inventory valuation and purchase spend become more trustworthy.
- Customer invoices later reflect healthier margin math when cost is complete.

#### 3. Safety, Reversal & Undo Rules
- If the wrong PDF was attached, upload the correct document and re-reconcile with a manager—do not delete stock history to “fix” cost.
- Quantity mistakes still go through receive corrections / cycle counts.

#### 4. Troubleshooting Common Blockers
- **Reconcile blocked?** Line quantities may not match what was received—finish dock receive or adjust with a manager first.
- **VIEWER cannot upload?** Expected—escalate to Admin or Owner.

---

### Dashboard inbound shortcuts

- **Target Audience & Roles:** All office roles; pickers use **Open fulfillment scanner** / **Start scanning** instead.
- **Route Location:** **Dashboard**
- **Primary Operational Goal:** Jump into the next inbound action without hunting the sidebar.

#### 1. Step-by-Step Action Plan
1. Open **Dashboard**.
2. Use **New purchase order** to start buying.
3. Watch exception / sync banners and click **Resolve Now** or **Open queue** when inbound conflicts appear.
4. Use checklist **Create order** only when the card is for purchasing (wording varies by card).

#### 2. Correlated Flow & Downstream Ripple Effect
- Shortcuts land you on the same Purchase Orders / Exceptions screens as the sidebar.
- Clearing inbound exceptions unblocks putaway and later allocation for sales.

#### 3. Safety, Reversal & Undo Rules
- Dashboard buttons never bypass confirmation dialogs on the destination screen—still use **Cancel**, **Discard**, or correction flows there.

#### 4. Troubleshooting Common Blockers
- **Picker does not see New purchase order?** Correct—procurement is an office/manager task.
- **Resolve Now goes to Exceptions?** Complete **Fulfillment Holds** or **Sync Conflicts** tabs as prompted.
