-- weGrowStock market-reality recovery playbooks (over-receipt, damaged goods,
-- AP 3-way mismatch, ghost inventory). V131 already ran — append only.

UPDATE page_knowledge_configs
SET
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "The supplier sent more items than we ordered, and the system blocked me.",
        "solution": "The system strictly enforces Over-Receipt Tolerances. Do not force the receive. A Warehouse Manager must review the overage and approve a tolerance override, or you must refuse the extra boxes.",
        "requiredRole": "FLOOR_WORKER"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v136'
WHERE route_pattern IN ('/purchasing/receive', '/inbound/receive');

UPDATE page_knowledge_configs
SET
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "The received boxes are crushed or damaged. Should I reverse the receipt?",
        "solution": "NEVER reverse the receipt for damaged goods, or accounting won't know we received them. Receive the goods normally, but route them immediately to a Quarantine Bin. Then, use the RTV (Return to Vendor) workspace to demand a chargeback.",
        "requiredRole": "FLOOR_WORKER"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v136'
WHERE route_pattern IN ('/inventory/quarantine');

UPDATE page_knowledge_configs
SET
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "The AP Invoice is blocked due to a 3-Way Mismatch.",
        "solution": "The Vendor's invoice quantities or prices do not match our PO and Dock Receipt. Compare the three documents in this workspace. If the vendor overbilled, reject the AP Invoice. If our dock miscounted, a Manager must post a stock correction.",
        "requiredRole": "FINANCE_ADMIN"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v136'
WHERE route_pattern IN ('/purchasing/suppliers', '/suppliers');

UPDATE page_knowledge_configs
SET
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "I can't find an item, so I want to edit the inventory to 0.",
        "solution": "weGrowStock prevents silent edits to prevent 'Ghost Inventory'. You must submit a cycle count of 0. This flags a variance. A Manager will review the financial loss, assign an Accounting Reason Code (e.g., Shrinkage), and approve the ledger adjustment.",
        "requiredRole": "FLOOR_WORKER"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v136'
WHERE route_pattern IN ('/inventory/cycle-counts', '/cycle-counts');
