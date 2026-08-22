-- weGrowStock: manual vs Mesh Network IN_TRANSIT workflows for Purchase Orders.
-- V131 already ran in deployed environments — do not rewrite it.

UPDATE page_knowledge_configs
SET
    key_actions = $pk$[
      "Click New PO, pick a supplier, and add SKU, quantity, unit cost, and UoM.",
      "Save as Draft, then Submit when the buy is firm.",
      "For standard suppliers, click 'Mark In Transit' and enter the tracking number when the vendor emails you the shipping confirmation.",
      "For Mesh Network suppliers, do nothing! weGrowStock listens to their warehouse and automatically marks your PO in transit when their truck leaves.",
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
      },
      {
        "mistake": "I marked a PO in transit, but the truck got cancelled",
        "solution": "Click 'Revert to Submitted' to clear the tracking number and put the order back in the waiting queue.",
        "requiredRole": "BUYER"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v135'
WHERE route_pattern IN ('/purchasing/orders', '/purchase-orders');
