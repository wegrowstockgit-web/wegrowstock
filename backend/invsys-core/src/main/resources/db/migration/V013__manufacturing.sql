-- Extend movement types for assembly (M7)
ALTER TABLE inventory_ledger DROP CONSTRAINT IF EXISTS inventory_ledger_movement_type_check;
ALTER TABLE inventory_ledger ADD CONSTRAINT inventory_ledger_movement_type_check
    CHECK (movement_type IN ('RECEIVE', 'SHIP', 'ADJUST', 'TRANSFER_IN', 'TRANSFER_OUT', 'ASSEMBLY_IN', 'ASSEMBLY_OUT'));

CREATE TABLE boms (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    parent_variant_id   UUID NOT NULL REFERENCES product_variants(id),
    name                VARCHAR(255) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, parent_variant_id)
);

CREATE TRIGGER boms_updated_at BEFORE UPDATE ON boms
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE bom_lines (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    bom_id                  UUID NOT NULL REFERENCES boms(id) ON DELETE CASCADE,
    component_variant_id    UUID NOT NULL REFERENCES product_variants(id),
    quantity_required       NUMERIC(19,4) NOT NULL CHECK (quantity_required > 0),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (bom_id, component_variant_id)
);

CREATE TRIGGER bom_lines_updated_at BEFORE UPDATE ON bom_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE production_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    number              VARCHAR(50) NOT NULL,
    parent_variant_id   UUID NOT NULL REFERENCES product_variants(id),
    qty_target          NUMERIC(19,4) NOT NULL CHECK (qty_target > 0),
    qty_produced        NUMERIC(19,4) NOT NULL DEFAULT 0,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'COMPONENTS_ALLOCATED', 'WIP', 'COMPLETED', 'CANCELLED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, number)
);

CREATE TRIGGER production_orders_updated_at BEFORE UPDATE ON production_orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE allocations ADD COLUMN production_order_id UUID NULL REFERENCES production_orders(id);
CREATE INDEX idx_allocations_production_order ON allocations(production_order_id) WHERE production_order_id IS NOT NULL;

-- RLS on manufacturing tables
DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY['boms', 'bom_lines', 'production_orders'];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.current_tenant'', true)::uuid) WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true)::uuid)',
            t
        );
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON boms, bom_lines, production_orders TO app_user;
