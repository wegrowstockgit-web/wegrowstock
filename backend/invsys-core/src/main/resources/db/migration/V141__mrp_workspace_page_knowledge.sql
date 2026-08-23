-- MRP workspace knowledge: math context, overrides, and supplier-targeted buys.
-- Do not rewrite V131.

UPDATE page_knowledge_configs
SET
    summary = $pk$Review system-generated purchase suggestions based on Min/Max rules and sales forecasting. Adjust quantities manually to meet supplier case-pack minimums before consolidating into Draft POs.$pk$,
    key_actions = $pk$[
      "Filter by Supplier to build a targeted buy.",
      "Review On-Hand vs Allocated to understand why the system is suggesting a reorder.",
      "Click any Suggested Qty to manually override the amount.",
      "Click Consolidate to auto-generate Draft POs grouped by Supplier."
    ]$pk$::jsonb,
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "The system tells me to buy 14, but the vendor only sells cases of 10.",
        "solution": "Click the 'Suggested Qty' cell and manually override it to 20 before creating the PO.",
        "requiredRole": "BUYER"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v141'
WHERE route_pattern IN ('/purchasing/mrp', '/mrp');
