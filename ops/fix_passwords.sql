-- Fix demo user passwords (password123) and re-activate core demo personas
SELECT set_config('app.current_tenant', 'a0000000-0000-4000-8000-000000000001', false);
UPDATE users
SET password_hash = '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu',
    status = 'ACTIVE'
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001'
  AND email IN (
    'owner@demo.test',
    'admin@demo.test',
    'manager@demo.test',
    'picker@demo.test',
    'viewer@demo.test',
    'b2b@demo.test'
  );

SELECT set_config('app.current_tenant', 'b0000000-0000-4000-8000-000000000001', false);
UPDATE users SET password_hash = '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu'
WHERE tenant_id = 'b0000000-0000-4000-8000-000000000001';
