-- Fix demo user passwords (password123)
SELECT set_config('app.current_tenant', 'a0000000-0000-4000-8000-000000000001', false);
UPDATE users SET password_hash = '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu'
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001';

SELECT set_config('app.current_tenant', 'b0000000-0000-4000-8000-000000000001', false);
UPDATE users SET password_hash = '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu'
WHERE tenant_id = 'b0000000-0000-4000-8000-000000000001';
