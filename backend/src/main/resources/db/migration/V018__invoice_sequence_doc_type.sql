-- Align seeded document sequence doc_type with application code (INVOICE, not INV).
UPDATE document_sequences
SET doc_type = 'INVOICE'
WHERE doc_type = 'INV';

-- Ensure the 2026 invoice counter starts after seeded INV-2026-00001 / 00002.
UPDATE document_sequences
SET next_value = GREATEST(next_value, 3)
WHERE doc_type = 'INVOICE'
  AND period = '2026';
