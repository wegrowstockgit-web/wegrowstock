-- V097: Enterprise market-gap feature matrix (thermal, cluster pick, MRP, bin capacity,
-- SSCC manifests, RMA QC, role permissions). Append-only ledger untouched.

-- ---------------------------------------------------------------------------
-- Feature 1: thermal printers
-- ---------------------------------------------------------------------------
CREATE TABLE thermal_printers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                 VARCHAR(120) NOT NULL,
    printer_type         VARCHAR(32) NOT NULL
        CHECK (printer_type IN ('PRINTNODE', 'DIRECT_SOCKET')),
    printnode_printer_id VARCHAR(64),
    ip_address           VARCHAR(64),
    port                 INT,
    is_default           BOOLEAN NOT NULL DEFAULT FALSE,
    location_id          UUID REFERENCES locations(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_thermal_printers_tenant ON thermal_printers (tenant_id);
CREATE TRIGGER thermal_printers_updated_at BEFORE UPDATE ON thermal_printers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE thermal_printers ENABLE ROW LEVEL SECURITY;
ALTER TABLE thermal_printers FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON thermal_printers
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON thermal_printers TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON thermal_printers TO app_owner;

-- ---------------------------------------------------------------------------
-- Feature 2: cluster tote mappings
-- ---------------------------------------------------------------------------
CREATE TABLE cluster_tote_mappings (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    batch_id       UUID NOT NULL,
    tote_barcode   VARCHAR(64) NOT NULL,
    sales_order_id UUID NOT NULL REFERENCES sales_orders(id) ON DELETE CASCADE,
    slot_index     INT NOT NULL CHECK (slot_index BETWEEN 1 AND 12),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, batch_id, slot_index),
    UNIQUE (tenant_id, batch_id, tote_barcode)
);

CREATE INDEX idx_cluster_tote_batch ON cluster_tote_mappings (tenant_id, batch_id);
CREATE TRIGGER cluster_tote_mappings_updated_at BEFORE UPDATE ON cluster_tote_mappings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE cluster_tote_mappings ENABLE ROW LEVEL SECURITY;
ALTER TABLE cluster_tote_mappings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON cluster_tote_mappings
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON cluster_tote_mappings TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON cluster_tote_mappings TO app_owner;

-- ---------------------------------------------------------------------------
-- Feature 4: bin physical capacity
-- ---------------------------------------------------------------------------
ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS max_cubic_cm NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS max_weight_kg NUMERIC(19, 4);

-- ---------------------------------------------------------------------------
-- Feature 5: SSCC-18 pallet manifests
-- ---------------------------------------------------------------------------
CREATE TABLE pallet_manifests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sscc_18       VARCHAR(20) NOT NULL,
    warehouse_id  UUID REFERENCES locations(id),
    carrier_name  VARCHAR(120),
    status        VARCHAR(32) NOT NULL DEFAULT 'BUILDING'
        CHECK (status IN ('BUILDING', 'SEALED', 'DISPATCHED')),
    bol_number    VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, sscc_18)
);

CREATE TABLE pallet_manifest_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    pallet_id    UUID NOT NULL REFERENCES pallet_manifests(id) ON DELETE CASCADE,
    lpn_id       UUID REFERENCES license_plates(id),
    shipment_id  UUID REFERENCES shipments(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pallet_manifests_tenant ON pallet_manifests (tenant_id);
CREATE INDEX idx_pallet_manifest_items_pallet ON pallet_manifest_items (tenant_id, pallet_id);
CREATE TRIGGER pallet_manifests_updated_at BEFORE UPDATE ON pallet_manifests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER pallet_manifest_items_updated_at BEFORE UPDATE ON pallet_manifest_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE pallet_manifests ENABLE ROW LEVEL SECURITY;
ALTER TABLE pallet_manifests FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pallet_manifests
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
ALTER TABLE pallet_manifest_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE pallet_manifest_items FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pallet_manifest_items
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON pallet_manifests TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON pallet_manifests TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON pallet_manifest_items TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON pallet_manifest_items TO app_owner;

-- ---------------------------------------------------------------------------
-- Feature 6: RMA QC inspections
-- ---------------------------------------------------------------------------
CREATE TABLE rma_qc_inspections (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    return_line_id        UUID NOT NULL REFERENCES return_lines(id) ON DELETE CASCADE,
    inspector_user_id     UUID REFERENCES users(id),
    grade                 VARCHAR(32) NOT NULL
        CHECK (grade IN ('GRADE_A_NEW', 'GRADE_B_OPEN_BOX', 'GRADE_C_DAMAGED')),
    inspection_notes      TEXT,
    photo_attachment_ids  JSONB NOT NULL DEFAULT '[]'::jsonb,
    disposition_action    VARCHAR(32) NOT NULL
        CHECK (disposition_action IN ('RESTOCK', 'SCRAP', 'REPAIR', 'REFURBISH')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rma_qc_return_line ON rma_qc_inspections (tenant_id, return_line_id);
CREATE TRIGGER rma_qc_inspections_updated_at BEFORE UPDATE ON rma_qc_inspections
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE rma_qc_inspections ENABLE ROW LEVEL SECURITY;
ALTER TABLE rma_qc_inspections FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rma_qc_inspections
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON rma_qc_inspections TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON rma_qc_inspections TO app_owner;

-- ---------------------------------------------------------------------------
-- Feature 7: granular role permissions
-- ---------------------------------------------------------------------------
CREATE TABLE role_permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_key  VARCHAR(100) NOT NULL,
    granted         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, role_id, permission_key)
);

CREATE INDEX idx_role_permissions_tenant_role ON role_permissions (tenant_id, role_id);
CREATE TRIGGER role_permissions_updated_at BEFORE UPDATE ON role_permissions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE role_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permissions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON role_permissions
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON role_permissions TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON role_permissions TO app_owner;

-- Safety stock helper for MRP (variant-level)
ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS safety_stock NUMERIC(19, 4) NOT NULL DEFAULT 0;
