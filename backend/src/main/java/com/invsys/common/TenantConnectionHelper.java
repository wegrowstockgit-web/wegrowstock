package com.invsys.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class TenantConnectionHelper {
    private TenantConnectionHelper() {
    }

    /**
     * Binds {@code app.current_tenant} as a <strong>transaction-local</strong> GUC
     * ({@code set_config(..., true)}). The value evaporates on COMMIT/ROLLBACK so
     * PgBouncer transaction pooling cannot leak tenant identity across clients.
     * Callers must execute SQL inside an open transaction (Spring {@code @Transactional}).
     */
    public static void bindTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    /**
     * Resets {@code app.current_tenant} to the empty string (session-level wipe) before a
     * connection returns to the pool. RLS policies treat empty as "no tenant" (fail closed).
     */
    public static void clearTenant(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            ps.setString(1, "");
            ps.execute();
        }
    }
}
