-- Allow app_owner to resolve/accept invitations before tenant context exists (public accept flow).

DROP POLICY IF EXISTS bootstrap_invitation_read ON invitations;
CREATE POLICY bootstrap_invitation_read ON invitations
    FOR SELECT TO app_owner
    USING (true);

DROP POLICY IF EXISTS bootstrap_invitation_update ON invitations;
CREATE POLICY bootstrap_invitation_update ON invitations
    FOR UPDATE TO app_owner
    USING (true)
    WITH CHECK (true);

GRANT SELECT, UPDATE ON invitations TO app_owner;
