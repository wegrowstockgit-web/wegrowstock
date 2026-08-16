---
title: "B2B Showroom & Commercial Finance SOP"
slug: "sop-b2b-showroom-fintech"
sourcePath: "docs/sops/05_b2b_showroom_and_fintech.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "B2B_CUSTOMER", "VIEWER"]
routeHints: ["/showroom/catalog", "/showroom/orders", "/showroom/checkout", "/showroom/billing", "/customers", "/invoices", "/mesh-network", "/settings", "/settings/billing", "/settings/fintech", "/settings/integrations"]
---

# B2B Showroom & Commercial Finance — Operations Playbook

Wholesale buyers shop the Showroom; Owners manage billing, financing, and integrations. Warehouse bin maps stay hidden from B2B buyers.

---

### Showroom catalog

- **Target Audience & Roles:** B2B_CUSTOMER (primary); OWNER/ADMIN may support commercially but buyers live here.
- **Route Location:** Wholesale Portal → **Catalog**
- **Primary Operational Goal:** Browse sellable items and start an order without seeing warehouse bin topology.

#### 1. Step-by-Step Action Plan
1. Sign in as a B2B customer (lands on Showroom).
2. Open **Catalog**.
3. Browse items and add lines per on-screen cart controls.
4. Continue toward **Checkout** when ready (nav: Catalog / Orders / Checkout / Billing).

#### 2. Correlated Flow & Downstream Ripple Effect
- Warehouse managers later see demand as sales orders to **Confirm** / **Allocate**.
- Pickers never take direction from the Showroom—only from **Fulfillment** waves.
- ATP shown to the buyer reflects sellable availability, not reserve-bin secrets.
- Credit posture can block checkout if the account is on hold.

#### 3. Safety, Reversal & Undo Rules
- Remove cart lines before **Place order** if the basket is wrong.
- After placement, use **Orders** / **Return Items** flows—not warehouse tools.

#### 4. Troubleshooting Common Blockers
- **Item shows unavailable?** Stock may be unreceived or reserved—contact your supplier rep; do not ask pickers for bin codes.
- **Why can’t I open Fulfillment?** B2B sessions stay in Showroom by design.

---

### Showroom checkout & place order

- **Target Audience & Roles:** B2B_CUSTOMER.
- **Route Location:** **Checkout**
- **Primary Operational Goal:** Submit a wholesale order against catalog availability and account terms.

#### 1. Step-by-Step Action Plan
1. Open **Checkout** with a populated cart.
2. Review quantities, ship-to, and commercial terms on screen.
3. Click **Place order** (or **Continue** when the wizard uses staged confirms).
4. Note confirmation and follow status under **Orders**.

#### 2. Correlated Flow & Downstream Ripple Effect
- Supplier warehouse creates/works the related sales order (**Confirm**, **Allocate**, **Release to floor**).
- Buyer billing appears under Showroom **Billing** and supplier **Invoices**.
- Credit utilization may rise when invoices post.

#### 3. Safety, Reversal & Undo Rules
- Before placing, edit the cart.
- After placing, use **Return Items** / **Submit return** when goods must come back.
- Warehouse history is not rewritten for a buyer regret—returns add a proper RMA path.

#### 4. Troubleshooting Common Blockers
- **Place order disabled?** Cart empty, item unavailable, or account credit hold—contact your rep.
- **Checkout failed mid-way?** Retry from **Catalog** → cart; if it persists, call support with what you saw on screen (no need for technical codes).

---

### Showroom orders & returns

- **Target Audience & Roles:** B2B_CUSTOMER.
- **Route Location:** **Orders**
- **Primary Operational Goal:** Track order progress and start returns when needed.

#### 1. Step-by-Step Action Plan
1. Open **Orders**.
2. Review status in plain chips (progress toward shipped/closed as your supplier updates).
3. Click **Return Items** when initiating a return, then **Submit return**.
4. Use **Browse catalog** when you need to reorder.

#### 2. Correlated Flow & Downstream Ripple Effect
- Supplier **Returns** queue shows **REQUESTED** / **PENDING_REVIEW** for staff **Approve & Buy Label** or **Deny & Close**.
- Approved returns later hit **Returns receive** on the floor with **Condition photo** + **Confirm +1**.

#### 3. Safety, Reversal & Undo Rules
- Submit accurate quantities/reasons—photos may be required at receive.
- Denied returns (**Deny & Close**) mean keep or dispose per your agreement—do not ship unauthorized freight.

#### 4. Troubleshooting Common Blockers
- **Return Items missing?** Order may be ineligible yet—or still shipping; wait or call your rep.
- **Status not moving?** Warehouse may be waiting on allocation/wave—your rep can check **Sales Orders** / **Fulfillment**.

---

### Showroom billing

- **Target Audience & Roles:** B2B_CUSTOMER.
- **Route Location:** **Billing**
- **Primary Operational Goal:** See invoices/balances that affect whether new checkout is allowed.

#### 1. Step-by-Step Action Plan
1. Open **Billing**.
2. Review open invoices and payment guidance on screen.
3. Coordinate payment with your AP team so **Credit Hold** situations clear.

#### 2. Correlated Flow & Downstream Ripple Effect
- Clearing balances helps **Place order** and supplier-side **Allocate** succeed again.
- Supplier Owner sees complementary AR in **Invoices** / Dashboard **Open AR**.

#### 3. Safety, Reversal & Undo Rules
- Billing disputes go through your rep and credits—not through asking warehouse to erase shipments.

#### 4. Troubleshooting Common Blockers
- **Cannot checkout due to credit?** Pay down **Billing** or request a temporary commercial exception from the supplier Owner.

---

### Customers, invoices & credit holds (supplier office)

- **Target Audience & Roles:** OWNER, ADMIN, WAREHOUSE_MANAGER (commercial policy varies).
- **Route Location:** **Customers**, **Invoices**, **Sales Orders**, Dashboard banners
- **Primary Operational Goal:** Keep wholesale accounts sellable without overextending credit.

#### 1. Step-by-Step Action Plan
1. Maintain the customer under **Customers**.
2. On **Sales Orders**, use **Invoice** / **Invoice remaining** when fulfillment progress allows.
3. Watch proactive **Credit Hold** messaging; pause **Allocate** until finance clears the account.
4. Use Dashboard **Open AR** / **Ready to invoice** cards to prioritize collections and billing.

#### 2. Correlated Flow & Downstream Ripple Effect
- B2B **Place order** and warehouse **Allocate** both feel credit posture.
- Pickers should not be asked to override credit—from the floor, short picks and holds look like missing work.

#### 3. Safety, Reversal & Undo Rules
- Credits and re-invoices are additive finance documents.
- Do not “fix” AR by deleting shipment history.

#### 4. Troubleshooting Common Blockers
- **Why is Allocate greyed out on a confirmed order?** Check **Credit Hold** and stock availability first.
- **VIEWER cannot invoice?** Escalate to Admin/Owner.

---

### Settings: billing, financing, integrations, users

- **Target Audience & Roles:** ADMIN for most settings; OWNER for billing & financing; others generally blocked.
- **Route Location:** **Settings** tabs and subpages (**Billing**, **Cash Flow & Financing**, **Integrations**, **Users**, **Profile**, **Operations**, **Partner Catalog**, etc.)
- **Primary Operational Goal:** Keep the tenant’s commercial rails, users, and connected apps healthy.

#### 1. Step-by-Step Action Plan
1. Open **Settings** (Admin/Owner).
2. **Profile** — personal display preferences.
3. **Users** — invite with roles such as ADMIN, WAREHOUSE_MANAGER, PICKER, VIEWER, B2B_CUSTOMER.
4. **Billing** — subscription/plan matters for Owners.
5. **Cash Flow & Financing** (Owner) — review financing / factoring style options your organization enabled; follow on-screen connects/confirms only.
6. **Integrations** / Integrations Hub — connect accounting or channel apps; respect **LIVE** badges when a connection is healthy.
7. Other tabs as needed: **Warehouses**, **Inventory Rules**, **Documents**, **Security & SSO**, **Reconciliation**, **Accounting Sync**, **Operations**, **Sync Conflicts**, **Cost Centers & Requisitions**, **Partner Catalog** (SKU mapping after a Mesh Network connection). Cross-tenant discover/handshake lives on **Inbound → Mesh Network**.
8. Save each tab using its on-screen save/confirm controls before leaving.

#### 2. Correlated Flow & Downstream Ripple Effect
- User invites determine who can **Release to floor** vs who only browses Showroom.
- Financing/billing settings change cash timing—not bin quantities directly.
- Broken integrations surface as sync/accounting exceptions for managers.

#### 3. Safety, Reversal & Undo Rules
- Disable a user instead of sharing passwords.
- Disconnect integrations deliberately; do not leave half-configured **LIVE** connections unattended.
- Stock truth still changes only via receive, pick, count, and corrections.

#### 4. Troubleshooting Common Blockers
- **Fintech page forbidden?** Owner-only.
- **Invite missing OWNER role?** Owners are not created from the standard invite list—follow your provisioning process.
- **Sync Conflicts tab in Settings?** Same decisions as **Exceptions → Sync Conflicts**: **Discard Transaction** vs **Approve & Re-process**.
