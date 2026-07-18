-- V081: Two-tier user management — self-service contact fields + org-scope columns.
-- user_warehouses junction already exists (V031); reaffirm purpose for LBAC.

-- ---------------------------------------------------------------------------
-- Organizational naming alignment (keep legacy department / shift_schedule synced in app)
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS corporate_department VARCHAR(128);

UPDATE users
SET corporate_department = department
WHERE corporate_department IS NULL
  AND department IS NOT NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS shift_schedule_type VARCHAR(32);

UPDATE users
SET shift_schedule_type = CASE
        WHEN shift_schedule IN ('DAY', 'NIGHT') THEN shift_schedule
        WHEN shift_schedule IN ('SWING', 'FLEX') THEN 'DAY'
        ELSE shift_schedule_type
    END
WHERE shift_schedule_type IS NULL
  AND shift_schedule IS NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_shift_schedule;
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_shift_schedule_type;
ALTER TABLE users
    ADD CONSTRAINT chk_users_shift_schedule_type
        CHECK (shift_schedule_type IS NULL
            OR shift_schedule_type IN ('DAY', 'NIGHT', 'WEEKEND'));

-- Legacy column stays for older readers; allow historic + new values during dual-write.
ALTER TABLE users
    ADD CONSTRAINT chk_users_shift_schedule
        CHECK (shift_schedule IS NULL
            OR shift_schedule IN ('DAY', 'NIGHT', 'WEEKEND', 'SWING', 'FLEX'));

-- ---------------------------------------------------------------------------
-- Self-service contact / preference fields
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(256),
    ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(256),
    ADD COLUMN IF NOT EXISTS address_city VARCHAR(128),
    ADD COLUMN IF NOT EXISTS address_region VARCHAR(64),
    ADD COLUMN IF NOT EXISTS address_postal_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS address_country VARCHAR(2),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ui_density_preference VARCHAR(16);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_ui_density;
ALTER TABLE users
    ADD CONSTRAINT chk_users_ui_density
        CHECK (ui_density_preference IS NULL
            OR ui_density_preference IN ('COMPACT', 'COMFORTABLE', 'SPACIOUS'));

COMMENT ON COLUMN users.corporate_department IS 'Org-scope department (admin-managed)';
COMMENT ON COLUMN users.shift_schedule_type IS 'Org-scope shift: DAY | NIGHT | WEEKEND (admin-managed)';
COMMENT ON COLUMN users.ui_density_preference IS 'Self-service UI density preference';
COMMENT ON TABLE user_warehouses IS 'LBAC junction: warehouse access assignments for localized roles';
