-- weGrowStock enterprise edge cases: UoM, cross-dock, lot hard-stop, landed cost.
-- V131 already ran — append / insert only.

UPDATE page_knowledge_configs
SET
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "I received 5 'Pallets' instead of 5 'Cases', blowing up our inventory value.",
        "solution": "Unit of Measure (UoM) errors are critical. If you receive the wrong UoM, a manager must use Reverse Receipt immediately. Always verify the UoM dropdown (Eaches, Cases, Pallets) matches the physical label you are scanning.",
        "requiredRole": "FLOOR_WORKER"
      },
      {
        "mistake": "I put away items that were meant for an urgent backorder.",
        "solution": "Always check for a 'Cross-Dock' alert upon scanning. If flagged, move the items directly to the Outbound Fulfillment staging area; do not put them away in the racks.",
        "requiredRole": "FLOOR_WORKER"
      }
    ]$pk$::jsonb,
    pro_tip = $pk$For regulated items (FSMA/DSCSA), the system will hard-stop you until you enter the Manufacturer Lot Number and Expiration Date. Do not use generic lot numbers.$pk$,
    updated_at = NOW(),
    updated_by = 'flyway-v137'
WHERE route_pattern IN ('/purchasing/receive', '/inbound/receive');

UPDATE page_knowledge_configs
SET
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "We got a surprise $500 customs bill a week after receiving the PO.",
        "solution": "Do NOT edit the PO. Use the Landed Cost Allocation engine to distribute the $500 freight/customs bill across the received items. This accurately recalculates the inventory valuation without altering the original dock receipt.",
        "requiredRole": "FINANCE_ADMIN"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v137'
WHERE route_pattern IN ('/purchasing/orders', '/purchase-orders');

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
)
SELECT
    '/inventory/landed-costs',
    'Inbound',
    'Landed Cost Allocation',
    $pk$Spread late freight, duty, and customs bills across already-received inventory. Valuation updates; the original dock receipt quantity stays untouched.$pk$,
    $pk$Finance Admins, Owners, and Administrators allocate landed costs. Floor workers do not edit PO lines after receive.$pk$,
    $pk$["Open the Landed Cost Allocation engine from the PO or invoice.", "Enter the freight or customs amount and choose a spread (value, weight, volume, or hybrid).", "Confirm — weGrowStock posts quantity-neutral valuation rows."]$pk$::jsonb,
    $pk$[{"mistake": "We got a surprise $500 customs bill a week after receiving the PO.", "solution": "Do NOT edit the PO. Use the Landed Cost Allocation engine to distribute the $500 freight/customs bill across the received items. This accurately recalculates the inventory valuation without altering the original dock receipt.", "requiredRole": "FINANCE_ADMIN"}]$pk$::jsonb,
    $pk$Late logistics bills are math on value, not a rewrite of what the dock scanned.$pk$,
    'flyway-v137'
WHERE NOT EXISTS (
    SELECT 1 FROM page_knowledge_configs WHERE route_pattern = '/inventory/landed-costs'
);
