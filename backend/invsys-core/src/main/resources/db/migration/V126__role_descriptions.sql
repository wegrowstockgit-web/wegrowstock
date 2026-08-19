-- IAM: persist human-readable role copy on the role row (system + custom).
ALTER TABLE roles ADD COLUMN IF NOT EXISTS description VARCHAR(255);

UPDATE roles SET description = 'Tenant owner — cannot be assigned from this list'
 WHERE code = 'OWNER' AND (description IS NULL OR btrim(description) = '');

UPDATE roles SET description = 'Full warehouse administration except ownership transfer'
 WHERE code = 'ADMIN' AND (description IS NULL OR btrim(description) = '');

UPDATE roles SET description = 'Floor leadership, adjustments, and cycle counts'
 WHERE code = 'WAREHOUSE_MANAGER' AND (description IS NULL OR btrim(description) = '');

UPDATE roles SET description = 'Pick, pack, and put-away'
 WHERE code = 'PICKER' AND (description IS NULL OR btrim(description) = '');

UPDATE roles SET description = 'Read-only operations'
 WHERE code = 'VIEWER' AND (description IS NULL OR btrim(description) = '');

UPDATE roles SET description = 'Retail POS register'
 WHERE code = 'RETAIL_CASHIER' AND (description IS NULL OR btrim(description) = '');

UPDATE roles SET description = 'POS supervision and voids'
 WHERE code = 'RETAIL_MANAGER' AND (description IS NULL OR btrim(description) = '');

UPDATE roles SET description = 'Customer portal access'
 WHERE code = 'B2B_CUSTOMER' AND (description IS NULL OR btrim(description) = '');

UPDATE roles SET description = 'Vendor portal access'
 WHERE code = 'SUPPLIER' AND (description IS NULL OR btrim(description) = '');

UPDATE roles SET description = 'Custom organizational role'
 WHERE is_system_role = FALSE AND (description IS NULL OR btrim(description) = '');

COMMENT ON COLUMN roles.description IS
    'Human-readable role summary shown in assignment UI; tenant-editable for custom roles';
