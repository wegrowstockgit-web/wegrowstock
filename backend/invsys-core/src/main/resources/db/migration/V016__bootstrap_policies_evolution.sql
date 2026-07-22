-- Pre-tenant webhook tenant resolution (M1)
CREATE POLICY bootstrap_read ON channel_integrations
    FOR SELECT TO app_owner
    USING (true);

GRANT SELECT ON channel_integrations TO app_owner;
