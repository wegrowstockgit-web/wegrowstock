-- Supplier mesh onboarding: enterprise class/incoterms/portal + page knowledge.
-- Do not rewrite V131.

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS supplier_class VARCHAR(64);

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS incoterms VARCHAR(16);

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS portal_invited_at TIMESTAMPTZ;

UPDATE page_knowledge_configs
SET
    key_actions = key_actions || $pk$[
      "Use 'Connect Mesh Partner' to link suppliers already using weGrowStock for zero-effort electronic PO and ASN sync."
    ]$pk$::jsonb,
    common_mistakes = common_mistakes || $pk$[
      {
        "mistake": "Supplier changed their bank details",
        "solution": "Update in Supplier Master Data; changes apply to future invoices only.",
        "requiredRole": "FINANCE_ADMIN"
      }
    ]$pk$::jsonb,
    updated_at = NOW(),
    updated_by = 'flyway-v139'
WHERE route_pattern IN ('/purchasing/suppliers', '/suppliers');
