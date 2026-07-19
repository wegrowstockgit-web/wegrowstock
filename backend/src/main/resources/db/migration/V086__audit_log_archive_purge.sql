-- Cold-storage archival may DELETE aged audit_log rows after a successful S3 upload.
-- Runtime app_user still cannot UPDATE/DELETE audit_log directly (V085); purge goes
-- through this SECURITY DEFINER helper only.

CREATE OR REPLACE FUNCTION archive_purge_audit_logs(p_ids uuid[])
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
SET row_security = off
AS $$
DECLARE
    deleted_count integer;
BEGIN
    IF p_ids IS NULL OR cardinality(p_ids) = 0 THEN
        RETURN 0;
    END IF;

    DELETE FROM audit_log
    WHERE id = ANY (p_ids);

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;

REVOKE ALL ON FUNCTION archive_purge_audit_logs(uuid[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION archive_purge_audit_logs(uuid[]) TO app_user, app_owner;
