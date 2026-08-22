---
title: "Inbound Receiving & Procurement SOP (Beginner Guide)"
slug: "sop-inbound-procurement"
sourcePath: "docs/sops/01_inbound_and_procurement.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER", "SUPPLIER"]
audienceLevel: "beginner"
routeHints: ["/purchase-orders", "/suppliers", "/mrp", "/returns", "/mesh-network", "/inbound/receive", "/supplier-portal", "/dashboard", "/exceptions"]
keywords: ["purchase order", "PO", "supplier", "receive", "putaway", "quarantine", "over-receive", "duplicate PO", "wrong price", "GS1 barcode", "EDI", "invoice upload"]
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
1. Open **Purchase Orders**.
2. **Search first!** Type the supplier name or product into the search box above the table. If an open PO for the same goods already exists, add to it or wait — do not create a twin.
3. Click **New PO**.
4. Choose the supplier (from the Suppliers list you set up above).
5. Choose the warehouse the goods should arrive at.
6. Add lines: for each product, pick the SKU, type the **quantity**, and check the **unit price** against the supplier's quote. Read prices twice — this number flows into what the company pays.
7. Click **Create purchase order**. The status chip shows **DRAFT**.
8. When you are sure everything is right, submit it. The chip becomes **SUBMITTED** — now the dock team can see it and the supplier can be notified.

**⚠️ What if I make a mistake?**
- **Duplicate PO (you clicked create twice, or two people ordered the same thing):** While the extra PO is still **DRAFT** or **SUBMITTED** and *nothing has been received*, open it and click **Cancel**. The PO stays in the list marked **CANCELLED** — that is correct, cancelled documents are kept as history, not deleted. If goods were *already received* against the duplicate, do **not** cancel; tell a WAREHOUSE_MANAGER — the received stock is real and must stay on the ledger, and the manager will sort the paperwork with a correction.
- **Wrong price (typed $100.00 instead of $10.00):** If nothing is received yet, edit the line or **Cancel** and recreate. If goods were already received at the wrong cost, an ADMIN/OWNER fixes the valuation through the invoice-reconcile step (see "Supplier invoice upload" below) — never by deleting the receive.
- **Wrong quantity (typed 1000 instead of 100):** Same rule — edit while **DRAFT**, **Cancel** if submitted but not received. If partially received, receive only what actually arrived; the leftover open quantity can be cancelled by a manager.
- **Wrong supplier selected:** **Cancel** the PO and create a new one with the right supplier. Do not "just receive it anyway" — spend reports and the supplier portal would point at the wrong vendor.
- You can also ask the assistant: *"Cancel PO-2026-00042, it's a duplicate."* It will show an **Action Draft** for a manager to **Approve**.

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
5. Tap **Continue to Putaway**. The screen names a bin — walk there and scan the bin barcode when it says "Confirm putaway — scan bin."
6. Use **Receive another line** to keep working the same truck.
7. When each line shows **Received**, you are done. The PO chip moves to **PARTIALLY RECEIVED** or **RECEIVED**.

**⚠️ What if I make a mistake?**
- **Typed 100 instead of 10 (over-receive / fat-finger):** Stop. Do not "receive negative" or invent a second receive to cancel it out. Tell a WAREHOUSE_MANAGER: they post an attributed **stock correction** (or the receipt parks in **Inventory → Exceptions** for review if it blew past tolerance). The wrong entry stays visible in the ledger with your name, and the correction sits next to it with the manager's name — that is exactly how the system is designed to work.
- **Scanned the wrong GS1 barcode / wrong product:** If you notice before confirming — rescan. If you already confirmed, the wrong SKU's count is now wrong in that bin. Report it: a manager fixes it with a stock correction or a directed move. Never type barcode digits by hand from memory; if a label is unreadable, that is a supplier labeling problem to escalate.
- **Put stock in the wrong bin:** Ask a manager for a bin **move** (or use the scanner's LPN Move mode if your site allows). Do not just carry the box to the right shelf without scanning — then the computer and the shelf disagree, and the next picker gets sent to an empty bin.
- **Box is damaged / leaking / crushed (Quarantine):** Do not receive it into a normal sellable bin. Follow your screen's damaged/quarantine option so the stock lands in a **quarantine** location — it exists in the ledger (we did physically get it) but can never be picked for a customer. A manager then decides: return to supplier (see **Inbound → Returns**), scrap with a correction, or release if it was cosmetic.
- **More boxes arrived than ordered:** Receive the real count. If it exceeds tolerance, the system parks it for manager approval instead of silently accepting — this is intentional.
- **The screen shows "Offline - Caching Scans":** Keep working carefully; your scans are saved on the device. If one conflicts later, it appears in **Inventory → Exceptions → Sync Conflicts**, where a manager chooses **Approve & Re-process** or **Discard Transaction** (see SOP 04).

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

## Quick reference: "I messed up" cheat sheet (Inbound)

| Mistake | Can I fix it myself? | The fix |
|---|---|---|
| Typo in supplier/customer name | Yes (office roles) | Edit the record — master data fixes are safe |
| Duplicate PO, nothing received | Yes (PO creators) | **Cancel** the extra PO; it stays as CANCELLED history |
| Duplicate PO, goods received | No — Manager | Manager posts a correction; never cancel a received PO |
| Wrong price on PO | Before receive: yes. After: ADMIN/finance | Edit/Cancel, or fix cost at invoice reconcile |
| Typed 100, meant 10 at receive | No — Manager | Manager stock correction; big variances auto-park in **Exceptions** |
| Wrong barcode / wrong SKU received | No — Manager | Report immediately; manager corrects the ledger |
| Wrong bin at putaway | Picker reports; Manager or LPN Move fixes | Directed move, never a silent carry |
| Damaged carton | Any receiver flags it | Quarantine, then Manager decides return/scrap |
| Scan stuck after offline work | No — Manager | **Exceptions → Sync Conflicts** → **Approve & Re-process** / **Discard Transaction** |

**Golden rule:** the ledger is permanent. You cannot delete a mistake — and you don't need to. Say what happened, and the fix gets written next to it with a name and timestamp. The only truly bad move is hiding an error by inventing counts.

**Still stuck?** Click the chat bubble and describe the problem in plain words. If the assistant offers an **Action Draft**, a Manager can click **Approve** to execute the fix safely.
