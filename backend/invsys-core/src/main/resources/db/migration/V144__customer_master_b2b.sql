-- B2B customer master: phone, tax-exempt flag, and page knowledge.
-- Do not rewrite V131.

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS phone VARCHAR(40),
    ADD COLUMN IF NOT EXISTS tax_exempt BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE page_knowledge_configs
SET
    title = 'Customers',
    summary = $pk$Manage B2B customer master data, assign custom pricing tiers, establish credit limits, and onboard buyers to the self-service showroom portal.$pk$,
    role_privileges = $pk$Owners and Administrators create and invite buyers. Finance Admins place accounts on Credit Hold. Warehouse Managers and Viewers can review the grid.$pk$,
    key_actions = $pk$[
      "Assign a Price Tier to automatically calculate discounts on Sales Orders.",
      "Set a Credit Limit to prevent orders from shipping if the account is past due.",
      "Invite customers to the B2B Showroom to allow them to place their own orders."
    ]$pk$::jsonb,
    common_mistakes = $pk$[
      {
        "mistake": "A customer cannot place an order because they are on Credit Hold.",
        "solution": "A Finance Admin must review their outstanding invoices. Do not create a duplicate account to bypass the hold.",
        "requiredRole": "FINANCE_ADMIN"
      }
    ]$pk$::jsonb,
    pro_tip = $pk$Price tiers change unit price on New Sales Order. Credit Hold blocks Confirm/Allocate until finance clears the account.$pk$,
    updated_at = NOW(),
    updated_by = 'flyway-v144'
WHERE route_pattern IN ('/customers', '/sales/customers');

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
)
SELECT
    '/sales/customers/new',
    category,
    'New Customer',
    summary,
    role_privileges,
    key_actions,
    common_mistakes,
    pro_tip,
    'flyway-v144'
FROM page_knowledge_configs
WHERE route_pattern = '/customers'
  AND NOT EXISTS (
      SELECT 1 FROM page_knowledge_configs WHERE route_pattern = '/sales/customers/new'
  );
