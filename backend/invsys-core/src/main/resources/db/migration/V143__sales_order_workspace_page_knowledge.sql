-- New Sales Order workspace knowledge. Do not rewrite V131.

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
)
SELECT
    '/sales/orders/new',
    'Sales',
    'New Sales Order',
    $pk$Build B2B sales orders with real-time inventory allocation and customer pricing.$pk$,
    $pk$Owners, Administrators, and Warehouse Managers create sales orders. Pickers fulfill released waves and do not build office orders.$pk$,
    $pk$[
      "Search for a customer to automatically load their default payment terms and price tiers.",
      "Search for items by SKU or Name. The system will display live Available-to-Promise (ATP) stock.",
      "Review the Grand Total and Credit Limit warnings before generating the order."
    ]$pk$::jsonb,
    $pk$[
      {
        "mistake": "I selected a SKU but the Available stock says 0.",
        "solution": "You can still add it to the order, but the system will automatically flag it as a Backorder once submitted until new inventory arrives.",
        "requiredRole": "SALES_REP"
      }
    ]$pk$::jsonb,
    $pk$ATP is on-hand minus existing allocations. Zero available is a backorder signal, not a hard stop.$pk$,
    'flyway-v143'
WHERE NOT EXISTS (
    SELECT 1 FROM page_knowledge_configs WHERE route_pattern = '/sales/orders/new'
);

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
)
SELECT
    '/sales-orders/new',
    category,
    title,
    summary,
    role_privileges,
    key_actions,
    common_mistakes,
    pro_tip,
    'flyway-v143'
FROM page_knowledge_configs
WHERE route_pattern = '/sales/orders/new'
  AND NOT EXISTS (
      SELECT 1 FROM page_knowledge_configs WHERE route_pattern = '/sales-orders/new'
  );
