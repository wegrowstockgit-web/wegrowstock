---
title: "Inventory Control & Compliance SOP"
slug: "sop-inventory-compliance"
sourcePath: "docs/sops/03_inventory_and_compliance.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER"]
routeHints: ["/products", "/import", "/settings/import", "/cycle-counts", "/compliance/lot-trace", "/reports", "/rtls", "/warehouses/add"]
---

# Inventory Control & Compliance — Operations Playbook

Products, imports, ledger visibility, lots, cycle counts, lot trace, reports, warehouses, and RTLS awareness—without talking about databases or services.

---

### Products catalog & ledger history

- **Target Audience & Roles:** OWNER, ADMIN, WAREHOUSE_MANAGER maintain; VIEWER reviews; PICKER rarely needs desktop Products.
- **Route Location:** Inventory → **Products** (peek → **Ledger History**)
- **Primary Operational Goal:** Keep SKUs, units of measure, and movement history understandable for office and floor.

#### 1. Step-by-Step Action Plan
1. Open **Products**.
2. Click **Add product** for a new SKU, or **Import** to bulk load.
3. Edit details and click **Save UoM** when units of measure change.
4. Open a product peek; choose tab **Details** or **Ledger History**.
5. To reverse a mistaken movement shown in history, use the control with label **Reverse transaction**, confirm in **Reverse Transaction?** with **Confirm Reversal** when policy allows.

#### 2. Correlated Flow & Downstream Ripple Effect
- Floor scans depend on correct barcodes/UoM on the product.
- ATP and allocation quality follow the on-hand story told by ledger history.
- Finance valuation and COGS views in **Reports** read the same operational truth.

#### 3. Safety, Reversal & Undo Rules
- Use **Confirm Reversal** / stock correction patterns—never “delete the past.”
- Reversal creates an attributed counter-entry; history stays auditable.

#### 4. Troubleshooting Common Blockers
- **Import button missing?** Role may be VIEWER—ask Admin.
- **Reverse transaction disabled?** Movement type may not be reversible online—ask a manager for a stock correction count instead.

---

### CSV import & settings import

- **Target Audience & Roles:** ADMIN, OWNER (and managers when permitted); legacy path **Settings → Import**.
- **Route Location:** **Import** (also **Settings → Import** for Admin/Owner)
- **Primary Operational Goal:** Load products/locations cleanly with preflight checks before hurting the floor.

#### 1. Step-by-Step Action Plan
1. Open **Import**.
2. Click **Download Template** and fill rows offline.
3. Upload and review preflight chips such as **READY TO IMPORT**, **MISSING PRODUCT**, **MISSING LOCATION**, **MISSING UOM**, **VALIDATION ERROR**.
4. Use **Create missing products based on CSV data** or **Map to existing** as prompted.
5. Re-check with the re-run preflight control when offered.
6. Click **Import N ready row(s)** only when rows are green/ready.

#### 2. Correlated Flow & Downstream Ripple Effect
- New SKUs appear on **Products** and become receivable/pickable.
- Bad imports create exception noise and blocked scans—preflight exists to protect ATP.
- Finance suddenly sees new items in valuation reports after successful import.

#### 3. Safety, Reversal & Undo Rules
- Prefer fixing the file and re-importing clean rows over silent edits after the floor already scanned.
- Quantity truth still goes through receive, counts, and corrections—not spreadsheet reloads alone.

#### 4. Troubleshooting Common Blockers
- **Stuck on MISSING LOCATION?** Create warehouse bins/locations first (**Warehouses** / floor setup).
- **VALIDATION ERROR?** Fix required columns from the template—do not force import.

---

### Cycle counts

- **Target Audience & Roles:** PICKER performs scans; WAREHOUSE_MANAGER approves variances.
- **Route Location:** Floor → **Cycle counts**
- **Primary Operational Goal:** Compare system expectation to physical bin quantity and approve honest adjustments.

#### 1. Step-by-Step Action Plan
1. Open **Cycle counts** on the handheld/floor shell.
2. Scan bin and product as directed.
3. When the count matches, tap **Confirm Match**.
4. When variance needs a manager: wait for status **PENDING MANAGER REVIEW**.
5. Manager clicks **Approve Ledger Adjustment** or **Request Recount**.
6. Watch chips such as **PENDING**, **AUTO APPROVED**, **APPROVED**, **RECOUNT REQUESTED**.

#### 2. Correlated Flow & Downstream Ripple Effect
- Approved adjustments change on-hand and ATP for sales orders.
- Pickers may see fewer phantom picks after honest counts.
- Office **Reports → Inventory Audit** and valuation reflect the adjustment trail.

#### 3. Safety, Reversal & Undo Rules
- Prefer **Request Recount** over guessing.
- Approvals write attributed adjustments; they do not erase earlier movements.
- Never type a “convenient” quantity to clear a variance quietly.

#### 4. Troubleshooting Common Blockers
- **Confirm Match disabled?** Scan sequence incomplete—rescan bin/product.
- **Why did my count park in Sync Conflicts?** You counted while **Offline - Caching Scans** and the bin changed—use **Discard Transaction** or **Approve & Re-process**.
- **Picker cannot Approve Ledger Adjustment?** Manager-only control—escalate.

---

### Lot / serial trace & compliance export

- **Target Audience & Roles:** WAREHOUSE_MANAGER, ADMIN, OWNER; VIEWER may trace read-only.
- **Route Location:** Inventory → **Lot Trace**
- **Primary Operational Goal:** Follow a lot through customers/orders when quality or recall questions appear.

#### 1. Step-by-Step Action Plan
1. Open **Lot Trace**.
2. Enter the lot / identifier and click **Trace**.
3. Review affected customers and order touchpoints on screen.
4. Click **Export affected customers** when outreach is required.

#### 2. Correlated Flow & Downstream Ripple Effect
- Operations may pause picks for quarantined lots (coordinate with **Exceptions**).
- Sales/customer service uses the export for notifications.
- Finance may need to prepare credits after approved returns.

#### 3. Safety, Reversal & Undo Rules
- Trace is investigative—do not “edit history” to hide a lot.
- Physical quarantine + returns/receive flows correct the floor state.

#### 4. Troubleshooting Common Blockers
- **No results?** Confirm lot spelling and that receive captured the lot at inbound.
- **Export empty?** Trace found no customer shipments yet—still quarantine on-hand.

---

### Reports (valuation, fulfillment, labor, audit)

- **Target Audience & Roles:** OWNER, ADMIN, WAREHOUSE_MANAGER; VIEWER as permitted.
- **Route Location:** Admin → **Reports** (tabs)
- **Primary Operational Goal:** Understand valuation, movement, fulfillment performance, and audit posture without changing stock.

#### 1. Step-by-Step Action Plan
1. Open **Reports**.
2. Choose a tab: Inventory valuation, Time-travel valuation, Stock turnover, COGS ledger, Profit & margin, Sales performance, Fulfillment, Purchase spend, Returns, Demand sensing, Labor & Velocity, Inventory Audit.
3. Apply on-screen filters/date ranges.
4. Use outputs to coach floor and purchasing—not to silently rewrite counts.

#### 2. Correlated Flow & Downstream Ripple Effect
- Insights drive POs, waves, and cycle-count priorities.
- No direct picker task is created until someone acts on **Purchase Orders**, **Fulfillment**, or **Cycle counts**.

#### 3. Safety, Reversal & Undo Rules
- Reports are read-only; corrections still happen on operational screens with attribution.

#### 4. Troubleshooting Common Blockers
- **Numbers look stale?** Finish pending **Approve Ledger Adjustment** / sync conflict decisions first.
- **Audit log link redirects?** Some audit views open under **Settings → Operations**—follow the redirect.

---

### Add warehouse & RTLS map

- **Target Audience & Roles:** ADMIN, OWNER (warehouse add); RTLS for ops leaders.
- **Route Location:** **Warehouses → Add**; Admin → **RTLS map**
- **Primary Operational Goal:** Open a new site context and visualize floor telemetry when enabled.

#### 1. Step-by-Step Action Plan
1. Open **Warehouses → Add** and complete the wizard fields; save.
2. Switch active warehouse context in the header when working that site.
3. Open **RTLS map**; use **Inject sample telemetry** only in training/demo situations.

#### 2. Correlated Flow & Downstream Ripple Effect
- All POs, SOs, and waves are warehouse-scoped—wrong context causes empty queues.
- Pickers on locked hardware contexts may be pinned to one site (SSID/geofence messaging in the shell).

#### 3. Safety, Reversal & Undo Rules
- Do not move historical stock by renaming warehouses; use proper transfers/corrections.
- Sample telemetry is not a stock correction.

#### 4. Troubleshooting Common Blockers
- **Screens empty after login?** Check the active warehouse chip/lock reason in the header.
- **Cannot add warehouse?** Owner/Admin only.
