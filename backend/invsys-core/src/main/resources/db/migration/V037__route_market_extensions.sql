-- V037: Route-by-route market extensions
-- terminal PIN hashes, work-center capacity, accounting webhook vault keys (app-layer)

-- 1) Shared-terminal PIN context swap (kiosk / packing station)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS terminal_pin_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_tenant_terminal_pin
    ON users (tenant_id, terminal_pin_hash)
    WHERE terminal_pin_hash IS NOT NULL;

-- 4) Shop-floor work center capacity (throughput / station sizing)
ALTER TABLE manufacturing_work_centers
    ADD COLUMN IF NOT EXISTS capacity NUMERIC(12, 2) NOT NULL DEFAULT 1;

COMMENT ON COLUMN users.terminal_pin_hash IS
    'Deterministic SHA-256 hex of tenantId:4digitPin for shared-terminal operator switch';
COMMENT ON COLUMN manufacturing_work_centers.capacity IS
    'Station capacity units used for WIP routing / load balancing';
