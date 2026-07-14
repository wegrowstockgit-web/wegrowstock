-- Idempotent fix: seed may be applied after V018, leaving doc_type as INV.
UPDATE document_sequences
SET doc_type = 'INVOICE'
WHERE doc_type = 'INV';

UPDATE document_sequences
SET next_value = GREATEST(next_value, 3)
WHERE doc_type = 'INVOICE'
  AND period = '2026'
  AND tenant_id = 'a0000000-0000-4000-8000-000000000001';

UPDATE document_sequences
SET next_value = GREATEST(next_value, 2)
WHERE doc_type = 'INVOICE'
  AND period = '2026'
  AND tenant_id = 'b0000000-0000-4000-8000-000000000001';
