-- Enterprise RMA workspace: disposition bins, credit memos, RTV link, page knowledge.
-- Do not rewrite V131.

ALTER TABLE returns
    ADD COLUMN IF NOT EXISTS resolution_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS credit_memo_id UUID,
    ADD COLUMN IF NOT EXISTS rtv_order_id UUID;

ALTER TABLE return_lines
    ADD COLUMN IF NOT EXISTS restock_location_id UUID,
    ADD COLUMN IF NOT EXISTS restocking_fee_pct NUMERIC(7, 4) NOT NULL DEFAULT 0;

UPDATE page_knowledge_configs
SET
    summary = $pk$Authorize, receive, and inspect customer returns. Set line dispositions to restock sellable goods or scrap damaged items, and automatically issue credit memos.$pk$,
    key_actions = $pk$[
      "Click 'New RMA' to search by Sales Order or Customer and select specific items being returned.",
      "Generate return shipping labels for customers directly from the wizard.",
      "When freight arrives, verify quantities on Receive Terminal and set dispositions per line.",
      "Closing an inspected RMA automatically creates a Credit Memo on the ledger."
    ]$pk$::jsonb,
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "I restocked a returned item that was actually broken.",
        "solution": "Use the QC Inspection drawer to change the disposition to SCRAP and post a stock correction before the item is reallocated to an outbound order.",
        "requiredRole": "WAREHOUSE_MANAGER"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v142'
WHERE route_pattern IN ('/returns', '/inbound/returns');

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
)
SELECT
    '/inbound/returns',
    category,
    title,
    summary,
    role_privileges,
    key_actions,
    common_mistakes,
    pro_tip,
    'flyway-v142'
FROM page_knowledge_configs
WHERE route_pattern = '/returns'
  AND NOT EXISTS (
      SELECT 1 FROM page_knowledge_configs WHERE route_pattern = '/inbound/returns'
  );
