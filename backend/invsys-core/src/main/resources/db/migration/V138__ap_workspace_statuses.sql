-- AP Document Workspace: MATCHED (approved voucher) and DISPUTED (debit memo).
ALTER TABLE supplier_invoice_ingestions
    DROP CONSTRAINT IF EXISTS supplier_invoice_ingestions_status_check;

ALTER TABLE supplier_invoice_ingestions
    ADD CONSTRAINT supplier_invoice_ingestions_status_check
        CHECK (status IN ('PENDING', 'RECONCILED', 'CONFLICT', 'MATCHED', 'DISPUTED'));

INSERT INTO page_knowledge_configs (
    route_pattern, category, title, summary, role_privileges, key_actions, common_mistakes, pro_tip, updated_by
)
SELECT
    '/purchasing/ap-ingestion',
    'Inbound',
    'AP Invoice Reconciliation',
    $pk$Split-screen weGrowStock workspace: preview the vendor bill on the left, 3-way match PO / dock receipt / invoice lines on the right. Approve a voucher, dispute an overbill, or request a warehouse recount.$pk$,
    $pk$Finance Admins, Owners, Administrators, and Warehouse Managers. Floor workers do not approve AP.$pk$,
    $pk$["Drop a PDF or image — OCR fills invoice number, date, supplier, and lines.", "Confirm the linked PO or search the combobox.", "Approve & Match when every line is green; otherwise issue a debit memo or request a recount."]$pk$::jsonb,
    $pk$[{"mistake": "The AP Invoice is blocked due to a 3-Way Mismatch.", "solution": "Compare PO Qty, Received Qty, and Invoiced Qty in this workspace. Reject / dispute if the vendor overbilled. If the dock miscounted, request a warehouse recount.", "requiredRole": "FINANCE_ADMIN"}]$pk$::jsonb,
    $pk$Never paste raw JSON or edit the original dock receipt to force a match.$pk$,
    'flyway-v138'
WHERE NOT EXISTS (
    SELECT 1 FROM page_knowledge_configs WHERE route_pattern = '/purchasing/ap-ingestion'
);
