-- Immutable archived PDF location for AR invoices (S3/MinIO object key or s3:// URI).
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS document_url TEXT;

COMMENT ON COLUMN invoices.document_url IS
    'Archived invoice PDF location (s3://bucket/tenant/invoices/{id}.pdf)';
