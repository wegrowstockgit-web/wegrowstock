-- Security hardening: fix support_tickets RLS GUC, revoke platform-table
-- grants from the shared app_user role, and tighten webhook insert checks.

-- 4.1 support_tickets used the wrong GUC (app.tenant_id) and lacked FORCE.
DROP POLICY IF EXISTS support_tickets_tenant_isolation ON support_tickets;
CREATE POLICY support_tickets_tenant_isolation ON support_tickets
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);
ALTER TABLE support_tickets FORCE ROW LEVEL SECURITY;

-- 4.2 / 4.5 Platform tables must not be readable/writable by the WMS app_user pool.
REVOKE ALL ON TABLE platform_admins FROM app_user;
REVOKE ALL ON TABLE platform_admin_refresh_tokens FROM app_user;
REVOKE ALL ON TABLE platform_audit_logs FROM app_user;
REVOKE ALL ON TABLE tenant_shard_routing FROM app_user;
REVOKE ALL ON TABLE tenant_integration_controls FROM app_user;
REVOKE ALL ON TABLE tenant_rate_limit_overrides FROM app_user;
REVOKE ALL ON TABLE platform_compliance_broadcasts FROM app_user;
REVOKE ALL ON TABLE platform_knowledge_documents FROM app_user;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE platform_admins TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE platform_admin_refresh_tokens TO app_owner;
GRANT SELECT, INSERT ON TABLE platform_audit_logs TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE tenant_shard_routing TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE tenant_integration_controls TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE tenant_rate_limit_overrides TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE platform_compliance_broadcasts TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE platform_knowledge_documents TO app_owner;

-- webhook_events stays WITH CHECK (true) for public ingest that later stamps tenant_id.
