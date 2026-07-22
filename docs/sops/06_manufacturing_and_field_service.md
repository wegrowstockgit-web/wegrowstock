---
title: "Manufacturing & Field Service SOP"
slug: "sop-manufacturing-field"
sourcePath: "docs/sops/06_manufacturing_and_field_service.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER"]
routeHints: ["/manufacturing/boms", "/manufacturing/orders", "/manufacturing/terminal", "/issue-supplies", "/field/truck", "/settings"]
---

# Manufacturing & Field Service — Operations Playbook

Bills of materials, production orders, the shop-floor terminal, issuing supplies, and technician van stock. Keep language on buttons, statuses, and physical steps.

---

### Bills of materials (BOMs)

- **Target Audience & Roles:** WAREHOUSE_MANAGER, ADMIN, OWNER; VIEWER read-only.
- **Route Location:** Manufacturing → **BOMs**
- **Primary Operational Goal:** Define which components build a finished item before anyone starts a production order.

#### 1. Step-by-Step Action Plan
1. Open **BOMs**.
2. Create or open a bill that lists component SKUs and quantities.
3. Save using the on-screen save/confirm control for that form.
4. Verify the finished good also exists under **Products** with scannable identity for later **Complete build**.

#### 2. Correlated Flow & Downstream Ripple Effect
- Production orders consume component availability and eventually add finished goods.
- Pick faces may need replenishment when kits pull many components.
- ATP for components drops when builds allocate; finished-good ATP rises after **Complete build**.
- Finance sees manufacturing completion in valuation/COGS-oriented reports—not as a silent spreadsheet edit.

#### 3. Safety, Reversal & Undo Rules
- Correct BOM mistakes before releasing many production orders.
- If a bad build already completed, use attributed stock corrections / new orders—do not erase completion history.

#### 4. Troubleshooting Common Blockers
- **Cannot save BOM?** Missing component SKU—create it under **Products** or **Import** first.
- **VIEWER cannot edit?** Expected—ask a Warehouse Manager.

---

### Production orders (office)

- **Target Audience & Roles:** WAREHOUSE_MANAGER, ADMIN, OWNER.
- **Route Location:** Manufacturing → **Production Orders**
- **Primary Operational Goal:** Plan and track builds from draft through completion.

#### 1. Step-by-Step Action Plan
1. Open **Production Orders**.
2. Click **Create order** and select the BOM / finished good and quantity.
3. Track status chips with spaces (for example **DRAFT**, **COMPONENTS ALLOCATED**, **WIP**, **COMPLETED**, **CANCELLED**).
4. Use modal **Cancel** when abandoning a build that should not start.
5. Hand work to the floor via **Manufacturing terminal** once the order is ready to run.

#### 2. Correlated Flow & Downstream Ripple Effect
- Terminal operators start timesheets and complete builds against these orders.
- Component shortages surface as blocked builds—trigger POs or transfers.
- Completed builds change what sales can promise on finished goods.

#### 3. Safety, Reversal & Undo Rules
- Prefer **Cancel** while still **DRAFT** / early states.
- After **COMPLETED**, reverse with careful corrections and, if needed, a new rework order—history stays.

#### 4. Troubleshooting Common Blockers
- **Stuck before WIP?** Components may be short—check **Products** on-hand and inbound POs.
- **Create order missing?** Role or module access—ask Admin.

---

### Manufacturing terminal (shop floor)

- **Target Audience & Roles:** PICKER / production operators; managers supervise.
- **Route Location:** Floor → **Manufacturing terminal**
- **Primary Operational Goal:** Capture labor time and finish builds with scanner discipline.

#### 1. Step-by-Step Action Plan
1. Open **Manufacturing terminal** on the floor device.
2. Select the production order you are running.
3. Click **Start timesheet** when work begins.
4. Consume/scan components as the terminal prompts (follow on-screen scan targets).
5. Click **Stop timesheet** for breaks or end of shift segments as your site requires.
6. When the finished good is truly done, click **Complete build**.
7. Stage finished goods into the directed bin if prompted after completion.

#### 2. Correlated Flow & Downstream Ripple Effect
- Office production order moves toward **COMPLETED**.
- Component on-hand decreases; finished-good on-hand increases for later **Allocate** on sales orders.
- Labor & Velocity report tabs gain usable time signals.
- Field/issue flows remain separate unless your BOM consumes van-issued parts by design.

#### 3. Safety, Reversal & Undo Rules
- Do not **Complete build** if components were skipped or damaged—escalate.
- Time mistakes: stop/start honestly on the next segment rather than inventing hours.
- Stock mistakes after completion need manager corrections, not silent edits.

#### 4. Troubleshooting Common Blockers
- **Complete build disabled?** Timesheet still running, scans missing, or order not in a runnable status.
- **Offline parking mid-build?** Resolve **Sync Conflicts** before completing another build on that device.
- **Damaged component?** Quarantine and tell a manager—do not substitute an unlabeled part.

---

### Issue supplies (cost centers / requisitions)

- **Target Audience & Roles:** WAREHOUSE_MANAGER, PICKER/ops as permitted; Admin configures cost centers under Settings.
- **Route Location:** Field → **Issue Supplies**; Settings → **Cost Centers & Requisitions**
- **Primary Operational Goal:** Hand out internal supplies against a cost center without pretending it is a customer shipment.

#### 1. Step-by-Step Action Plan
1. Admins maintain cost centers under **Settings → Cost Centers & Requisitions**.
2. Operators open **Issue Supplies**.
3. Select the requisition/cost center context shown on screen.
4. Scan or confirm items, then submit with **Issue Fact**.
5. Use **Back to list** to take the next request.

#### 2. Correlated Flow & Downstream Ripple Effect
- Warehouse on-hand drops for issued consumables; customer ATP usually unchanged unless the same SKU is sellable stock.
- Finance/cost-center reporting sees the issue event.
- Pick waves should not steal quantities already issued to a job—coordinate timing.

#### 3. Safety, Reversal & Undo Rules
- Wrong issue: stop and ask a manager for a correction / return-to-stock process used at your site.
- Do not “put it back” by deleting the issue fact—add a correcting movement.

#### 4. Troubleshooting Common Blockers
- **Issue Fact disabled?** Missing cost center or quantity—complete required fields/scans.
- **No cost centers listed?** Admin must configure **Cost Centers & Requisitions** first.

---

### Technician truck (van stock)

- **Target Audience & Roles:** Field technicians / PICKER-like field roles; managers oversee transfers.
- **Route Location:** Field → **Technician Truck**
- **Primary Operational Goal:** Move stock onto a van, consume on-site, and keep the truck inventory honest.

#### 1. Step-by-Step Action Plan
1. Open **Technician Truck**.
2. Click **Assign to me** when claiming the truck session/device context.
3. To load the van from the warehouse, use **Transfer to van** and complete the scans/confirms shown.
4. On site, use **Consume from van** when parts are used on the job.
5. End the day with counts if your manager requires a cycle count on van bins.

#### 2. Correlated Flow & Downstream Ripple Effect
- Warehouse pickable ATP drops when stock moves to the van.
- Office can see field consumption separate from customer parcel shipping.
- Manufacturing may still need warehouse components—even if vans hold service parts.

#### 3. Safety, Reversal & Undo Rules
- Mis-transfers: stop and reverse with a manager-approved transfer back—do not hide usage.
- Consumptions are attributed usage events; fix with corrections, not deletion.

#### 4. Troubleshooting Common Blockers
- **Assign to me fails?** Another tech may hold the truck—coordinate handoff.
- **Consume from van short?** Perform a van count; do not borrow unlabeled stock from another truck.
- **Offline in the field?** Be precise; resolve any parked moves under **Exceptions → Sync Conflicts** when back online.

---

### Cross-module coordination checklist

- **Target Audience & Roles:** WAREHOUSE_MANAGER, ADMIN.
- **Route Location:** Across Manufacturing, Fulfillment, Purchase Orders, Settings
- **Primary Operational Goal:** Keep builds, vans, and outbound waves from fighting over the same components.

#### 1. Step-by-Step Action Plan
1. Before releasing a large production batch, confirm component POs are **RECEIVED** into usable bins.
2. Before **Release to floor** on sales waves, check manufacturing is not holding the last components in **WIP**.
3. Before **Transfer to van**, confirm sales allocations will not immediately go **BACKORDERED**.
4. Use **Dashboard** low-stock and exception cards daily.

#### 2. Correlated Flow & Downstream Ripple Effect
- Honest prioritization protects customer ship dates and field SLAs together.
- Finance sees fewer emergency corrections and write-offs.

#### 3. Safety, Reversal & Undo Rules
- When two teams need the same SKU, managers decide openly—then record moves with the proper buttons.
- Never delete competing history to favor one team.

#### 4. Troubleshooting Common Blockers
- **Everything looks allocated but bins are empty?** Run **Cycle counts**, clear **Sync Conflicts**, then rebuild the wave.
- **Complete build succeeded but sales still BACKORDERED?** Finished goods may sit in a non-sellable location—putaway/transfer into a pickable bin.
