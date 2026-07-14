package com.invsys.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class TenantConnectionHelper {
    private TenantConnectionHelper() {
    }

    /**
     * Binds {@code app.current_tenant} on the connection. Inside an explicit transaction the
     * binding is transaction-local (reverts cleanly on commit/rollback). In autocommit mode a
     * transaction-local set_config would evaporate with the implicit statement transaction, so
     * the binding is made session-level instead; {@link #clearTenant} must be called before the
     * connection is returned to the pool.
     */
    public static void bindTenant(Connection connection, UUID tenantId) throws SQLException {
        boolean transactionLocal = !connection.getAutoCommit();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, ?)")) {
            ps.setString(1, tenantId.toString());
            ps.setBoolean(2, transactionLocal);
            ps.execute();
        }
    }

    /**
     * Resets {@code app.current_tenant} to the empty string at session level. RLS policies treat
     * the empty string as "no tenant" (fail closed) via nullif(..., '').
     */
    public static void clearTenant(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '', false)");
        }
    }
}
