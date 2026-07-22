CREATE TABLE invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sales_order_id  UUID REFERENCES sales_orders(id),
    customer_id     UUID NOT NULL REFERENCES customers(id),
    number          VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'OPEN', 'PARTIALLY_PAID', 'PAID', 'VOID')),
    subtotal        NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax             NUMERIC(19,4) NOT NULL DEFAULT 0,
    total           NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency        CHAR(3) NOT NULL DEFAULT 'USD',
    due_at          TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, number)
);

CREATE TRIGGER invoices_updated_at BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE invoice_lines (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id  UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description VARCHAR(500) NOT NULL,
    qty         NUMERIC(19,4) NOT NULL DEFAULT 1,
    unit_price  NUMERIC(19,4) NOT NULL DEFAULT 0,
    amount      NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER invoice_lines_updated_at BEFORE UPDATE ON invoice_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE stripe_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    connected_account_id VARCHAR(255) NOT NULL,
    onboarding_status   VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    capabilities        JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER stripe_accounts_updated_at BEFORE UPDATE ON stripe_accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE payment_intents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id              UUID NOT NULL REFERENCES invoices(id),
    provider                VARCHAR(20) NOT NULL DEFAULT 'STRIPE',
    external_id             VARCHAR(255) NOT NULL,
    amount                  NUMERIC(19,4) NOT NULL,
    currency                CHAR(3) NOT NULL DEFAULT 'USD',
    application_fee_amount  NUMERIC(19,4) NOT NULL DEFAULT 0,
    connected_account_ref   VARCHAR(255),
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    raw_payload             JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, external_id)
);

CREATE TRIGGER payment_intents_updated_at BEFORE UPDATE ON payment_intents
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    payment_intent_id   UUID NOT NULL REFERENCES payment_intents(id),
    amount              NUMERIC(19,4) NOT NULL,
    fee_amount          NUMERIC(19,4) NOT NULL DEFAULT 0,
    balance_txn_ref     VARCHAR(255),
    settled_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
