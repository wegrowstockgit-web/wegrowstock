-- V039: Deep enterprise pillars — WebAuthn credentials, bom_outputs, ap_matching_logs

-- 2) Terminal biometric / passkey credentials (shared-floor WebAuthn binding)
CREATE TABLE webauthn_credentials (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id   VARCHAR(512) NOT NULL,
    public_key_pem  TEXT,
    -- Software authenticator secret (HMAC) for glove-friendly / test harness; never returned after create
    credential_secret_hash VARCHAR(128),
    sign_count      BIGINT NOT NULL DEFAULT 0,
    label           VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, credential_id)
);

CREATE INDEX idx_webauthn_credentials_user ON webauthn_credentials (tenant_id, user_id);

CREATE TABLE webauthn_challenges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    challenge       VARCHAR(128) NOT NULL,
    purpose         VARCHAR(40) NOT NULL DEFAULT 'TERMINAL_ASSERT',
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, challenge)
);

CREATE TRIGGER webauthn_credentials_updated_at BEFORE UPDATE ON webauthn_credentials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER webauthn_challenges_updated_at BEFORE UPDATE ON webauthn_challenges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 5) Process manufacturing co/by-product outputs
CREATE TABLE bom_outputs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    bom_id           UUID NOT NULL REFERENCES boms(id) ON DELETE CASCADE,
    variant_id       UUID NOT NULL REFERENCES product_variants(id),
    output_type      VARCHAR(20) NOT NULL
        CHECK (output_type IN ('MAIN', 'CO_PRODUCT', 'BY_PRODUCT')),
    allocation_ratio NUMERIC(5, 2) NOT NULL DEFAULT 0
        CHECK (allocation_ratio >= 0 AND allocation_ratio <= 100),
    qty_per_batch    NUMERIC(18, 6) NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, bom_id, variant_id)
);

CREATE INDEX idx_bom_outputs_bom ON bom_outputs (bom_id);
CREATE TRIGGER bom_outputs_updated_at BEFORE UPDATE ON bom_outputs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 7) Three-way AP matching audit log
CREATE TABLE ap_matching_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id        UUID,
    ingestion_id      UUID REFERENCES supplier_invoice_ingestions(id) ON DELETE SET NULL,
    po_id             UUID NOT NULL REFERENCES purchase_orders(id),
    match_status      VARCHAR(30) NOT NULL
        CHECK (match_status IN ('MATCHED', 'QTY_MISMATCH', 'COST_MISMATCH', 'RECEIPT_MISMATCH', 'PARTIAL', 'FAILED')),
    validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ap_matching_logs_po ON ap_matching_logs (tenant_id, po_id);
CREATE TRIGGER ap_matching_logs_updated_at BEFORE UPDATE ON ap_matching_logs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- RLS
DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'webauthn_credentials',
        'webauthn_challenges',
        'bom_outputs',
        'ap_matching_logs'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid) WITH CHECK (tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid)',
            t
        );
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON webauthn_credentials, webauthn_challenges, bom_outputs, ap_matching_logs TO app_user;
GRANT SELECT ON webauthn_credentials, webauthn_challenges, bom_outputs, ap_matching_logs TO app_owner;
