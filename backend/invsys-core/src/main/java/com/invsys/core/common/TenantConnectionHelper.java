package com.invsys.core.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
     * Binds {@code app.current_user_id} as a transaction-local GUC for DB audit triggers.
     */
    public static void bindUser(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_user_id', ?, true)")) {
            ps.setString(1, userId != null ? userId.toString() : "");
            ps.execute();
        }
    }

    /**
     * Fail-closed wipe before a connection returns to the pool:
     * <ul>
     *   <li>clear {@code app.current_tenant} / {@code app.current_user_id} (session-level)</li>
     *   <li>{@code DEALLOCATE ALL} so prepared plans cannot ride the pooled backend</li>
     * </ul>
     * Complements PgBouncer {@code server_reset_query = DISCARD ALL}.
     */
    public static void clearTenant(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            ps.setString(1, "");
            ps.execute();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_user_id', ?, false)")) {
            ps.setString(1, "");
            ps.execute();
        }
        try (Statement st = connection.createStatement()) {
            st.execute("DEALLOCATE ALL");
        }
    }
}
