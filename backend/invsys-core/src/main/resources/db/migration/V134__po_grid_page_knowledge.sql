-- weGrowStock: PO list-grid vendor reference + replace PO page-knowledge
-- with the enterprise procurement copy (ETAs, receiving progress, Reverse Receipt).
-- V131 already ran in deployed environments — do not rewrite it.

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS vendor_reference VARCHAR(128);

UPDATE page_knowledge_configs
SET
    title = 'Purchase Orders',
    summary = $pk$Owners, Administrators, and Warehouse Managers create and submit POs. Floor Pickers receive against submitted POs on Inbound Receive.$pk$,
    role_privileges = $pk$Owners, Administrators, and Warehouse Managers create and submit POs. Floor Pickers receive against submitted POs on Inbound Receive.$pk$,
    key_actions = $pk$[
      "Click New PO, pick a supplier, and add SKU, quantity, unit cost, and UoM.",
      "Save as Draft, then Submit when the buy is firm.",
      "Mark In Transit when the vendor ships, then hand off to Floor receive.",
      "Use the data grid to monitor ETAs and Receiving Progress."
    ]$pk$::jsonb,
    common_mistakes = $pk$[
      {
        "mistake": "I typed the wrong supplier price or quantity",
        "solution": "If DRAFT, edit the line. If SUBMITTED (0 receipts), cancel and recreate. If RECEIVED, use Reverse Receipt — NEVER edit a posted ledger row.",
        "requiredRole": "WAREHOUSE_MANAGER"
      },
      {
        "mistake": "I accidentally created a duplicate PO",
        "solution": "Select the duplicate and click Cancel PO before any items are received.",
        "requiredRole": "BUYER"
      }
    ]$pk$::jsonb,
    pro_tip = $pk$Confirm unit cost out loud before Submit. A wrong price on a submitted PO becomes a finance problem; a wrong receive becomes a ledger reversal.$pk$,
    updated_at = NOW(),
    updated_by = 'flyway-v134'
WHERE route_pattern IN ('/purchasing/orders', '/purchase-orders');
