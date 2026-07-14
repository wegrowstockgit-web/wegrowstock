-- Allow migration owner to resolve users before tenant RLS context is set (login flow)
CREATE POLICY bootstrap_user_read ON users
    FOR SELECT TO app_owner
    USING (true);

GRANT SELECT ON users TO app_owner;
