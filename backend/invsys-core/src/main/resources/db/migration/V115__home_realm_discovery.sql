-- Home Realm Discovery: verified-domain TXT token + corporate CIDR + SAML ACS/cert.

ALTER TABLE tenant_domains
    ADD COLUMN IF NOT EXISTS dns_verification_token VARCHAR(128),
    ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tenant_domains
SET is_verified = TRUE
WHERE verification_status IN ('ACTIVE', 'VERIFIED');

UPDATE tenant_domains
SET dns_verification_token = 'growstock-verification=' || tenant_id::text
WHERE dns_verification_token IS NULL OR btrim(dns_verification_token) = '';

ALTER TABLE tenant_sso_configs
    ADD COLUMN IF NOT EXISTS sso_provider VARCHAR(32) NOT NULL DEFAULT 'CUSTOM',
    ADD COLUMN IF NOT EXISTS acs_url VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS saml_certificate TEXT,
    ADD COLUMN IF NOT EXISTS corporate_cidr_ips JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE tenant_sso_configs
    DROP CONSTRAINT IF EXISTS tenant_sso_configs_sso_provider_check;
ALTER TABLE tenant_sso_configs
    ADD CONSTRAINT tenant_sso_configs_sso_provider_check
        CHECK (sso_provider IN ('OKTA', 'ENTRA', 'GOOGLE', 'CUSTOM_SAML', 'CUSTOM'));

COMMENT ON COLUMN tenant_domains.dns_verification_token IS 'TXT record value published at the corporate domain';
COMMENT ON COLUMN tenant_domains.is_verified IS 'True after DNS TXT verification (mirrors ACTIVE/VERIFIED status)';
COMMENT ON COLUMN tenant_sso_configs.corporate_cidr_ips IS 'JSON array of CIDR blocks used for warehouse-network HRD';
COMMENT ON COLUMN tenant_sso_configs.acs_url IS 'SAML Assertion Consumer Service URL advertised to the IdP';
