ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS app_context VARCHAR(16);
