-- Prevent cross-tenant hijack of corporate email domains used for force-SSO routing.
-- Only one tenant may hold a given domain in VERIFIED status.

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_domains_verified_name
    ON tenant_domains (lower(domain_name))
    WHERE verification_status = 'VERIFIED';
