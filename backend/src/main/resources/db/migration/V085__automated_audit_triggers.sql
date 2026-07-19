-- SOC 2 append-only audit: DB triggers on administrative boundaries + harden audit_log.

CREATE OR REPLACE FUNCTION invsys_audit_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_tenant_id UUID;
    v_actor_id  UUID;
    v_entity_id UUID;
    v_action    TEXT;
    v_old       JSONB;
    v_new       JSONB;
    v_diff      JSONB;
    v_actor_raw TEXT;
BEGIN
    v_actor_raw := nullif(current_setting('app.current_user_id', true), '');
    IF v_actor_raw IS NOT NULL AND v_actor_raw ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
        v_actor_id := v_actor_raw::uuid;
    ELSE
        v_actor_id := NULL;
    END IF;

    IF TG_OP = 'DELETE' THEN
        v_tenant_id := OLD.tenant_id;
        v_entity_id := OLD.id;
        v_old := to_jsonb(OLD);
        v_new := NULL;
        v_action := 'TG_DELETE';
    ELSIF TG_OP = 'UPDATE' THEN
        v_tenant_id := NEW.tenant_id;
        v_entity_id := NEW.id;
        v_old := to_jsonb(OLD);
        v_new := to_jsonb(NEW);
        v_action := 'TG_UPDATE';
    ELSE
        v_tenant_id := NEW.tenant_id;
        v_entity_id := NEW.id;
        v_old := NULL;
        v_new := to_jsonb(NEW);
        v_action := 'TG_INSERT';
    END IF;

    -- Prefer session tenant when present; fall back to row tenant.
    BEGIN
        IF nullif(current_setting('app.current_tenant', true), '') IS NOT NULL THEN
            v_tenant_id := nullif(current_setting('app.current_tenant', true), '')::uuid;
        END IF;
    EXCEPTION WHEN others THEN
        -- keep row tenant_id
        NULL;
    END;

    IF v_tenant_id IS NULL OR v_entity_id IS NULL THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    -- Avoid FK failures during bootstrap / stale pooled GUCs.
    IF v_actor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = v_actor_id) THEN
        v_actor_id := NULL;
    END IF;

    -- Redact secrets from captured row images.
    IF v_old IS NOT NULL THEN
        v_old := v_old - 'password_hash';
        IF v_old ? 'passwordHash' THEN
            v_old := v_old - 'passwordHash';
        END IF;
    END IF;
    IF v_new IS NOT NULL THEN
        v_new := v_new - 'password_hash';
        IF v_new ? 'passwordHash' THEN
            v_new := v_new - 'passwordHash';
        END IF;
    END IF;

    v_diff := jsonb_build_object(
        'source', 'postgres_trigger',
        'table', TG_TABLE_NAME,
        'op', TG_OP,
        'old', v_old,
        'new', v_new
    );

    INSERT INTO audit_log (tenant_id, actor_user_id, action, entity_type, entity_id, diff)
    VALUES (
        v_tenant_id,
        v_actor_id,
        v_action,
        upper(TG_TABLE_NAME),
        v_entity_id,
        v_diff
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS invsys_audit_users ON users;
CREATE TRIGGER invsys_audit_users
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION invsys_audit_trigger();

DROP TRIGGER IF EXISTS invsys_audit_user_roles ON user_roles;
CREATE TRIGGER invsys_audit_user_roles
    AFTER INSERT OR UPDATE OR DELETE ON user_roles
    FOR EACH ROW EXECUTE FUNCTION invsys_audit_trigger();

DROP TRIGGER IF EXISTS invsys_audit_user_warehouses ON user_warehouses;
CREATE TRIGGER invsys_audit_user_warehouses
    AFTER INSERT OR UPDATE OR DELETE ON user_warehouses
    FOR EACH ROW EXECUTE FUNCTION invsys_audit_trigger();

DROP TRIGGER IF EXISTS invsys_audit_tenant_settings ON tenant_settings;
CREATE TRIGGER invsys_audit_tenant_settings
    AFTER INSERT OR UPDATE OR DELETE ON tenant_settings
    FOR EACH ROW EXECUTE FUNCTION invsys_audit_trigger();

-- Historical immutability for the runtime application role.
REVOKE UPDATE, DELETE ON audit_log FROM app_user;

COMMENT ON FUNCTION invsys_audit_trigger() IS
    'SOC 2 append-only audit capture for administrative tables (users, roles, warehouses, settings)';
