---
title: "Exceptions & Conflict Resolution SOP"
slug: "sop-exceptions-conflicts"
sourcePath: "docs/sops/04_exceptions_and_conflict_resolution.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER"]
routeHints: ["/exceptions", "/settings", "/fulfillment", "/returns", "/returns/receive", "/purchase-orders"]
---

# Exceptions & Conflict Resolution — Operations Playbook

Damaged product, skip-and-flag, offline parked scans, fulfillment holds, returns, and office/finance matching when paperwork disagrees with the dock. Plain language only.

---

### Exceptions hub (holds & sync)

- **Target Audience & Roles:** WAREHOUSE_MANAGER owns decisions; PICKER may open to understand parked work; VIEWER read-only; ADMIN/OWNER oversee.
- **Route Location:** Inventory → **Exceptions** (tabs **Fulfillment Holds** and **Sync Conflicts**); also **Settings → Sync Conflicts**
- **Primary Operational Goal:** Clear work that stopped the floor so waves and receives can continue safely.

#### 1. Step-by-Step Action Plan
1. Open **Exceptions** (or Dashboard **Open queue** / **Resolve Now**).
2. Choose **Fulfillment Holds** or **Sync Conflicts**.
3. Read the reason text on the card (damaged barcode, short bin, parked scan type badges such as **Inbound Receive**, **Outbound Pick**, **Cycle Count**).
4. For holds: use **Lot override**, **Clear**, or **Discard** as the card allows.
5. For sync: choose **Discard Transaction** or **Approve & Re-process** (confirm **Discard transaction?** / **Approve & re-process?**).
6. Return to **Fulfillment** or **Inbound Receive** and continue scanning.

#### 2. Correlated Flow & Downstream Ripple Effect
- **Pickers:** Device can proceed after the parked item is discarded or approved.
- **Managers:** Dashboard exception counts drop; allocation/waves become trustworthy again.
- **ATP:** Approving a replayed move may change on-hand; discarding prevents a bad quantity from landing.
- **Finance:** Cleaner stock truth means invoices and valuation stop drifting from the floor.

#### 3. Safety, Reversal & Undo Rules
- **Discard Transaction** drops the parked attempt; it does not rewrite older history.
- **Approve & Re-process** retries the real-world intent after you fix the bin/product state.
- Core rule: never delete past stock history—only add attributed corrections or approved adjustments.

#### 4. Troubleshooting Common Blockers
- **Why did my scan park in Sync Conflicts?** Usually the handheld showed **Offline - Caching Scans** and the bin changed before **Syncing…** / **Connected** finished.
- **Approve & Re-process fails again?** Fix the physical bin count or complete a cycle count first, then retry.
- **VIEWER cannot Discard?** Escalate to a Warehouse Manager.

---

### Skip & Flag damaged / unreadable barcodes

- **Target Audience & Roles:** PICKER on **Fulfillment**; managers clear resulting holds.
- **Route Location:** Floor → **Fulfillment**
- **Primary Operational Goal:** Keep the wave moving when a label is torn without inventing numbers.

#### 1. Step-by-Step Action Plan
1. During **Pick** (or other scan mode), when the barcode will not read, tap **Skip & Flag Barcode**.
2. Follow any on-screen exception prompts; do not type a guessed code.
3. Physically quarantine the unit per site rules.
4. Manager opens **Exceptions → Fulfillment Holds** and uses **Clear**, **Discard**, or **Lot override** as appropriate.
5. Print/apply a replacement label when Products / lots tooling in your site process allows, then resume the wave.

#### 2. Correlated Flow & Downstream Ripple Effect
- Wave line moves to an exception state instead of blocking the entire device forever.
- Office sees the hold reason; customer ship dates may slip until replacement stock is picked.
- ATP for that unit becomes unavailable until the exception is resolved.

#### 3. Safety, Reversal & Undo Rules
- Skipping is safer than fabricating a scan.
- After resolution, continue with honest scans only.
- Stock corrections remain the path if quantity was already wrong.

#### 4. Troubleshooting Common Blockers
- **What if an item is damaged on the floor?** **Skip & Flag Barcode**, quarantine, tell a manager—do not complete pack on damaged goods.
- **Skip control missing?** Confirm you are on **Fulfillment** in an active claimed wave.

---

### Offline caching & network badge behavior

- **Target Audience & Roles:** All floor roles.
- **Route Location:** Header network badge on floor shells
- **Primary Operational Goal:** Understand **Connected**, **Offline - Caching Scans**, and **Syncing…** so you know when work might park.

#### 1. Step-by-Step Action Plan
1. Glance at the network badge before a long wave.
2. If **Offline - Caching Scans**, keep scans accurate—know they may need manager review later.
3. When **Syncing…** appears, wait for **Connected** before releasing the device to another user.
4. If anything parked, open **Exceptions → Sync Conflicts**.

#### 2. Correlated Flow & Downstream Ripple Effect
- Office managers get conflict cards instead of silent data loss.
- ATP updates only after successful sync/approval.

#### 3. Safety, Reversal & Undo Rules
- Prefer **Discard Transaction** for scans you know were wrong while offline.
- Prefer **Approve & Re-process** after the bin is corrected.

#### 4. Troubleshooting Common Blockers
- **Badge stuck Syncing…?** Stay on Wi-Fi; ask IT/manager before wiping the device.
- **Device locked for PIN?** Complete scanner PIN unlock before resuming counts/picks.

---

### Customer Returns & RMA Processing

- **Target Audience & Roles:** WAREHOUSE_MANAGER / ADMIN / OWNER for the 3-step New RMA wizard, disposition, complete, and Escalate to RTV. PICKER/ops on **Returns receive**.
- **Route Location:** Inbound → **Returns** (`/returns` or `/inbound/returns`); Floor → **Returns receive**
- **Primary Operational Goal:** Authorize specific returned SKUs, generate inbound labels, restock or scrap each line, and draft a credit memo when the RMA closes.

#### 1. Step-by-Step Action Plan
1. Click **New RMA**. Step 1: search customer name, sales order #, or customer PO # and select a shipped order.
2. Step 2: check returned SKUs, enter return qty, and pick a reason (`DEFECTIVE_PRODUCT`, `WRONG_ITEM_SHIPPED`, `DAMAGED_IN_TRANSIT`, `BUYER_REMORSE`, `SIZE_FIT_EXCHANGE`).
3. Step 3: choose `REFUND_CREDIT_MEMO`, `REPLACEMENT_ORDER`, or `REPAIR`. Optionally generate an EasyPost return label, then **Create RMA**.
4. When freight arrives, receive on **Returns receive** (condition photo + Confirm +1).
5. Expand the accordion: **RESTOCK** requires a restock target bin; **SCRAP** writes off; **Escalate to RTV** drafts a vendor return for manufacturer defects.
6. **Complete Disposition & Close RMA** sets status **CLOSED** and drafts a Credit Memo (return value minus restocking fees) unless the resolution is Repair.

#### 2. Correlated Flow & Downstream Ripple Effect
- Portal / showroom returns still land in the **PENDING_REVIEW** queue.
- Restock posts `RMA_RESTOCK` to the chosen bin; scrap posts `RMA_SCRAP` when stock was already received.
- The draft credit memo appears on **Invoices**. RTV drafts appear on **Purchasing → RTV**.

#### 3. Safety, Reversal & Undo Rules
- Do not restock a broken item. Use **Inspect / QC Details**, switch to **SCRAP**, and post a stock correction before allocation.
- History of the RMA remains; corrections are new adjustments.

#### 4. Troubleshooting Common Blockers
- **Complete disabled / 422 RESTOCK_BIN_REQUIRED?** Choose a restock target bin on every RESTOCK line.
- **Escalate to RTV fails?** Assign a default supplier on the SKU (or create a supplier) first.
- **Confirm +1 disabled?** Scan the RMA barcode and attach **Condition photo** when required.

---

### Purchase invoice vs dock vs PO (three-way agreement)

- **Target Audience & Roles:** WAREHOUSE_MANAGER, ADMIN, OWNER.
- **Route Location:** **Purchase Orders** (receive + **Upload invoice document** / **Upload & reconcile**)
- **Primary Operational Goal:** Make sure what you ordered, what arrived, and what the supplier billed all tell the same story.

#### 1. Step-by-Step Action Plan
1. Confirm the PO lines and status (**SUBMITTED**, **PARTIALLY RECEIVED**, **RECEIVED**).
2. Finish dock work with **Floor receive** / **Directed Putaway**.
3. Click **Upload invoice document**, review, then **Upload & reconcile**.
4. If quantities disagree, stop—fix dock counts or supplier paperwork before forcing reconcile.
5. Escalate stubborn mismatches through **Exceptions** rather than inventing numbers.

#### 2. Correlated Flow & Downstream Ripple Effect
- Finance trusts purchase spend and inventory value.
- Sales ATP stays honest because receive quantities were real.
- Suppliers get faster payment when paperwork matches.

#### 3. Safety, Reversal & Undo Rules
- Never “fix” a mismatch by deleting receive history.
- Use corrections, recounts, or supplier credit notes with attribution.

#### 4. Troubleshooting Common Blockers
- **Reconcile blocked after partial truck?** Wait until remaining lines arrive or split expectations with a manager.
- **Invoice total differs but qty matches?** Involve Owner/finance for cost adjustments—floor should not fake quantity to match dollars.
