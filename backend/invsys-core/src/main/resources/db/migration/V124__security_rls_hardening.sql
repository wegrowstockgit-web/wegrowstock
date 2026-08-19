-- Align support_tickets RLS with the app GUC and force it for table owners.
DROP POLICY IF EXISTS support_tickets_tenant_isolation ON support_tickets;

CREATE POLICY support_tickets_tenant_isolation ON support_tickets
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE support_tickets FORCE ROW LEVEL SECURITY;

-- oauth_callback_states is consumed on the bootstrap (app_owner) path before TenantContext.
-- Revoke DML from the restricted app role so a compromised request connection cannot dump live states.
REVOKE INSERT, UPDATE, DELETE, SELECT ON oauth_callback_states FROM app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON oauth_callback_states TO app_owner;
