-- Custom tenant roles: drop the closed code checklist and mark platform baselines as immutable.
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_code_check;

ALTER TABLE roles ALTER COLUMN code TYPE VARCHAR(80);

ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_system_role BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE roles
   SET is_system_role = TRUE
 WHERE code IN (
     'OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER',
     'RETAIL_CASHIER', 'RETAIL_MANAGER', 'B2B_CUSTOMER', 'SUPPLIER'
 );

COMMENT ON COLUMN roles.is_system_role IS
    'True for platform baseline roles; tenant admins cannot update or delete them';
