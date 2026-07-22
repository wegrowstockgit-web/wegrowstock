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

### Returns office (RMA) & returns receive

- **Target Audience & Roles:** WAREHOUSE_MANAGER / ADMIN for RMA office; PICKER/ops on **Returns receive**.
- **Route Location:** Inbound → **Returns**; Floor → **Returns receive**
- **Primary Operational Goal:** Authorize customer returns, then scan them back with condition photos.

#### 1. Step-by-Step Action Plan
1. Office: open **Returns**, click **New RMA** / **Create RMA**.
2. Review statuses such as **REQUESTED**, **PENDING_REVIEW**, **APPROVED**, **RECEIVED**, **CLOSED**, **REJECTED**.
3. On review, choose **Approve & Buy Label**, **Approve without Label**, or **Deny & Close**.
4. Use **Receive terminal** to jump floor operators into receive work.
5. On **Returns receive**: scan the RMA, capture **Condition photo**, tap **Confirm +1**, then **Scan next RMA**.

#### 2. Correlated Flow & Downstream Ripple Effect
- Customer showroom **Return Items** / **Submit return** feeds this office queue for B2B cases.
- Good stock returned can restore ATP after putaway/quality rules.
- Finance prepares credits after **APPROVED** / **RECEIVED** per policy.

#### 3. Safety, Reversal & Undo Rules
- **Deny & Close** stops a bad return before stock is increased.
- Do not receive damaged goods as pristine—photos exist to protect the ledger story.
- History of the RMA remains; corrections are new adjustments if needed.

#### 4. Troubleshooting Common Blockers
- **Confirm +1 disabled?** Scan the RMA barcode and attach **Condition photo** when required.
- **Customer insists warehouse rewrote history?** Explain returns add a new receive event; nothing is erased.

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
