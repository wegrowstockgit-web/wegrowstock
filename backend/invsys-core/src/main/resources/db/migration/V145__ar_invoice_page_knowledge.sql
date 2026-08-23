-- Accounts Receivable invoice list knowledge. Do not rewrite V131.

UPDATE page_knowledge_configs
SET
    title = 'Invoices',
    summary = $pk$Manage Accounts Receivable, generate invoices from shipped orders, dispatch PDFs to customers, and track overdue balances.$pk$,
    role_privileges = $pk$Owners and Administrators create invoices and dispatch PDFs. Finance Admins log payments and prioritize Overdue collections. Warehouse Managers can review issued documents.$pk$,
    key_actions = $pk$[
      "Use the Create Invoice button to search for unbilled, shipped Sales Orders.",
      "Monitor the 'Balance Due' and 'Overdue' badges to prioritize collections.",
      "Use the row action menu to quickly download PDFs or email the invoice to the customer."
    ]$pk$::jsonb,
    common_mistakes = $pk$[
      {
        "mistake": "The customer paid half their bill, but I can't adjust the Total.",
        "solution": "Do not alter the Invoice Total. Use the 'Log Payment' action to record the partial payment, which will automatically update the Balance Due.",
        "requiredRole": "FINANCE_ADMIN"
      }
    ]$pk$::jsonb,
    pro_tip = $pk$Overdue is due date in the past on an unpaid invoice. Log Payment — never edit Total — so collections and credit stay honest.$pk$,
    updated_at = NOW(),
    updated_by = 'flyway-v145'
WHERE route_pattern IN ('/invoices', '/sales/invoices');
