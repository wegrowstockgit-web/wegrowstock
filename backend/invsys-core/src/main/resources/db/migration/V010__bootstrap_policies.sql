-- Allow migration owner to resolve tenants and refresh tokens before RLS context is set
CREATE POLICY bootstrap_read ON tenants
    FOR SELECT TO app_owner
    USING (true);

CREATE POLICY bootstrap_refresh_read ON refresh_tokens
    FOR SELECT TO app_owner
    USING (true);

GRANT SELECT ON tenants TO app_owner;
GRANT SELECT ON refresh_tokens TO app_owner;
