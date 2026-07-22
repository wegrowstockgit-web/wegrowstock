CREATE TABLE locations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    parent_location_id  UUID REFERENCES locations(id) ON DELETE CASCADE,
    type                VARCHAR(20) NOT NULL CHECK (type IN ('WAREHOUSE', 'ZONE', 'AISLE', 'BIN')),
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    path                VARCHAR(500) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_locations_tenant_parent ON locations(tenant_id, parent_location_id);
CREATE TRIGGER locations_updated_at BEFORE UPDATE ON locations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sku_root    VARCHAR(100) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    uom         VARCHAR(20) NOT NULL DEFAULT 'EA',
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, sku_root)
);

CREATE TRIGGER products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_variants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku             VARCHAR(100) NOT NULL,
    barcode         VARCHAR(100),
    attributes      JSONB NOT NULL DEFAULT '{}',
    price           NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency        CHAR(3) NOT NULL DEFAULT 'USD',
    reorder_point   NUMERIC(19,4) NOT NULL DEFAULT 0,
    reorder_qty     NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, sku)
);

CREATE INDEX idx_variants_barcode ON product_variants(tenant_id, barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_variants_attributes_gin ON product_variants USING GIN (attributes);
CREATE TRIGGER product_variants_updated_at BEFORE UPDATE ON product_variants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE lots (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id  UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    lot_number  VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, variant_id, lot_number)
);

CREATE INDEX idx_lots_expiry ON lots(tenant_id, variant_id, expires_at);
CREATE TRIGGER lots_updated_at BEFORE UPDATE ON lots
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
