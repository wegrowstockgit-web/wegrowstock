-- Align V034 tables with BaseEntity timestamps (updated_at)
ALTER TABLE customer_catalog_restrictions
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE magic_login_tokens
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

DO $$
BEGIN
    CREATE TRIGGER customer_catalog_restrictions_updated_at
        BEFORE UPDATE ON customer_catalog_restrictions
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TRIGGER magic_login_tokens_updated_at
        BEFORE UPDATE ON magic_login_tokens
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
