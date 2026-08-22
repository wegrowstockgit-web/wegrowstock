---
title: "Inventory Control & Compliance SOP (Beginner Guide)"
slug: "sop-inventory-compliance"
sourcePath: "docs/sops/03_inventory_and_compliance.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER"]
audienceLevel: "beginner"
routeHints: ["/products", "/import", "/settings/import", "/cycle-counts", "/compliance/lot-trace", "/replenishments", "/reports", "/exceptions", "/pallet-manifests"]
keywords: ["cycle count", "blind count", "variance", "LPN", "license plate", "lost pallet", "lot", "expired lot", "recall", "trace", "replenishment", "reverse transaction", "stock correction", "ledger history"]
---

# Inventory Control & Compliance — Beginner Playbook

This guide covers keeping the stock numbers honest: counting shelves (cycle counts), tracking pallets (LPNs), following batches (lots), refilling pick shelves (replenishment), and fixing wrong numbers the safe way.

---

## Before you start: 3 ideas that explain everything on these screens

**1. The shelf and the computer must agree.**
Every screen in this section exists to answer one question: *does the physical shelf match what the system believes?* When they disagree, we never "just change the number" — we record *why* with a signed correction.

**2. What is an LPN (License Plate Number)?**
An LPN is a barcode sticker for a whole pallet or container — like a license plate for a car. Instead of scanning 500 individual boxes, you scan one plate and the system knows everything riding on it. Plates are created with **Mint New LPN** and moved with the scanner's **LPN Move** mode.

**3. What is a lot?**
A lot is a batch of product made/received together (e.g. "all the yogurt from Tuesday's truck," with one expiry date). Lots matter for expiry-first picking (FEFO) and for recalls — if lot #123 is bad, we must find every customer who got it.

**Who does what here:**

| Role | Inventory powers |
|---|---|
| **OWNER / ADMIN** | Everything, including product master edits, imports, ledger reversals |
| **WAREHOUSE_MANAGER** | Approves count variances (**Approve Ledger Adjustment**), posts stock corrections (needs the **Adjust Inventory** permission), runs lot traces |
| **PICKER** | Performs counts, replenishment moves, LPN moves on the scanner. Cannot approve adjustments |
| **VIEWER** | Read-only everywhere |

---

### How to Look Up a Product and Its History (Ledger History)

**What is this?**
Every product page has a **Ledger History** tab — the permanent diary of every receive, pick, count, and correction for that SKU. When someone asks "why does the system say 40 when the shelf has 35?", the answer is always in this diary.

**Who can do this? (Privileges Required)**
Everyone can look (VIEWER included). **Reversing** a movement requires a WAREHOUSE_MANAGER or above with the **Adjust Inventory** permission.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inventory** → **Products** → click a product → tab **Ledger History**

**Step-by-Step Instructions:**
1. Open **Products** and use the search box to find the SKU.
2. Click the product row to open its peek panel.
3. Choose the **Ledger History** tab.
4. Read entries newest-first: each shows what happened, when, and **who did it**.

**⚠️ What if I make a mistake? (Reversing a wrong movement)**
- Found a movement that should never have happened (e.g. an accidental double receive)? A manager clicks **Reverse transaction** on that entry, then **Confirm Reversal** in the "Reverse Transaction?" dialog. This writes an equal-and-opposite entry next to the original — the mistake stays visible, the math becomes correct.
- **Reverse transaction greyed out?** Some movement types can't be reversed online (e.g. part of a completed shipment). Use a cycle count or a manager stock correction instead.
- You can also ask the assistant: *"Reverse the duplicate receive on SKU WIDGET-S from this morning."* It proposes an **Action Draft**; a Manager clicks **Approve**.

---

### How to Do a Cycle Count (counting a shelf)

**What is this?**
Instead of shutting the warehouse once a year to count everything, we count a few bins every day. It is a **blind count**: the scanner does NOT show you how many the system expects — because if you saw "system says 48," you'd be tempted to just type 48. You count what your eyes see.

**Who can do this? (Privileges Required)**
PICKER (or any floor role) performs the count. Only a WAREHOUSE_MANAGER or above can approve a large variance (**Approve Ledger Adjustment**) or order a **Request Recount**.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inventory** → **Cycle counts** (on the handheld/floor device)

**Step-by-Step Instructions:**
1. Open **Cycle counts** and take the next task.
2. Walk to the bin, scan the **bin barcode**, then scan the **product barcode**.
3. Physically count every unit. Count twice if you were interrupted.
4. Type the number you counted and confirm. If it matches the system, tap **Confirm Match** — done.
5. If it doesn't match, the count is submitted as a variance. Small variances may show **AUTO APPROVED**; large ones show **PENDING MANAGER REVIEW** — that is normal, keep working.
6. *Manager:* open the pending count and click **Approve Ledger Adjustment** (writes the correction) or **Request Recount** (sends someone to count again). Chips: **PENDING**, **AUTO APPROVED**, **APPROVED**, **RECOUNT REQUESTED**.

**⚠️ What if I make a mistake?**
- **Fat-fingered the number (typed 1000 instead of 10):** Don't panic — the system is built for exactly this. A huge variance does **not** silently change stock; it parks as **PENDING MANAGER REVIEW**. Tell your manager "that 1000 was a typo," and they click **Request Recount**. Count again, submit the real number. Your typo remains in the log — and that's fine.
- **Counted the wrong bin:** Tell the manager before approval; they'll request a recount on the right bin. If it was already approved, the manager posts a correction — same ledger rule as always.
- **Tempted to type the number a coworker remembers ("it's always 48"):** Never. The entire point of blind counting is eyes-on-shelf. A wrong honest count is fixable; a fake count poisons every order that trusts it.
- **Count parked in Sync Conflicts:** You counted while the badge said **Offline - Caching Scans** and the bin changed meanwhile. A manager resolves it under **Inventory → Exceptions → Sync Conflicts** (see SOP 04).

---

### How to Work with LPNs / Pallets (and what to do when one goes missing)

**What is this?**
An LPN is the pallet's "license plate." Building a pallet = **Mint New LPN**, stack cartons, scan them to the plate. Moving a pallet = scanner **LPN Move** mode: scan plate, scan destination. One scan moves everything on the plate at once.

**Who can do this? (Privileges Required)**
PICKER and WAREHOUSE_MANAGER on the scanner. Declaring an LPN lost (writing off its contents) is a manager decision.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Outbound** → **Fulfillment** → scan mode **LPN Move** or **Build Pallet**
🖥️ Pallet paperwork: **Outbound** → **Pallet manifests**

**Step-by-Step Instructions (moving a pallet):**
1. In **Fulfillment**, set scan mode to **LPN Move**.
2. Scan the LPN label on the pallet.
3. Move the pallet physically.
4. Scan the destination location barcode. Done — every carton on the plate now "lives" at the new spot.

**⚠️ What if I make a mistake?**
- **Physically lost an LPN (plate exists in the system, pallet is nowhere):** Do not ignore it and do not zero it out yourself. Tell a WAREHOUSE_MANAGER. The honest sequence is: search the likely spots → cycle count the last known location → if truly gone, the manager posts an attributed write-off correction for the LPN contents. The loss becomes a visible ledger event (which finance needs for shrink reporting), not a mystery.
- **Moved the pallet but forgot to scan the move:** The system still shows the old location, and the next picker walks to an empty spot. Go back and do the **LPN Move** scan now, or tell a manager — never leave the shelf and computer disagreeing.
- **Scanned the wrong destination:** Do another **LPN Move** to the correct location. Two honest moves in the log are perfectly fine.
- **LPN label torn/unreadable:** Ask a manager to reprint; never handwrite a guess or borrow a plate from another pallet.

---

### How to Trace a Lot (recalls & expired stock)

**What is this?**
Lot Trace answers "where did this batch go?" — which bins still hold it and which customers received it. You use it when a supplier announces a recall or when you find expired product on a shelf.

**Who can do this? (Privileges Required)**
WAREHOUSE_MANAGER, ADMIN, OWNER run traces and exports. VIEWER can trace read-only. PICKERs report suspicious/expired stock to a manager.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inventory** → **Lot Trace**

**Step-by-Step Instructions:**
1. Open **Lot Trace**.
2. Type the lot number (from the product label) and click **Trace**.
3. Review the results: on-hand bins holding the lot, and orders/customers that received it.
4. If customers are affected, click **Export affected customers** and hand the list to customer service.

**⚠️ What if I make a mistake? (and: "I found expired lots on the shelf")**
- **Found expired product while picking or counting:** Do not pick it, do not bin-trash it quietly. Tell a manager. The correct sequence: quarantine the stock (move it to a quarantine location so it can't be picked), run **Lot Trace** to see if any of that lot already shipped, then the manager posts the disposal as an attributed correction. FEFO picking exists to prevent this, but late discoveries still happen — reporting one is doing your job well, not causing trouble.
- **Traced the wrong lot number:** Just trace again — tracing is read-only and changes nothing.
- **No results for a real lot:** The lot may not have been captured at receive (someone skipped the lot field). Escalate — this is a receiving-discipline problem a manager must fix at the dock.
- **Never** edit history to "hide" a lot. Quarantine + trace + corrections is the entire recall playbook.

---

### How to Replenish a Pick Face (refill the small shelf)

**What is this?**
Pickers pick from small, easy-to-reach bins ("pick faces"). Bulk stock lives higher up in reserve. Replenishment is the directed move that refills a pick face from reserve before waves stall.

**Who can do this? (Privileges Required)**
PICKER or WAREHOUSE_MANAGER.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inventory** → **Replenishments** (the Fulfillment screen links here when it shows **Replenishments Needed**)

**Step-by-Step Instructions:**
1. Open **Replenishments** and take a task.
2. Scan the **from** bin (reserve), move the stock physically, scan the **to** bin (pick face).
3. Return to **Fulfillment** and keep picking.

**⚠️ What if I make a mistake?**
- **Moved to the wrong bin:** Ask a manager for a corrective move. Don't improvise a reverse scan unless the screen offers one.
- **No tasks but the pick face is empty:** Reserve may be empty too — the real fix is inbound (a PO), not a workaround. Tell your manager.

---

### How to Bulk-Import Products (CSV)

**What is this?**
Loading many SKUs at once from a spreadsheet instead of typing them one by one. The system pre-checks ("preflight") every row before anything is created, so a bad spreadsheet can't hurt the floor.

**Who can do this? (Privileges Required)**
ADMIN or OWNER (managers where permitted). Not PICKER/VIEWER.

**Where to go in weGrowStock:**
🖥️ **Import** screen (also **Settings → Import** for Admin/Owner)

**Step-by-Step Instructions:**
1. Open **Import** and click **Download Template**.
2. Fill the spreadsheet offline, one product per row.
3. Upload it. Read the preflight chips per row: **READY TO IMPORT**, **MISSING PRODUCT**, **MISSING LOCATION**, **MISSING UOM**, **VALIDATION ERROR**.
4. Fix issues using **Create missing products based on CSV data** or **Map to existing**, or edit the file and re-upload.
5. Click **Import N ready row(s)** only when the rows you want are green.

**⚠️ What if I make a mistake?**
- **Imported with a typo'd product name:** Edit the product under **Products** — master-data fixes are safe.
- **Imported duplicates:** Point future work at the correct SKU and ask an Admin to retire the twin. Movement history on both stays.
- **Wrong quantities in your head?** Imports don't set stock levels by themselves — quantity truth only enters through receiving, counts, and corrections. So a bad import can't corrupt on-hand.

---

## Quick reference: "I messed up" cheat sheet (Inventory)

| Mistake | Can I fix it myself? | The fix |
|---|---|---|
| Typed 1000 instead of 10 in a blind count | Parks automatically | Variance goes **PENDING MANAGER REVIEW**; manager clicks **Request Recount** |
| Counted the wrong bin | Tell manager | Recount the right bin; correction if already approved |
| Lost an LPN / pallet missing | No — Manager | Search → cycle count last location → attributed write-off |
| Moved pallet without scanning | Yes, immediately | Do the **LPN Move** scan now, or tell a manager |
| Found expired lot on shelf | Report it | Quarantine → **Lot Trace** → manager posts disposal |
| Wrong/duplicate receive in history | No — Manager | **Reverse transaction** → **Confirm Reversal** on Ledger History |
| Import row errors | Yes (Admin) | Fix file, re-run preflight; never force red rows |
| Count/move parked offline | No — Manager | **Exceptions → Sync Conflicts** (SOP 04) |

**Golden rule:** big variances don't auto-apply — the system parks them for a human. So the fastest way through any counting mistake is the honest sentence: *"I typed it wrong, please recount."*

**Still stuck?** Click the chat bubble and describe it plainly (e.g. *"Bin A-3 count is pending review because I typo'd it"*). Action Drafts that adjust stock always wait for a Manager's **Approve**.
