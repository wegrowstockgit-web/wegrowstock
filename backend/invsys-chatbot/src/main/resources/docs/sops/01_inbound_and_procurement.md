---
title: "Inbound Receiving & Procurement SOP (Beginner Guide)"
slug: "sop-inbound-procurement"
sourcePath: "docs/sops/01_inbound_and_procurement.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER", "SUPPLIER"]
audienceLevel: "beginner"
routeHints: ["/purchase-orders", "/suppliers", "/purchasing/suppliers", "/mrp", "/returns", "/mesh-network", "/inbound/receive", "/purchasing/receive", "/supplier-portal", "/dashboard", "/exceptions"]
keywords: ["purchase order", "PO", "supplier", "receive", "putaway", "quarantine", "over-receive", "over-receipt", "tolerance", "duplicate PO", "wrong price", "GS1 barcode", "EDI", "invoice upload", "3-way matching", "3-way mismatch", "AP invoice", "UoM", "unit of measure", "cross-dock", "lot", "expiration"]
---

# Inbound & Procurement — Beginner Playbook

This guide is written for people who have **never used a warehouse system before**. It explains buying goods (Purchase Orders), setting up suppliers, receiving trucks at the dock, and — most importantly — **how to fix every common mistake** without breaking anything.

---

## Before you start: 4 things every new user must know

**1. What is weGrowStock?**
It is the system of record for everything the company owns and moves. If a box arrives, ships, breaks, or gets counted — it must be recorded here.

**2. What is the "immutable ledger"?**
Every stock movement is written down permanently, like ink in a paper logbook. **Nothing is ever erased.** If you make a mistake, the fix is a *new* entry that corrects the old one (a "reversal" or "correction"), signed with your name. This is a good thing: you can never destroy data, so mistakes are always fixable and always visible.

**3. Which role am I?** Ask your manager which of these you are — it controls which buttons you can see:

| Role | Who they usually are | What they can do (inbound) |
|---|---|---|
| **OWNER / ADMIN** | Company owner, office admin | Everything: create/cancel POs, edit suppliers, approve exceptions, manage users |
| **WAREHOUSE_MANAGER** | Floor supervisor | Create/cancel POs, receive, approve over-receipts and stock corrections |
| **PICKER** | Floor worker with a scanner | Receive and putaway with the scanner. Cannot create POs or edit suppliers |
| **VIEWER** | Read-only office staff | Look at everything, change nothing |
| **SUPPLIER** | Your vendor's contact | Only sees the private portal link you send them — never your warehouse |

If a button described below is missing or greyed out for you, **that is on purpose** — your role does not have that privilege. Ask a Warehouse Manager or Admin instead of trying to work around it.

**4. The assistant can help you fix things.**
Click the blue chat bubble (bottom-right corner) and describe your problem in plain words, e.g. *"I received 100 units but the truck only had 10."* When the assistant knows a safe fix, it shows an **Action Draft** card with an **Approve** and a **Dismiss** button. Nothing happens until a person clicks **Approve** — and drafts that touch stock usually require a Manager or Admin to approve.

---

### How to Add a New Supplier

**What is this?**
A supplier is a company you buy from. Before you can create a Purchase Order, the supplier must exist in the system with the correct name and payment terms — otherwise your orders and reports point at the wrong company.

**Who can do this? (Privileges Required)**
OWNER, ADMIN, or WAREHOUSE_MANAGER. VIEWERs can look but not edit. PICKERs do not see this screen.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **Suppliers**

**Step-by-Step Instructions:**
1. Click **Inbound** in the left sidebar, then **Suppliers**.
2. Use the search box at the top of the table first — **make sure the supplier does not already exist** (search part of the name, e.g. "Acme"). This prevents duplicates.
3. Click the button to add a new supplier.
4. Type the legal company name exactly as it appears on their invoices.
5. Fill in payment terms (e.g. NET30 = you pay within 30 days) and contact details.
6. Save. The supplier now appears when you create a **New PO**.

**⚠️ What if I make a mistake?**
- **Typo in the supplier name?** Open the supplier and correct it. Name fixes are safe at any time — they do not touch stock history.
- **Created a duplicate supplier ("Acme Parts" and "Acme Parts Inc")?** Do not keep using both. Pick one as the real record, point new POs at it, and ask an ADMIN to retire the duplicate. Old POs keep their history — that is fine, the ledger never lies about the past.
- **Wrong payment terms?** Fix them before the next PO is sent. Already-sent POs keep the old terms; tell your finance person.

---

### How to Create a Purchase Order (PO)

**What is this?**
A Purchase Order is the official "we are buying this" document. It tells the supplier what you want, tells your own dock team what truck to expect, and later lets finance check that you were billed for exactly what arrived.

**Who can do this? (Privileges Required)**
OWNER, ADMIN, or WAREHOUSE_MANAGER. Some workspaces also require the **Approve Purchase Orders** permission for final approval — if your PO sits waiting after you submit it, an approver needs to act. PICKERs and VIEWERs cannot create POs.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **Purchase Orders** (shortcut: **New purchase order** button on the Dashboard)

**Step-by-Step Instructions:**
1. Open **Purchase Orders**. The list is a procurement data grid, not just a name list:
   - **Created Date** — when the PO was opened (MMM DD, YYYY).
   - **Expected** — promised delivery / ETA from the vendor.
   - **Total** — line qty × unit cost, plus freight and duties.
   - **Progress** — units received vs units ordered (`received / ordered`).
   - **Vendor Ref** is optional; turn it on from the Columns menu when you need the supplier's own PO number.
2. **Search first!** Type the supplier name or product into the search box above the table. If an open PO for the same goods already exists, add to it or wait — do not create a twin.
3. Click **New PO**.
4. Choose the supplier (from the Suppliers list you set up above).
5. Choose the warehouse the goods should arrive at.
6. Add lines: for each product, pick the SKU, type the **quantity**, and check the **unit price** against the supplier's quote. Read prices twice — this number flows into what the company pays.
7. Click **Create purchase order**. The status chip shows **DRAFT**.
8. When you are sure everything is right, submit it. The chip becomes **SUBMITTED** — now the dock team can see it and the supplier can be notified.
9. **Standard suppliers:** when the vendor emails a shipping confirmation, open the PO Workspace and click **Mark In Transit**. Optional fields: vendor reference, tracking number, expected delivery date. After this, **Cancel PO** is hidden — the freight is already on a truck.
10. **Mesh Network suppliers:** do nothing. weGrowStock listens to the partner warehouse. When they ship, the PO flips to **IN TRANSIT** and tracking/carrier appear under the status chip. The **Mark In Transit** button stays disabled.
11. Truck cancelled after a manual transit? Click **Revert to Submitted** to clear tracking and put the PO back in the waiting queue. The chatbot cannot edit posted ledger rows.

**⚠️ What if I make a mistake?**
- **Duplicate PO (you clicked create twice, or two people ordered the same thing):** While the extra PO is still **DRAFT** or **SUBMITTED** and *nothing has been received*, open it and click **Cancel**. The PO stays in the list marked **CANCELLED** — that is correct, cancelled documents are kept as history, not deleted. If goods were *already received* against the duplicate, do **not** cancel; tell a WAREHOUSE_MANAGER — the received stock is real and must stay on the ledger, and the manager will sort the paperwork with a correction.
- **Wrong price (typed $100.00 instead of $10.00):** If nothing is received yet, edit the line or **Cancel** and recreate. If goods were already received at the wrong cost, an ADMIN/OWNER fixes the valuation through the invoice-reconcile step (see "Supplier invoice upload" below) — never by deleting the receive.
- **Wrong quantity (typed 1000 instead of 100):** Same rule — edit while **DRAFT**, **Cancel** if submitted but not received. If the dock already received the wrong count, a WAREHOUSE_MANAGER opens the **PO Workspace** and clicks **Reverse Receipt**. That posts an offsetting ledger entry. The original receive stays in history.
- **Wrong supplier selected:** **Cancel** the PO and create a new one with the right supplier. Do not "just receive it anyway" — spend reports and the supplier portal would point at the wrong vendor.
- You can also ask the assistant: *"Cancel PO-2026-00042, it's a duplicate."* It will show an **Action Draft** for a manager to **Approve**.
- **The chatbot cannot edit posted ledger rows.** If someone complains that a received quantity is wrong, do **not** invent a typed correction. Instruct them: open the PO → **Open Workspace** → **Reverse Receipt**. Only a WAREHOUSE_MANAGER or ADMIN can press that button.

---

### How to Ingest a Supplier Invoice (Document Upload / EDI)

**What is this?**
When the supplier bills you, you attach their invoice to the PO so the system can compare "what we ordered" vs "what arrived" vs "what they charged." This catches wrong prices before the company pays.

**Who can do this? (Privileges Required)**
OWNER, ADMIN, or WAREHOUSE_MANAGER. VIEWERs cannot upload.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **Purchase Orders** → open the PO → document upload panel

**Step-by-Step Instructions:**
1. Open the Purchase Order the invoice belongs to.
2. Click **Upload invoice document** and drop the PDF/image (or paste OCR JSON if your site uses EDI feeds).
3. The system reads the document and shows extracted lines and totals next to your PO lines.
4. Compare them. Green matches are fine; mismatched prices or quantities are highlighted.
5. Click **Upload & reconcile** only when the match is correct.

**⚠️ What if I make a mistake?**
- **Attached the wrong PDF:** Upload the correct document and reconcile again. The wrong attachment is superseded, not secretly deleted.
- **Reconciled against a wrong price:** Tell an ADMIN/OWNER — cost corrections are a finance-level fix; stock quantities are untouched.
- **Reconcile button blocked?** Usually the dock has not finished receiving, so quantities cannot match yet. Finish the receive first.

---

### How to Receive a Purchase Order (Dock Receive & Directed Putaway)

**What is this?**
When the truck arrives, we log what boxes were actually dropped off so we can (a) trust our stock numbers and (b) pay the supplier only for what really arrived. "Putaway" means the scanner then tells you exactly which shelf (bin) each box belongs in.

**Who can do this? (Privileges Required)**
PICKER and WAREHOUSE_MANAGER do this on the floor. Office roles can launch it via **Floor receive** on the PO. Approving an **over-receipt** (more arrived than ordered, beyond tolerance) requires a WAREHOUSE_MANAGER or above.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **Purchase Orders** → open the PO → **Floor receive**
(or the standalone **Inbound Receive** screen on the handheld scanner)

**Step-by-Step Instructions:**
1. Open the PO and click **Floor receive** (or **Receive stock** / **Receive**).
2. Scan the purchase order / product barcodes as the screen prompts.
3. Count the physical items in the carton. Type or confirm **the number you actually counted** — not the number on the paperwork.
4. For a multi-line delivery, either receive line-by-line or use **Receive All (N)** when the screen offers it and you have truly checked every line.
5. For **regulated items (FSMA / DSCSA)** the screen **hard-stops** until you enter the **Manufacturer Lot Number** and **Expiration Date** from the label. Do not invent a generic lot (e.g. "LOT1", today's date, or the PO number).
6. Tap **Continue to Putaway**. The screen names a bin — walk there and scan the bin barcode when it says "Confirm putaway — scan bin." If a **Cross-Dock** alert appears, follow **Cross-Docking vs. Putaway** below instead of the reserve rack.
7. Use **Receive another line** to keep working the same truck.
8. When each line shows **Received**, you are done. The PO chip moves to **PARTIALLY RECEIVED** or **RECEIVED**.

**⚠️ What if I make a mistake?**
- **Typed 100 instead of 10 (over-receive / fat-finger):** Stop. Do not "receive negative" or invent a second receive to cancel it out. A WAREHOUSE_MANAGER opens the PO → **Open Workspace** → **Reverse Receipt**. That posts an offsetting ledger row. The original receive stays in history. The chatbot cannot edit posted ledger rows — if a user asks the assistant to "change the quantity," tell them to use **Reverse Receipt** in the PO Workspace instead.
- **Scanned the wrong GS1 barcode / wrong product:** If you notice before confirming — rescan. If you already confirmed, the wrong SKU's count is now wrong in that bin. Report it: a manager fixes it with a stock correction or a directed move. Never type barcode digits by hand from memory; if a label is unreadable, that is a supplier labeling problem to escalate.
- **Put stock in the wrong bin:** Ask a manager for a bin **move** (or use the scanner's LPN Move mode if your site allows). Do not just carry the box to the right shelf without scanning — then the computer and the shelf disagree, and the next picker gets sent to an empty bin.
- **Box is damaged / leaking / crushed (Quarantine):** Do not receive it into a normal sellable bin. Follow your screen's damaged/quarantine option so the stock lands in a **quarantine** location — it exists in the ledger (we did physically get it) but can never be picked for a customer. A manager then decides: return to supplier (see **Inbound → Returns**), scrap with a correction, or release if it was cosmetic.
- **More boxes arrived than ordered:** Stop. Do **not** force the receive. Follow **Handling Over-Receipts** below.
- **The screen shows "Offline - Caching Scans":** Keep working carefully; your scans are saved on the device. If one conflicts later, it appears in **Inventory → Exceptions → Sync Conflicts**, where a manager chooses **Approve & Re-process** or **Discard Transaction** (see SOP 04).

---

### Handling Over-Receipts

**What is this?**
An over-receipt is when the truck (or a typed count) is **higher than the PO line**. weGrowStock enforces **Over-Receipt Tolerances** — a percentage set by Admin under Settings. Anything beyond that limit is blocked. The system will not silently accept extra boxes, because extra units become unbilled inventory and wreck AP matching later.

**Who can do this? (Privileges Required)**
Floor workers (PICKER / FLOOR_WORKER) receive the real count and stop when blocked. Only a **WAREHOUSE_MANAGER** (or above) can review the overage and approve a **tolerance override**. Nobody should invent a second receive to sneak the extras in.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **Inbound Receive** (`/purchasing/receive` or `/inbound/receive`)
🖥️ Manager override: the parked over-receipt exception, or Settings → **Over-receipt tolerance (%)** (Admin)

**Step-by-Step Instructions:**
1. Count what is physically on the dock. Type that number — not the PO quantity and not a guess.
2. If the system **blocks** the receive, **stop**. Do not retry with a fake smaller quantity to "get past" the check, and do not force the extra boxes onto a sellable bin.
3. Call a Warehouse Manager. They review the overage against the PO and the tolerance setting.
4. The manager either **approves a tolerance override** (the extras are real and the company will accept them) **or** you **refuse the extra boxes** and send them back with the driver / open an RTV.
5. Only after that decision do you finish putaway for the accepted quantity.

**⚠️ What if I make a mistake?**
- **Forced a receive around the block:** Tell a manager immediately. The extras must be reversed or returned — do not hide them in a random bin.
- **Refused extras but already scanned them:** A manager uses **Reverse Receipt** on the PO Workspace, then you refuse the physical overage.
- **Tolerance feels too tight/loose:** That is an Admin Settings change, not a floor workaround.

---

### Unit of Measure (UoM) Best Practices

**What is this?**
A Unit of Measure is the size of the "1" you type at receive: **Each**, **Case**, or **Pallet**. The PO line and the physical label must use the same UoM. Receiving 5 **Pallets** when the PO meant 5 **Cases** multiplies on-hand and **inventory value** by the case/pallet conversion — finance will think the company owns far more stock than it paid for.

**Who can do this? (Privileges Required)**
Floor workers confirm the UoM dropdown before they tap receive. Only a **WAREHOUSE_MANAGER** can **Reverse Receipt** after a wrong UoM posts.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **Inbound Receive** (`/purchasing/receive`)
🖥️ Recovery: PO Workspace → **Reverse Receipt**

**Step-by-Step Instructions:**
1. Read the physical label: Each / Case / Pallet (or inner pack).
2. Look at the **UoM dropdown** on the receive line. It must match that label — not what you assume from the PO qty.
3. Receive only after the UoM and the count both match what is in your hands.
4. If you already posted the wrong UoM, **stop putting away**. Call a manager. They click **Reverse Receipt** immediately, then you re-receive in the correct UoM.
5. Never "fix" a UoM miss by typing a smaller quantity of the wrong unit.

**⚠️ What if I make a mistake?**
- **Received 5 Pallets instead of 5 Cases:** Manager **Reverse Receipt** now. Do not leave the inflated value on the ledger overnight — every pick and invoice after that uses the wrong cost.
- **UoM on the product master is wrong:** That is an Admin catalog fix under **Products**. Do not invent a receive workaround.

---

### Cross-Docking vs. Putaway

**What is this?**
**Putaway** means walk the box to a reserve or pick-face rack. **Cross-Dock** means an urgent **backorder** is already waiting — the system wants those units in **Outbound Fulfillment staging**, not in the racks. A Cross-Dock alert at scan is the computer saying "this SKU is late for a customer; do not bury it in storage."

**Who can do this? (Privileges Required)**
PICKER / FLOOR_WORKER follows the overlay. WAREHOUSE_MANAGER can override a mis-stage with a directed move.

**Where to go in weGrowStock:**
🖥️ **Inbound Receive** — watch for the **Cross-Dock** overlay after you scan the product
🖥️ Staging path is shown on the overlay (e.g. ship lane / S-01). Do not use the reserve bin it tells you to bypass.

**Step-by-Step Instructions:**
1. Scan the PO, then the product.
2. **Always check for a Cross-Dock alert.** If the overlay appears, read the staging path.
3. Move the items **directly to Outbound Fulfillment staging**. Confirm the staging barcode.
4. Do **not** put them away in the racks, even if that bin is closer or habitual.
5. If you already put them away, tell a manager — they move the stock from reserve to staging. Do not silently carry boxes without scanning.

**⚠️ What if I make a mistake?**
- **Put a cross-dock carton into reserve:** The backorder stays short and the next picker walks to an empty ship lane. Manager directed-move to staging now.
- **Ignored the overlay because "we always put away":** That is the most expensive inbound habit. The overlay exists only when a customer is already waiting.

---

### How to Return Goods to a Supplier (RTV)

**What is this?**
When received goods are wrong, damaged, or excess, you send them back to the supplier and the ledger records stock leaving quarantine/your warehouse.

**Who can do this? (Privileges Required)**
OWNER, ADMIN, or WAREHOUSE_MANAGER. PICKERs and VIEWERs do not see this screen.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **Returns**

**Step-by-Step Instructions:**
1. Open **Returns**.
2. Create a return for the supplier and PO in question, choosing the affected lines and quantities.
3. Follow the on-screen steps until the goods are physically shipped back and the return is complete.

**⚠️ What if I make a mistake?**
- **Returned the wrong quantity:** The return is a ledger movement like any other — a manager posts a correction; nothing is erased.
- **Should have quarantined instead of returned (or vice versa):** Tell a manager immediately, before the truck leaves.

---

### Supplier Delivery Portal (for your vendors)

**What is this?**
A private web link you send the supplier so they can confirm a delivery date — without ever logging into your warehouse.

**Who can do this? (Privileges Required)**
The SUPPLIER contact uses the link. Your ADMIN/OWNER re-sends links. Warehouse staff just watch the confirmed date appear on the PO.

**Where to go in weGrowStock:**
🖥️ The supplier opens the secure link from their email. Your team monitors: Sidebar Navigation → **Inbound** → **Purchase Orders**.

**Step-by-Step Instructions (for the supplier):**
1. Open the link.
2. Check the PO lines and quantities.
3. Enter the delivery date, click **Submit delivery date**.

**⚠️ What if I make a mistake?**
- **Supplier submitted a wrong date:** Re-share the link; they submit an updated date.
- **Link expired or blank:** Ask your Admin to resend it for that PO.
- Never ask a supplier to enter *received quantities* — receiving happens only on your dock.

---

### MRP Reorder (automatic buying suggestions)

**What is this?**
The system watches safety stock and lead times and suggests what to reorder. One click turns suggestions into draft POs, grouped by supplier.

**Who can do this? (Privileges Required)**
OWNER, ADMIN, or WAREHOUSE_MANAGER, with the **Run MRP Reorder** permission and the MRP module enabled. Not visible to PICKER/VIEWER.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **MRP reorder**

**Step-by-Step Instructions:**
1. Open **MRP reorder** and review the suggested lines (SKU, supplier, suggested qty, capital estimate).
2. Click **Refresh** if you just received stock and want fresh math.
3. Click **Consolidate & Create Draft POs**. The system creates draft POs per supplier.
4. Open **Purchase Orders**, review each draft (prices! quantities!), then submit.

**⚠️ What if I make a mistake?**
- **Consolidated too early / created draft POs you don't want:** They are only **DRAFT** — open each and click **Cancel**. Nothing was sent to a supplier and no stock moved.

---

### AP 3-Way Matching

**What is this?**
Paying a vendor is like checking three papers that must tell the same story: (1) the **Purchase Order** (what we asked for), (2) the **Dock Receipt** (what actually arrived), and (3) the **Vendor's Bill** (what they want us to pay). weGrowStock reads the bill with OCR and compares those three. **Matched** means they agree. **Discrepancy** means a qty, price, or receipt is off. **Pending** means the bill is still being read.

**Who can do this? (Privileges Required)**
**OWNER, ADMIN, or WAREHOUSE_MANAGER** reviews AP ingestions. Floor pickers receive freight; they do not approve vendor bills.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **Suppliers** → **Open Workspace** → tab **AP Invoices**
(Upload still lives on **Purchase Orders** if you are ingesting a new PDF.)

**Step-by-Step Instructions:**
1. Open the supplier workspace.
2. Click **AP Invoices**.
3. Read the **3-Way Match** chip: Matched / Discrepancy / Pending.
4. If it is a discrepancy, open the linked PO and fix the receive or the bill **before** anyone pays. Do not silently edit a posted receipt.

**⚠️ What if I make a mistake?**
- **Paid a mismatched bill:** Tell finance. The OCR match log stays. Correct the receive with a reversing receipt if stock was wrong; AP then re-matches. Never delete the vendor bill.

---

### AP 3-Way Match Failures

**What is this?**
A **3-Way Mismatch** means the AP Invoice is blocked because the vendor's quantities or prices do **not** match our **Purchase Order** and our **Dock Receipt**. weGrowStock will not let finance pay until someone finds which of the three documents is wrong.

**Who can do this? (Privileges Required)**
**FINANCE_ADMIN**, OWNER, ADMIN, or WAREHOUSE_MANAGER reviews the three documents. Floor workers do not reject AP invoices.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inbound** → **Suppliers** (`/purchasing/suppliers`) → **Open Workspace** → tab **AP Invoices**

**Step-by-Step Instructions (how to find the discrepancy):**
1. Open the supplier workspace and click **AP Invoices**.
2. Read the **3-Way Match** chip. **Discrepancy** means at least one of qty, price, or receipt is off.
3. Compare the three documents in this workspace, side by side:
   - **PO** — what we ordered and the unit price we agreed.
   - **Dock Receipt** — what the floor actually scanned in.
   - **Vendor bill** — what they want us to pay.
4. Decide who is wrong:
   - **Vendor overbilled** (bill qty/price higher than PO + receipt) → **reject the AP Invoice**. Do not pay. Ask the vendor for a corrected bill.
   - **Dock miscounted** (receipt does not match the boxes that are physically here) → a **Manager must post a stock correction** (or Reverse Receipt + re-receive). Then AP can re-match.
   - **PO price was typed wrong** and nothing should have been received at that cost → finance/admin corrects the commercial document; do not silently edit the posted receipt.
5. Only when the three agree does the chip go **Matched** and AP can post.

**⚠️ What if I make a mistake?**
- **Rejected a correct vendor bill:** Re-ingest or re-open the invoice after you confirm the PO and receipt. The mismatch log stays.
- **Posted a stock correction when the vendor was at fault:** Tell finance. You now have extra/missing stock on the ledger that needs a second signed correction — never delete the first one.

---

## Quick reference: "I messed up" cheat sheet (Inbound)

| Mistake | Can I fix it myself? | The fix |
|---|---|---|
| Typo in supplier/customer name | Yes (office roles) | Edit the record — master data fixes are safe |
| Duplicate PO, nothing received | Yes (PO creators) | **Cancel** the extra PO; it stays as CANCELLED history |
| Duplicate PO, goods received | No — Manager | Manager posts a correction; never cancel a received PO |
| Wrong price on PO | Before receive: yes. After: ADMIN/finance | Edit/Cancel, or fix cost at invoice reconcile |
| Typed 100, meant 10 at receive | No — Manager | Manager **Reverse Receipt**; big variances auto-park in **Exceptions** |
| Supplier sent more than ordered (over-receipt blocked) | No — Manager | Do not force the receive. Manager approves a tolerance override, or refuse the extra boxes |
| AP Invoice blocked (3-Way Mismatch) | Finance / Manager | Compare PO, dock receipt, and vendor bill. Reject the bill if the vendor overbilled; Manager stock-corrects if the dock miscounted |
| Wrong barcode / wrong SKU received | No — Manager | Report immediately; manager corrects the ledger |
| Wrong bin at putaway | Picker reports; Manager or LPN Move fixes | Directed move, never a silent carry |
| Damaged carton | Any receiver flags it | Receive into **Quarantine**, then RTV chargeback — never reverse the receipt so AP never saw the freight |
| Wrong UoM (Pallets vs Cases) | No — Manager | **Reverse Receipt** immediately, then re-receive in the label UoM |
| Put a backorder SKU into the racks | Picker + Manager | Follow the **Cross-Dock** overlay to outbound staging — do not put away |
| Scan stuck after offline work | No — Manager | **Exceptions → Sync Conflicts** → **Approve & Re-process** / **Discard Transaction** |

**Golden rule:** the ledger is permanent. You cannot delete a mistake — and you don't need to. Say what happened, and the fix gets written next to it with a name and timestamp. The only truly bad move is hiding an error by inventing counts.

**Still stuck?** Click the chat bubble and describe the problem in plain words. If the assistant offers an **Action Draft**, a Manager can click **Approve** to execute the fix safely.
