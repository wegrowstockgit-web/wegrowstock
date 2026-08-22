---
title: "Manufacturing & Field Service SOP (Beginner Guide)"
slug: "sop-manufacturing-field"
sourcePath: "docs/sops/06_manufacturing_and_field_service.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER"]
audienceLevel: "beginner"
routeHints: ["/manufacturing/boms", "/manufacturing/orders", "/manufacturing/terminal", "/issue-supplies", "/field/truck", "/settings", "/reports"]
keywords: ["BOM", "bill of materials", "production order", "build", "complete build", "report completion", "log labor", "disassemble", "wrong BOM", "kit", "timesheet", "punch clock", "clock in", "wrong hours", "labor", "issue supplies", "cost center", "technician truck", "van stock"]
---

# Manufacturing & Field Service — Beginner Playbook

This guide covers building products from parts (manufacturing) and taking parts out of the warehouse for jobs (field service). Written for someone who has never seen a Bill of Materials or a production order.

---

## Before you start: the 3 ideas behind these screens

**1. A BOM (Bill of Materials) is a recipe.**
"1 Gift Basket = 2 candles + 1 mug + 1 box." The BOM lists exactly which component SKUs, and how many of each, make one finished item.

**2. A Production Order is one cooking session.**
"Build 50 Gift Baskets using that recipe." When the build completes, the ledger records components leaving stock and finished goods entering — one honest transformation, never a silent edit.

**3. Labor time is part of the cost.**
Clocking in/out and running build timesheets tells the company what an hour of work costs per product. Honest time in = honest product cost out.

**Who does what:**

| Role | Manufacturing & field powers |
|---|---|
| **OWNER / ADMIN** | Everything, incl. BOM edits and module settings |
| **WAREHOUSE_MANAGER** | Creates BOMs and production orders, approves corrections, fixes labor entries |
| **PICKER / operator / technician** | Runs the shop-floor terminal, clocks in/out, issues supplies, manages van stock. Cannot edit BOMs or approve corrections |
| **VIEWER** | Read-only |

---

### How to Create or Fix a BOM (the recipe)

**What is this?**
Before anyone can build, the recipe must exist: which components, how many of each, for one unit of the finished good.

**Who can do this? (Privileges Required)**
WAREHOUSE_MANAGER, ADMIN, or OWNER (requires the MANUFACTURING module). VIEWER read-only; operators don't edit BOMs.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Manufacturing** → **BOMs**

**Step-by-Step Instructions:**
1. Open **BOMs**.
2. Create a bill: pick the finished good, then add each component SKU with its quantity **per one finished unit** (a classic beginner error is entering totals for the whole batch — don't).
3. Save with the on-screen confirm.
4. Check the finished good exists under **Inventory → Products** with a scannable barcode — you'll need it at **Complete build**.

**⚠️ What if I make a mistake?**
- **Wrong component or wrong quantity in the BOM, nothing built yet:** Just edit the BOM. Recipes are master data — free to fix before use.
- **Wrong BOM and builds already completed:** See "How to Disassemble" below — that's the purpose-built undo.
- **Component SKU doesn't exist:** Create it under **Products** (or **Import**) first; the BOM form can't reference a ghost part.

---

### How to Create and Run a Production Order

**What is this?**
The work order: "build N units of X." It reserves components, hands work to the floor, and tracks status: **DRAFT → COMPONENTS ALLOCATED → WIP → COMPLETED / CANCELLED**.

**Who can do this? (Privileges Required)**
WAREHOUSE_MANAGER, ADMIN, or OWNER create and cancel orders. Operators run them at the terminal (next section).

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Manufacturing** → **Production Orders** (search box and pagination at the top of the list)

**Step-by-Step Instructions:**
1. Open **Production Orders** and click **Create order**.
2. Pick the finished good (its BOM comes along) and the quantity to build.
3. Let the system allocate components — chip shows **COMPONENTS ALLOCATED**. If it stalls, components are short: check on-hand and inbound POs.
4. Hand it to the floor: the operator takes over at the **Manufacturing terminal**.
5. Track the chip to **COMPLETED**.

**⚠️ What if I make a mistake?**
- **Wrong quantity (typed 500, meant 50), not started:** **Cancel** the order and create the right one. Cancelled orders stay in the list as history — normal.
- **Wrong finished good / wrong BOM selected, not started:** Same — **Cancel** and recreate. Cancelling releases the reserved components back to stock automatically.
- **Order already WIP:** Talk to the floor first — stop work, then a manager cancels; components already consumed come back via correction or disassembly, depending how far the build got.
- **Duplicate production order:** Cancel the twin before it allocates components away from real work.

---

### How to Work the Manufacturing Terminal (shop floor)

**What is this?**
The floor screen where the actual build happens: start the clock, consume components by scanning, finish with **Complete build** — which is the moment the ledger swaps components for finished goods.

**Who can do this? (Privileges Required)**
Operators (PICKER-type floor roles). Managers supervise and fix mistakes.

**Where to go in weGrowStock:**
🖥️ Floor device → **Manufacturing terminal**

**Step-by-Step Instructions:**
1. Open the terminal and select your production order.
2. Click **Start timesheet** when you begin working — not when you arrive at the building (the header punch clock covers your shift; the timesheet covers this build).
3. Scan components as prompted while assembling.
4. Click **Stop timesheet** for breaks/end of segment, per site rules.
5. When units are truly finished, click **Complete build**.
6. If prompted, put finished goods away into the directed bin.

**⚠️ What if I make a mistake?**
- **Clicked Complete build too early (units not actually finished):** Tell a manager immediately. The completion is a ledger event — the manager reverses it with an attributed correction (or a disassembly if goods were partially real). Same rule as clicking "Shipped" early in SOP 02: the fast confession is the cheap fix.
- **Damaged a component mid-build:** Don't substitute an unlabeled part and don't stay quiet. Quarantine the damaged part, tell the manager (correction records the loss), scan a replacement.
- **Forgot to stop the timesheet before lunch:** See "Fixing labor time" below.
- **Complete build disabled?** Timesheet still running, scans missing, or the order isn't in a runnable status — check the chip.

---

### How to Fix Labor Time Mistakes (punch clock & timesheets)

**What is this?**
Two clocks exist: the **shift punch clock** (the **Clock in / Clock out** control in the floor header — are you at work?) and **build timesheets** (Start/Stop on the terminal — which order is your time charged to?). Both feed the **Labor & Velocity** report and product costing.

**Who can do this? (Privileges Required)**
Everyone clocks their own time. **Correcting a wrong time entry requires a WAREHOUSE_MANAGER or above** — you cannot edit your own past hours.

**Where to go in weGrowStock:**
🖥️ Shift clock: the **Clock in** button in the floor header
🖥️ Build time: **Manufacturing terminal** → **Start timesheet** / **Stop timesheet**
🖥️ Manager review: **Admin** → **Reports** → **Labor & Velocity** tab

**Step-by-Step Instructions:**
1. Arriving: tap **Clock in** in the header.
2. Starting a build: **Start timesheet** on that order.
3. Breaks and handoffs: **Stop timesheet**, and start again on return.
4. Leaving: **Clock out**.

**⚠️ What if I make a mistake?**
- **Typo'd / forgot hours (worked 2h, the clock says 12h because you forgot to clock out):** Tell your manager the real times ("I actually left at 4pm"). The manager posts the correction — the original entry stays, the correction sits next to it with the manager's name. **Never** try to balance it yourself by clocking weird hours tomorrow; two wrong entries are harder to fix than one.
- **Charged time to the wrong production order:** Same path — manager reassigns/corrects the timesheet segment.
- **Forgot to clock in at all:** Report it same day; the manager enters the attributed correction. Memory fades — same-day fixes are accurate fixes.

---

### How to Disassemble a Built Kit (undo a build made with the wrong BOM)

**What is this?**
The purpose-built "undo" for manufacturing: **Disassemble** splits finished goods back into their component parts on the ledger — used when a build used the wrong BOM, or when you need the parts back more than the kits.

**Who can do this? (Privileges Required)**
WAREHOUSE_MANAGER, ADMIN, or OWNER. Operators report the problem; managers run the disassembly.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Manufacturing** → **Production Orders** → **Disassemble** button (top of the page)

**Step-by-Step Instructions:**
1. First fix the recipe: if the BOM was wrong, correct it under **BOMs** — otherwise disassembly returns the wrong parts too.
2. Open **Production Orders** and click **Disassemble**.
3. In the "Disassemble — Split finished goods back into components" dialog, choose the finished-good variant, the location holding the built units, and the quantity to break down.
4. Click **Disassemble**. The ledger writes: finished goods out, components back in — one attributed event.
5. Physically break the kits down and put components back in their bins (follow any directed putaway).
6. If the goal was a correct rebuild: create a new production order against the fixed BOM.

**⚠️ What if I make a mistake?**
- **"Could not disassemble. Check stock and BOM."** — the on-screen error means either the finished units aren't at the location you selected (find where they actually are — Ledger History helps) or the BOM math can't be applied. Fix the input, retry.
- **Disassembled more than intended:** Build them again with a production order — the two events sit honestly in history.
- **Components came back damaged from teardown:** Quarantine and let the manager post the loss correction, exactly like a damaged pick.

---

### How to Issue Supplies (internal use, not customer shipping)

**What is this?**
Handing out internal supplies (gloves, tape, service parts) charged to a **cost center** — so internal use never masquerades as customer shipments or theft-shrink.

**Who can do this? (Privileges Required)**
WAREHOUSE_MANAGER, or PICKER/ops where permitted. ADMIN configures cost centers first.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Field** → **Issue Supplies**
🖥️ Admin setup: **Admin** → **Organization** → **Cost Centers & Requisitions**

**Step-by-Step Instructions:**
1. Open **Issue Supplies** and select the requisition/cost center shown.
2. Scan or confirm the items being handed out.
3. Submit with **Issue Fact**.
4. **Back to list** for the next request.

**⚠️ What if I make a mistake?**
- **Issued the wrong item or quantity:** Stop and tell a manager — the fix is a correcting movement (return-to-stock), not deleting the issue. **Issue Fact disabled** usually means a missing cost center or quantity.
- **No cost centers listed:** An Admin must set up **Cost Centers & Requisitions** first.

---

### How to Manage a Technician Truck (van stock)

**What is this?**
A service van is a tiny warehouse on wheels. Stock moves onto the van (**Transfer to van**), gets used at customer sites (**Consume from van**), and the van's inventory must stay honest like any bin.

**Who can do this? (Privileges Required)**
Field technicians (PICKER-type roles) run their own truck; managers oversee transfers and corrections.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Field** → **Technician Truck**

**Step-by-Step Instructions:**
1. Open **Technician Truck** and click **Assign to me** to claim the truck.
2. Load up: **Transfer to van**, completing the scans shown.
3. On site: **Consume from van** for each part used on the job.
4. End of day: run a van count if your manager requires it.

**⚠️ What if I make a mistake?**
- **Transferred the wrong parts to the van:** Transfer back with a manager-approved reverse transfer — don't quietly restock the shelf without scanning.
- **Used a part but forgot to Consume:** Do it when you notice, or report same-day. The van count at day's end will catch it otherwise — better it catches nothing.
- **Consume shows less than the van holds:** Count the van. Never borrow unlabeled stock from another truck — each van's ledger is per-technician.
- **Offline in the field:** Scans cache like the warehouse floor; anything parked resolves later under **Exceptions → Sync Conflicts** (SOP 04).
- **Assign to me fails:** Another tech holds the truck — coordinate the handoff instead of sharing a session; attribution is what makes mistakes fixable.

---

### Logging Labor and Reporting Yield

**What is this?**
**Log Scrap** is for mistakes — a damaged component is written off to the scrap ledger. **Report Completion** is for success — finished goods are minted into the warehouse ledger. **Log Labor Time** is the hours people spent on a routing step (Cutting, Assembly, QA). Those hours are added to the **cost of the finished good** when you report yield.

**Who can do this? (Privileges Required)**
- **WAREHOUSE_MANAGER or PRODUCTION_SUPERVISOR** (plus ADMIN): **Log Scrap**.
- Floor operators and managers: **Log Labor Time** on an active production order.
- **WAREHOUSE_MANAGER / PRODUCTION_SUPERVISOR / ADMIN:** **Report Completion** after components are allocated / WIP.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Manufacturing** → **Production Orders** → **Open Workspace**

**Step-by-Step Instructions:**
1. **Release to Floor** locks the BOM and moves the order to WIP.
2. On each routing step, click **Log Labor Time**, pick the step, and enter hours.
3. If a part is ruined, **Log Scrap** (manager/supervisor). That is a write-off, not a completion.
4. When units are really finished, click **Report Completion** and enter the yield quantity. weGrowStock consumes allocated components and receives finished goods.

**⚠️ What if I make a mistake?**
- **Forgot to log labor before completion:** Post a **manual labor adjustment** on the same workspace, then complete (or tell a manager to add the hours next to the original timesheet). Do not pretend the build was free.
- **Clicked Report Completion too early:** A manager reverses with an attributed correction or **Disassemble**. The mint stays in history.

---

## Quick reference: "I messed up" cheat sheet (Manufacturing & field)

| Mistake | Can I fix it myself? | The fix |
|---|---|---|
| Wrong qty/BOM on order, not started | Yes (order creators) | **Cancel**, recreate — components auto-release |
| Built kits with the wrong BOM | No — Manager | Fix the BOM → **Disassemble** → rebuild on a new order |
| Clicked **Complete build** early | No — Manager | Manager reverses with an attributed correction |
| Broke a component mid-build | Report it | Quarantine + manager loss correction; scan a replacement |
| Wrong hours on the punch clock (typo / forgot to clock out) | No — Manager | Report the real times same-day; manager posts a signed correction |
| Time charged to wrong order | No — Manager | Manager reassigns the timesheet segment |
| Issued wrong supplies | No — Manager | Correcting return-to-stock movement |
| Wrong parts on the van | Manager-approved | Reverse transfer with scans |
| Van count doesn't match | Count honestly | Variance goes through the same manager approval as any cycle count |

**Golden rule:** a build is a trade recorded in ink — parts out, product in. Every undo is another recorded trade (**Disassemble**, corrections), never an eraser. And your hours follow the same law: you can't edit your own past time, but a manager can always write the honest correction next to it — so report time mistakes the same day, while everyone still remembers.

**Still stuck?** Ask the chat bubble (e.g. *"I completed a build with the wrong BOM — 20 units"*). Where a safe fix exists, the assistant proposes an **Action Draft** for a Manager to **Approve**.
