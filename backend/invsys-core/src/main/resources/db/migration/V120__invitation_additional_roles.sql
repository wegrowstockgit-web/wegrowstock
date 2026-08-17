ALTER TABLE invitations
    ADD COLUMN IF NOT EXISTS additional_roles VARCHAR(512);
