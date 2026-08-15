-- SOC 2 control-plane audit trail for Super Admin mutations.

CREATE TABLE platform_audit_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id            UUID NOT NULL,
    admin_email         VARCHAR(255),
    action              VARCHAR(128) NOT NULL,
    target_tenant_id    UUID,
    diff_json           JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address          VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_audit_logs_created ON platform_audit_logs (created_at DESC);
CREATE INDEX idx_platform_audit_logs_admin ON platform_audit_logs (admin_id, created_at DESC);
CREATE INDEX idx_platform_audit_logs_tenant ON platform_audit_logs (target_tenant_id, created_at DESC);

COMMENT ON TABLE platform_audit_logs IS
    'Append-only Super Admin mutation log (tiers, modules, suspend, kill-switch, etc.).';

GRANT SELECT, INSERT ON platform_audit_logs TO app_owner;
GRANT SELECT, INSERT ON platform_audit_logs TO app_user;
