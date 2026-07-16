-- Authoritative DNS verification uses ACTIVE status + TXT token hint in dkim_tokens.
ALTER TABLE tenant_domains
    DROP CONSTRAINT IF EXISTS tenant_domains_verification_status_check;

ALTER TABLE tenant_domains
    ADD CONSTRAINT tenant_domains_verification_status_check
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'ACTIVE', 'FAILED'));

-- Prefer ACTIVE as the public-verified state going forward.
UPDATE tenant_domains
SET verification_status = 'ACTIVE'
WHERE verification_status = 'VERIFIED';

DROP INDEX IF EXISTS ux_tenant_domains_verified_name;
CREATE UNIQUE INDEX ux_tenant_domains_active_name
    ON tenant_domains (lower(domain_name))
    WHERE verification_status IN ('ACTIVE', 'VERIFIED');
