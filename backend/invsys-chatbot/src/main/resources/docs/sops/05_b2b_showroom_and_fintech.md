---
title: "B2B Showroom & Commercial Finance SOP (Beginner Guide)"
slug: "sop-b2b-showroom-fintech"
sourcePath: "docs/sops/05_b2b_showroom_and_fintech.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "B2B_CUSTOMER", "VIEWER"]
audienceLevel: "beginner"
routeHints: ["/showroom/catalog", "/showroom/orders", "/showroom/checkout", "/showroom/billing", "/customers", "/invoices", "/sales-orders", "/settings", "/settings/billing", "/settings/fintech", "/settings/integrations", "/settings?tab=retailPos", "/purchase-orders", "/inventory/landed-costs"]
keywords: ["wholesale application", "showroom", "checkout", "credit hold", "credit line", "capital", "financing", "factoring", "partial credit", "invoice", "wrong price invoice", "duplicate invoice", "void invoice", "credit note", "refund", "billing", "landed cost", "freight", "customs", "allocation"]
---

# B2B Showroom & Commercial Finance — Beginner Playbook

Two audiences share this guide: **wholesale buyers** (your B2B customers shopping the Showroom) and **your own office staff** (managing applications, invoices, credit, and financing). Each section says clearly which side it is for.

---

## Before you start: how the money side works (in plain words)

1. **Wholesale application** — a business asks to become your B2B customer. You review and approve; they get Showroom access and payment terms.
2. **Showroom** — the buyer's private storefront: Catalog → Checkout → Orders → Billing. Buyers never see your warehouse screens, stock locations, or other customers.
3. **Credit line** — instead of paying upfront, approved buyers can owe you up to a limit (e.g. $10,000 at NET30 = pay within 30 days). Unpaid invoices consume the limit; payments free it.
4. **Credit Hold** — when a buyer owes too much or is overdue, the system blocks new allocations/checkouts until billing clears. This protects you automatically.
5. **Invoices are ledger documents** — like stock movements, a posted invoice is never deleted. Mistakes are fixed with **void** or **credit note** entries that sit next to the original, signed and dated.

**Who does what:**

| Role | Commercial powers |
|---|---|
| **OWNER** | Everything, including **Cash Flow & Financing** (capital/credit products) and billing plan settings — Owner-only |
| **ADMIN** | Customers, invoices, voiding (**Void Invoices** permission), integrations, users |
| **WAREHOUSE_MANAGER** | Sales orders and invoicing per company policy; cannot open Owner financing pages |
| **B2B_CUSTOMER** | Showroom only: Catalog, Checkout, Orders, Billing |
| **VIEWER** | Read-only office screens |

---

### How to Review and Approve a Wholesale Application (office)

**What is this?**
A business filled in your wholesale signup form and wants to buy from you. Approving creates their customer record and unlocks Showroom access with the terms you set.

**Who can do this? (Privileges Required)**
OWNER or ADMIN (customer management). Not visible to floor roles.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Outbound** → **Customers** → **Wholesale Applications** panel (pending applications from the showroom appear here)

**Step-by-Step Instructions:**
1. Open **Customers** and find the **Wholesale Applications** panel (pending list).
2. Open the application; check the business name, tax ID, and requested terms like any credit decision.
3. Click **Approve** to create the customer and grant Showroom access — or reject per policy.
4. After approval, verify the new customer record: payment terms (NET30/NET60), credit limit, ship-to address.

**⚠️ What if I make a mistake?**
- **Approved with wrong terms or credit limit:** Edit the customer record — terms and limits are master data, safe to correct any time. Already-posted invoices keep the terms they were issued under.
- **Approved a duplicate of an existing customer:** Point everything at the original record and ask an ADMIN to deactivate the twin. Order history on both remains visible.
- **Rejected by accident:** The business can re-apply, or an Admin creates the customer manually under **Customers**.

---

### How to Shop the Showroom (B2B buyer)

**What is this?**
Your private wholesale storefront. You see sellable items and your negotiated terms — never the seller's warehouse internals.

**Who can do this? (Privileges Required)**
B2B_CUSTOMER accounts. Office/floor staff of the seller do not shop here.

**Where to go in weGrowStock:**
🖥️ Sign in with your buyer account — you land in the Showroom. Navigation tabs: **Catalog** | **Orders** | **Checkout** | **Billing**

**Step-by-Step Instructions:**
1. Open **Catalog** and browse items; add quantities to your cart.
2. Open **Checkout**; review quantities, ship-to address, and terms **before** placing.
3. Click **Place order**.
4. Track progress under **Orders** (the seller's warehouse confirms, allocates, ships).

**⚠️ What if I make a mistake?**
- **Wrong quantity/items in the cart:** Edit the cart before **Place order** — everything is free to change until then.
- **Placed the order with a mistake:** Contact your sales rep quickly. If the seller hasn't allocated/shipped yet, they can cancel their side and you re-order. Once shipped, use **Return Items** → **Submit return** on the order.
- **Wrong ship-to address:** Same urgency rule — before ship, the rep fixes it; after ship, carrier intercept or a return.
- **Checkout blocked ("credit")?** Your account hit its credit limit or is on **Credit Hold**. Check **Billing**, pay down open invoices, or ask your rep about a temporary exception. The block is automatic, not personal.

---

### How to Issue an Invoice to a B2B Customer (office)

**What is this?**
Billing the buyer for what actually shipped. The invoice amount comes from shipped lines and agreed prices — it becomes part of the permanent financial ledger the moment it posts.

**Who can do this? (Privileges Required)**
OWNER or ADMIN (WAREHOUSE_MANAGER per company policy). **Voiding requires the Void Invoices permission (ADMIN/OWNER).**

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Outbound** → **Sales Orders** → **Invoice** / **Invoice remaining**
🖥️ Or: **Outbound** → **Invoices** for the full list (search box + status filter at the top)

**Step-by-Step Instructions:**
1. Open the shipped sales order.
2. Click **Invoice** (or **Invoice remaining** after partial shipments).
3. Check three things out loud: **customer**, **line prices**, **total**.
4. Confirm. The invoice appears under **Invoices**; the buyer sees it in their Showroom **Billing**.

**⚠️ What if I make a mistake?**
- **Wrong price on the invoice (typed $1,000, meant $100):** Do not try to edit the posted invoice — posted means posted. An ADMIN/OWNER with **Void Invoices** voids it (or issues a credit note for the difference), then re-invoices correctly. The buyer's Billing shows the honest trail: wrong invoice → void/credit → correct invoice.
- **Duplicate invoice (clicked Invoice twice / two people billed the same order):** Void the duplicate. The order's billing state returns to normal, and **Invoice remaining** shows what is truly still billable. Check the buyer wasn't double-charged through an integration; if a payment already landed on the duplicate, finance applies it to the correct invoice.
- **Invoiced the wrong customer:** Void, then re-issue on the correct account. Both events remain visible on both accounts — that is correct and protects you in a dispute.
- **Refunded invoice — how do I reverse the ledger entry?** A refund is never a deletion. The sequence: (1) issue the **credit note / void** against the invoice (finance document), (2) if goods came back, receive them through the RMA flow — **Returns** office approval, then floor **Returns receive** with **Condition photo** + **Confirm +1** — which writes the stock back onto the ledger, (3) payment is refunded through your payment rails. Three entries, three signatures, zero erasing. If you only remember one thing: *money fixes and stock fixes are separate entries; do both, delete neither.*

---

### How to Read Buyer Billing & Credit (both sides)

**What is this?**
The buyer's **Billing** tab shows open invoices and balance. On your side, the same numbers appear as AR (accounts receivable) and drive **Credit Hold** automation.

**Who can do this? (Privileges Required)**
Buyer: their own Billing only. Seller: OWNER/ADMIN see all AR; Dashboard cards visible to office roles.

**Where to go in weGrowStock:**
🖥️ Buyer: Showroom → **Billing**
🖥️ Seller: **Outbound** → **Invoices**, plus Dashboard cards **Open AR** and **Ready to invoice**

**Step-by-Step Instructions:**
1. *Seller:* watch **Open AR** on the Dashboard; chase overdue invoices before they trigger holds.
2. *Buyer:* check **Billing** before big orders — paying down balance prevents checkout blocks.
3. When a **Credit Hold** banner appears on a customer: stop trying to **Allocate** their orders and resolve billing first (payment, credit note, or an Owner-approved limit increase).

**⚠️ What if I make a mistake?**
- **Put the wrong customer on hold / raised the wrong limit:** Credit posture is master data — correct it on the customer record; allocations resume immediately.
- **Kept allocating around a hold:** Don't. The hold exists because the math says risk. If business wants an exception, an OWNER changes the limit — visibly — rather than staff working around it.

---

### Capital & Financing (Cash Flow) — Owner only

**What is this?**
Optional financing products (e.g. capital advances or factoring-style offers) that change *when* cash arrives — not what stock exists. All of it lives behind Owner-only settings.

**Who can do this? (Privileges Required)**
OWNER only. Admins and managers see a "forbidden" page by design.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Admin** → **Organization** → **Cash Flow & Financing** tab

**Step-by-Step Instructions:**
1. Open **Settings → Cash Flow & Financing** as the Owner.
2. Review offers/status shown for your organization.
3. Follow only the on-screen connect/confirm steps — never share credentials outside the flow.

**⚠️ What if I make a mistake?**
- **Started a financing connect flow you didn't mean to:** Stop before the final confirm — nothing binds until the flow completes. If you completed something in error, contact the financing provider through the same page's support path; this is a contractual matter, not a ledger edit.
- **Fintech page "forbidden":** You're not the Owner. That's the control working.

---

### Settings Quick Map (office admins)

**What is this?**
Where the commercial rails live. One-line map so you don't hunt:

**Who can do this? (Privileges Required)**
ADMIN for most tabs; OWNER for **Billing** and **Cash Flow & Financing**.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Admin** → **Organization**

- **Users** — invite staff with roles (ADMIN, WAREHOUSE_MANAGER, PICKER, VIEWER, B2B_CUSTOMER). Disable a user instead of sharing passwords.
- **Billing** — your subscription plan (Owner).
- **Cash Flow & Financing** — capital products (Owner).
- **Integrations** — accounting/channel apps; a healthy connection shows **LIVE**.
- **Sync Conflicts** — same queue as Exceptions (SOP 04).
- **Partner Catalog** — SKU mapping after a Mesh Network connection.
- **Retail POS** — register settings (needs the RETAIL_POS addon; Owner/Admin only).

**⚠️ What if I make a mistake?**
- **Invited someone with the wrong role:** Edit their role under **Users** — takes effect on next sign-in.
- **Disconnected an integration by accident:** Reconnect from the same card; check for a backlog of unsynced documents afterward under Reconciliation/Accounting Sync.

---

### Factoring & Partial Credits

**What is this?**
**Factoring** is selling an open invoice to a lender so the company gets cash now. The customer still pays the original invoice; weGrowStock just marks it **Factored**. A **partial credit memo** is for "they returned 1 of 5" — you credit that one line instead of voiding the whole bill.

**Who can do this? (Privileges Required)**
- **OWNER / ADMIN / FINANCE_ADMIN:** **Mark as Factored** and **Log Payment** on the Invoice Workspace.
- **FINANCE_ADMIN, WAREHOUSE_MANAGER, or ADMIN:** **Issue Partial Credit Memo** on a line, or **Void & Issue Credit Memo** for the whole document. Pickers never see Void.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Outbound** → **Invoices** → **Open Workspace**

**Step-by-Step Instructions:**
1. Open an **OPEN / ISSUED** invoice.
2. To advance cash, click **Mark as Factored** (fintech must accept the invoice).
3. When cash arrives, click **Log Payment** and enter the amount. A partial amount leaves the invoice **PARTIALLY PAID**.
4. If the customer returned one SKU, click **Issue Partial Credit Memo** on that line and type the returned qty. Do **not** void the whole invoice.

**⚠️ What if I make a mistake?**
- **Customer returned 1 of 5 items:** Issue a Partial Credit Memo. Voiding would reverse all five.
- **Factored the wrong invoice:** Tell the Owner. Factoring posts a funded advance — it is not deleted; finance settles it on the next remittance.

---

### Landed Costs & Freight Allocation

**What is this?**
The PO unit cost is what you agreed to pay the vendor for the goods. A week later a **freight**, **duty**, or **customs** bill often arrives ($500 is a typical surprise). That bill is part of what the inventory *actually cost* to land in the warehouse. **Landed Cost Allocation** spreads that dollar amount across the SKUs that already arrived, so margins and inventory valuation stay honest. It does **not** change how many units the dock scanned.

**Who can do this? (Privileges Required)**
**FINANCE_ADMIN**, OWNER, or ADMIN. Floor workers never edit a received PO to "add freight" onto a line.

**Where to go in weGrowStock:**
🖥️ **Inbound** → **Purchase Orders** (`/purchasing/orders`) → open the received PO / invoice → Landed Cost Allocation
🖥️ Help overlay also seeds `/inventory/landed-costs`

**How the math is spread:**
1. Finance enters the late bill total (e.g. $500) and the event type (freight, customs, duty).
2. weGrowStock picks a **strategy**:
   - **By value** — each SKU pays a share equal to (line value ÷ PO value) × $500. Expensive lines absorb more.
   - **By weight / volume** — heavier or bulkier lines absorb more (used when the carrier billed by cube or kilos).
   - **Hybrid** — the engine uses the best available physical data, then falls back (weight → volume → value) when a SKU is missing a measurement.
3. The engine writes **quantity-neutral** ledger rows: on-hand qty stays the same; each unit's **landed-cost component** goes up so inventory valuation = original receive + allocated freight.
4. The original dock receipt is **not edited**. AP still matches the vendor merchandise invoice to the PO and receipt. The logistics bill is a second, signed allocation.

**Step-by-Step Instructions:**
1. Do **not** open the PO and change line prices or quantities to "bake in" the $500.
2. Open the **Landed Cost Allocation** engine on the received PO / supplier invoice.
3. Enter $500 (or the real bill), choose freight vs customs, and confirm the spread.
4. Check that on-hand quantity is unchanged and that unit valuation rose.

**⚠️ What if I make a mistake?**
- **Edited the PO after receive to add freight:** Stop. Reverse that commercial edit if it is still draft-side; the dock receipt must stay as scanned. Then run Landed Cost Allocation.
- **Allocated $500 to the wrong PO:** Tell finance. Allocation rows stay in history; a reversing allocation (or a second allocation to the correct PO) is the fix — never delete the first.

---

## Quick reference: "I messed up" cheat sheet (B2B & money)

| Mistake | Can I fix it myself? | The fix |
|---|---|---|
| Invoice with wrong price/total | Needs **Void Invoices** (Admin/Owner) | Void or credit note → re-invoice correctly |
| Duplicate invoice | Needs **Void Invoices** | Void the twin; re-apply any payment to the right invoice |
| Invoice on wrong customer | Needs **Void Invoices** | Void → re-issue on correct account |
| Refund needed after return | Office + floor together | Credit note (money) + RMA receive (stock) — two separate entries |
| Approved wholesale app with wrong terms | Yes (Admin) | Edit customer terms/limit; old invoices keep old terms |
| Buyer placed a wrong order | Buyer + rep | Cancel before ship, or **Return Items** after |
| Buyer blocked at checkout (credit) | Buyer pays / Owner decides | Pay down **Billing**, or Owner adjusts the limit visibly |
| Wrong credit hold / limit | Yes (Owner/Admin) | Correct the customer record |
| Surprise freight/customs bill after receive | Finance | **Landed Cost Allocation** — do not edit the PO or the dock receipt |

**Golden rule:** money documents follow the same law as stock: **posted is permanent; corrections are new signed documents.** A void next to a wrong invoice is not embarrassing — it's what a trustworthy books looks like. And the stock side of any refund always goes through the RMA flow, never through editing a shipment.

**Still stuck?** Ask the chat bubble in plain words (e.g. *"I invoiced SO-1042 twice"*). Where the assistant can help, it drafts the fix as an **Action Draft** for an Admin/Owner to **Approve**.
