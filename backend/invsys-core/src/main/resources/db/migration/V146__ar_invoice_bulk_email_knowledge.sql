-- Bulk email action on the Accounts Receivable invoice list. Do not rewrite V131 or V145.

UPDATE page_knowledge_configs
SET
    key_actions = $pk$[
      "Use the Create Invoice button to search for unbilled, shipped Sales Orders.",
      "Monitor the 'Balance Due' and 'Overdue' badges to prioritize collections.",
      "Use the row action menu to quickly download PDFs or email the invoice to the customer.",
      "Select invoices with the checkboxes, then use Email Invoices to dispatch a batch."
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v146'
WHERE route_pattern IN ('/invoices', '/sales/invoices');
