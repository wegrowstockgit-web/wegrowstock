-- Advanced workspace capabilities for the weGrowStock Page Info ("i") overlay.
-- Also persists sales-order credit-hold overrides used by the Sales Order workspace.

ALTER TABLE sales_orders
    ADD COLUMN IF NOT EXISTS credit_hold_override BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE page_knowledge_configs
SET
    key_actions = key_actions || $pk$[
      "Open Workspace and check the Credit Status badge before Allocate.",
      "Override Credit Holds (Finance Admin / Admin) when AR clears a blocked account.",
      "Split / Backorder unfulfilled lines so available stock can ship now."
    ]$pk$::jsonb,
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "Customer over credit limit",
        "solution": "Split order or request finance override. Do not force a pick around the hold.",
        "requiredRole": "FINANCE_ADMIN"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v133'
WHERE route_pattern IN ('/sales/orders', '/sales-orders', '/fulfillment/orders');

UPDATE page_knowledge_configs
SET
    key_actions = key_actions || $pk$[
      "Mark as Factored when a fintech advance funds an open invoice.",
      "Log Payment for cash received against an issued invoice.",
      "Issue Partial Credit Memo on a single returned line instead of voiding the whole document."
    ]$pk$::jsonb,
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "Customer returned 1 of 5 items",
        "solution": "Issue Partial Credit Memo (do not void whole invoice).",
        "requiredRole": "FINANCE_ADMIN"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v133'
WHERE route_pattern IN ('/sales/invoices', '/invoices');

UPDATE page_knowledge_configs
SET
    key_actions = key_actions || $pk$[
      "Open Variance Approval when a blind count misses the ledger.",
      "Select an Accounting Reason Code (Shrinkage, Damaged, Expired) before Approve Variance."
    ]$pk$::jsonb,
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "I approved a variance without saying why stock was missing.",
        "solution": "Accounting needs Shrinkage vs Damaged vs Expired. Re-open Variance Approval and choose the reason code before the ledger offset posts.",
        "requiredRole": "WAREHOUSE_MANAGER"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v133'
WHERE route_pattern IN ('/inventory/cycle-counts', '/cycle-counts');

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
)
SELECT
    '/inventory/variances',
    'Inventory',
    'Variance Approval',
    $pk$Manager review of blind-count discrepancies. Expected vs counted stays visible until a Warehouse Manager posts a stock correction with an accounting reason code.$pk$,
    $pk$Warehouse Managers approve variances. Floor Pickers request recounts from Cycle Counts; they cannot post the ledger offset.$pk$,
    $pk$["Compare Expected Quantity to Counted Quantity.", "Request Recount if the count looks like a typo.", "Approve Variance and pick Shrinkage, Damaged, or Expired."]$pk$::jsonb,
    $pk$[{"mistake": "I approved a variance without saying why stock was missing.", "solution": "Accounting needs Shrinkage vs Damaged vs Expired. Choose the reason code before the ledger offset posts.", "requiredRole": "WAREHOUSE_MANAGER"}]$pk$::jsonb,
    $pk$The original expected quantity stays in history. Approve posts an offset — it never rewrites the count.$pk$,
    'flyway-v133'
WHERE NOT EXISTS (
    SELECT 1 FROM page_knowledge_configs WHERE route_pattern = '/inventory/variances'
);

UPDATE page_knowledge_configs
SET
    key_actions = key_actions || $pk$[
      "Open the Supplier Workspace and review the AP Invoices tab.",
      "AP 3-Way Matching review: compare the Purchase Order, dock receipt, and vendor bill."
    ]$pk$::jsonb,
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "I paid a vendor bill that did not match the receipt.",
        "solution": "Open AP Invoices on the Supplier Workspace. Discrepancy means the PO, dock receipt, and OCR bill disagree — fix the receive or the bill before AP posts.",
        "requiredRole": "WAREHOUSE_MANAGER"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v133'
WHERE route_pattern IN ('/purchasing/suppliers', '/suppliers');

UPDATE page_knowledge_configs
SET
    key_actions = key_actions || $pk$[
      "Log Labor Time against a routing step so hours absorb into finished-good cost.",
      "Report Completion to mint finished inventory into the warehouse ledger."
    ]$pk$::jsonb,
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "Forgot to log labor before completion",
        "solution": "Post manual labor adjustment on the Production Order workspace, then Report Completion so yield carries the true labor cost.",
        "requiredRole": "WAREHOUSE_MANAGER"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v133'
WHERE route_pattern = '/manufacturing/orders';
