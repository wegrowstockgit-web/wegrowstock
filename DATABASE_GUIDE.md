# 📚 The WMS Database: A Human-Friendly Guide

Welcome to the Inventory & Warehouse Management System (WMS)! 

If you are reading this, you might be a new developer, a product manager, or a stakeholder trying to understand how our data is organized. Databases can look intimidating, but at its core, this system is just a digital reflection of a real-world physical warehouse. 

This document explains **what** tables we have, **why** they exist, and the **logic** keeping everything safe.

---

## 🏗️ The Golden Rules of Our System

Before we look at the tables, you need to understand the two massive rules that dictate how this entire database is built:

1. **The "Apartment Building" Rule (Multi-Tenancy & RLS):** 
   This software is "multi-tenant." That means multiple different companies use our app at the same time. Think of the database as a giant apartment building. Every company gets their own apartment (`tenant_id`). Because of a PostgreSQL feature called **Row-Level Security (RLS)**, the database acts like an unbreakable digital lock. A user from Company A physically cannot query, see, or accidentally delete data belonging to Company B.
2. **The "Bank Statement" Rule (Append-Only Ledger):** 
   If you make a mistake on your taxes, you don't use white-out; you write a correction line. We treat inventory exactly like money. We **never** simply change a stock quantity from `100` to `90`. Instead, we add a new transaction saying `-10`. This means we have a flawless, un-hackable history of every single item that has ever moved.

---

## 🏢 1. The Companies & People (Identity)

These tables answer the question: *"Who is using the system, and what are they allowed to do?"*

* **`tenants`**: The companies using our software (e.g., "Demo Corp", "Acme Wholesale").
* **`tenant_settings`**: The specific preferences for that company, stored flexibly (like their time zone, default currency, and whether they allow inventory to go negative).
* **`users`**: The actual human beings logging in (identified by their email and password).
* **`roles` & `user_roles`**: What a user is allowed to do. An `OWNER` can see billing, but a `PICKER` can only see the warehouse scanning screens.
* **`invitations`**: When a manager wants to hire a new picker, they send an invite. This table holds the secure link until the new employee clicks it and creates their password.

---

## 🗺️ 2. The Physical Space & The Goods (Catalog & Locations)

These tables map out the physical world: the building itself and the boxes sitting on the shelves.

* **`locations`**: This is a hierarchy. It isn't just "Warehouse 1". It maps out the exact physical spot: `Warehouse -> Zone -> Aisle -> Bin`. This tells the warehouse worker exactly where to walk.
* **`products`**: The high-level concept of an item (e.g., "Industrial T-Shirt").
* **`product_variants`**: The specific, sellable version of that product (e.g., "Industrial T-Shirt, Size Medium, Blue"). This is what actually gets a barcode.
* **`lots`**: Important for things that expire or get recalled (like food or medical supplies). It tracks the specific batch a variant belongs to.

---

## ❤️ 3. The Heartbeat: Inventory Math

This is the most important part of the database. How do we know how many items we have?

* **`inventory_ledger`**: The master historical record. Every time a box enters the building, leaves the building, gets assembled, or goes missing, a row is permanently added here. **No rows are ever updated or deleted.**
* **`allocations`**: The "Promise" table. If someone buys a shirt online, we haven't shipped it yet, but we can't sell it to anyone else either. An "allocation" reserves that specific shirt.
* **`inventory_levels`**: The "Dashboard Gauge." Calculating the sum of millions of ledger rows every time a user loads a page is too slow. So, whenever a ledger row is added, invisible database robots (Triggers) instantly update this table so we know exactly what is `on_hand` and what is `allocated` in real-time.

> **The Math:** `Available to Promise = (On Hand - Allocated)`

---

## 📦 4. Moving Goods (Purchasing & Sales)

How do items get into the warehouse, and how do they leave?

* **`suppliers`**: The people we buy raw goods from.
* **`purchase_orders` (POs) & `purchase_order_lines`**: The documents we send to suppliers asking for stock. When the truck arrives, workers scan the boxes, and the PO lines are marked as "Received", which feeds the `inventory_ledger`.
* **`customers`**: The people buying our finished goods.
* **`sales_orders` (SOs) & `sales_order_lines`**: The orders customers place. This triggers an `allocation` (reserving the item), and tells the warehouse floor it's time to go pick it.
* **`shipments`**: The physical box leaving the building. It holds the FedEx/UPS tracking number.
* **`returns`**: If a customer sends something back (RMA), it gets logged here before being inspected and put back on the shelf (or thrown in the trash).

---

## 🛠️ 5. Light Manufacturing (Kitting)

Sometimes a company doesn't just buy and sell; they build things.

* **`boms` (Bill of Materials) & `bom_lines`**: The "Recipe." It says, *"To make 1 Skateboard, you need 1 Deck, 2 Trucks, and 4 Wheels."*
* **`production_orders`**: The command to the warehouse floor to follow a recipe. When they finish building it, the system deducts the components from the ledger and adds the finished Skateboard to the ledger.

---

## 💵 6. Money (Billing & Payments)

Getting paid is why the business exists.

* **`invoices` & `invoice_lines`**: The physical bill sent to a customer for a Sales Order.
* **`payment_intents`**: The digital handshake with our credit card processor (Stripe).
* **`payments`**: The finalized record that the money actually hit our bank account.

---

## 🔌 7. The "Plumbing" (Integrations & Safety)

These tables run quietly in the background. They make sure the app doesn't crash, that it talks to other software correctly, and that we have a trail of breadcrumbs if something goes wrong.

* **`outbox_events`**: Our reliable mailman. Instead of pausing the whole system to tell Shopify or QuickBooks that an item sold, the database writes a message here. A background worker picks it up and delivers it. If the internet goes down, the message stays safe here until it comes back.
* **`webhook_events`**: The inbox. When Stripe or Shopify sends *us* a message, we log it here immediately so we don't lose it, then process it when we are ready.
* **`idempotency_keys`**: The "Anti-Double-Click" guard. If a user has a bad connection and clicks "Charge Credit Card" three times quickly, this table remembers the first click and ignores the duplicates.
* **`external_references`**: The Rosetta Stone. We might call a product `SKU-123`, but Shopify calls it `ID-9999`. This table links our internal IDs to the outside world's IDs.
* **`audit_log`**: The security camera. If an Admin changes the company's negative inventory settings, or changes a user's role, it is recorded here with their User ID and a timestamp.

---

### 🎉 Summary for Developers
If you are building a new feature:
1. **Always** make sure your new table has a `tenant_id` column.
2. **Never** try to manually edit the `inventory_levels` table. Let the ledger do the work.
3. **Always** think about what happens if the internet drops. Use the `outbox_events` for anything talking to the outside world!


# 🗄️ WMS Database Schema Dictionary

This is the exhaustive index of all tables powering the multi-tenant Warehouse Management System. 

**Global Rule:** With the exception of `currency_rates`, every single table is isolated using PostgreSQL Row-Level Security (RLS) bound to a `tenant_id`.

## 1. Identity, Access & Tenancy (IAM)
Manages companies, users, permissions, and security parameters.

| Table Name | Description |
| :--- | :--- |
| `tenants` | The core company account record (e.g., Acme Wholesale). |
| `tenant_settings` | JSONB preferences for the company (currency, negative inventory rules, barcode prefixes). |
| `tenant_domains` | Verified custom DNS domains used for white-labeling and sending emails. |
| `tenant_sso_configurations` | SAML2/OIDC configurations for routing enterprise users to their corporate login page. |
| `users` | The physical humans logging into the system. |
| `roles` | System roles (`OWNER`, `ADMIN`, `WAREHOUSE_MANAGER`, `PICKER`, `VIEWER`, `B2B_CUSTOMER`). |
| `user_roles` | Maps a user to their specific system role(s). |
| `user_warehouses` | Location-Based Access Control (LBAC). Maps which specific warehouses a user is allowed to access. |
| `refresh_tokens` | Secure, rotating session tokens to keep users logged in without requiring constant passwords. |
| `invitations` | Stores secure, time-expiring hashes sent to new hires or B2B clients to join a tenant. |

## 2. Catalog & Product Master
Defines what items exist and how they are measured.

| Table Name | Description |
| :--- | :--- |
| `products` | High-level item families (e.g., "T-Shirt"). |
| `product_variants` | Sellable, barcoded specific items (e.g., "T-Shirt, Blue, Medium"). Holds unit prices and average cost tracking. |
| `variant_uom_conversions` | Unit of Measure math (e.g., "1 Case = 24 Each"). |
| `lots` | Batches, expirations, and serial tracking for strict FIFO/FEFO picking and compliance. |
| `tax_rates` | Regional tax percentages applied to invoices. |

## 3. Physical Space & Fleet
Maps where inventory physically exists in the world.

| Table Name | Description |
| :--- | :--- |
| `locations` | Hierarchical map of physical storage: `WAREHOUSE` -> `ZONE` -> `AISLE` -> `BIN`. |
| `vehicle_assignments` | Maps mobile locations (like a Service Van) to a specific technician. |

## 4. Inventory Core (The Ledger)
The unalterable financial truth of the warehouse.

| Table Name | Description |
| :--- | :--- |
| `inventory_ledger` | The append-only ledger. Records every single `RECEIVE`, `SHIP`, `ADJUST`, `TRANSFER`, and `ASSEMBLY`. |
| `allocations` | Soft-reservations. Stock promised to a Sales Order that hasn't shipped yet. |
| `inventory_levels` | A trigger-maintained summary table. Instantly calculates `on_hand` and `allocated` for fast dashboard loads. |
| `cycle_counts` | The header for a physical bin-counting session. |
| `cycle_count_lines` | The specific items counted vs. expected, which generates `ADJUST` ledger rows upon approval. |

## 5. Inbound Supply Chain (Purchasing)
Getting stock into the building.

| Table Name | Description |
| :--- | :--- |
| `suppliers` | Vendors who sell us raw goods or wholesale items. |
| `purchase_orders` | The request sent to a supplier (Header). |
| `purchase_order_lines` | What we specifically asked the supplier for, tracking `qty_ordered` vs `qty_received`. |
| `demand_forecasts` | Algorithmically generated restock recommendations based on 30-day velocity. |

## 6. Outbound Supply Chain (Sales & Fulfillment)
Getting stock out of the building.

| Table Name | Description |
| :--- | :--- |
| `customers` | The businesses or people buying goods. |
| `sales_orders` | The customer's order (Header). |
| `sales_order_lines` | What the customer ordered, tracking `qty_ordered` vs `qty_allocated` vs `qty_shipped`. |
| `shipments` | The physical package leaving the dock. Holds EasyPost tracking numbers and carrier info. |
| `shipment_lines` | What is inside the specific physical shipment box. |
| `returns` (RMA) | Customer returns (Header). |
| `return_lines` | Expected items returning, determining if they go to `RESTOCK`, `SCRAP`, or `REPAIR`. |

## 7. B2B Portal & Mesh Network
Allows other companies to buy from us directly.

| Table Name | Description |
| :--- | :--- |
| `customer_user_mappings` | Maps a login account directly to a B2B Customer profile to restrict their view to their own orders. |
| `customer_price_tiers` | Special discount groupings (e.g., "Gold Tier gets 15% off"). |
| `customer_credit_lines` | Underwritten credit limits allowing B2B customers to purchase on NET30 terms. |
| `tenant_mesh_partners` | Automated cross-tenant links. Allows Tenant A's Purchase Order to automatically become Tenant B's Sales Order. |

## 8. Manufacturing & Assembly
Building new products from raw materials.

| Table Name | Description |
| :--- | :--- |
| `boms` (Bill of Materials) | The master recipe (Header). |
| `bom_lines` | The ingredients needed to make the recipe. |
| `manufacturing_operations` | Standard steps (e.g., "Assembly", "Quality Check") and standard costs. |
| `bom_operations` | The specific steps required to build a specific BOM. |
| `production_orders` | The active command telling the floor to follow a BOM and build the item. |
| `team_labor_rates` | Hourly costs tied to specific workers to calculate the precise landed cost of assembled goods. |

## 9. Billing, Capital, & Payments
Following the money.

| Table Name | Description |
| :--- | :--- |
| `invoices` | The finalized bill sent to the customer. Locks in the financial exchange rate. |
| `invoice_lines` | The line items on the bill. |
| `stripe_accounts` | The tenant's linked Stripe Connect account for processing credit cards. |
| `payment_intents` | The digital handshake with Stripe attempting to pull funds. Tracks the platform fee spread. |
| `payments` | Settled money. The transaction has fully cleared the bank. |
| `currency_rates` | **Global Table (No RLS).** Updated daily to provide real-time FX estimates for global users. |

## 10. Infrastructure, Safety & Integrations (The Plumbing)
Keeps the system resilient, offline-capable, and integrated with the world.

| Table Name | Description |
| :--- | :--- |
| `document_sequences` | Ensures every invoice and order gets a perfect, gapless number (e.g., INV-001, INV-002). |
| `idempotency_keys` | Prevents double-charging or double-shipping if a user accidentally double-clicks a button. |
| `webhook_events` | The Inbox. Stores incoming alerts from Stripe/Shopify so we process them safely without dropping them. |
| `outbox_events` | The Outbox. Saves messages destined for the outside world (like Accounting Syncs) so they survive server reboots. |
| `offline_sync_conflicts` | Catches and flags errors that happen when a warehouse worker reconnects from a dead-zone with bad data. |
| `external_references` | The Rosetta Stone. Links our internal `product_id` to Shopify's or Amazon's `external_id`. |
| `integration_credentials` | The Vault. Encrypts and securely stores OAuth tokens for Xero, QBO, and Amazon SP-API. |
| `integration_sync_logs` | The integration audit. Tracks successes, failures, and retries for external data pushes. |
| `account_mappings` | Maps our internal inventory events (like a `SHIP`) to specific General Ledger account codes in Xero/QBO. |
| `channel_integrations` | Stores the connection status of active e-commerce channels (Shopify, Amazon). |
| `edi_trading_partners` | AS2 IDs mapping traditional enterprise EDI supply chain connections. |
| `ap_invoice_ingestions` | Stores PDFs and optical character recognition (OCR) parsed data from paper supplier invoices. |
| `audit_log` | The unalterable security camera tracking exactly who changed what setting or record. |