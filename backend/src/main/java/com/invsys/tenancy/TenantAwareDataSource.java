package com.invsys.tenancy;

import com.invsys.common.TenantConnectionHelper;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Binds {@code app.current_tenant} with a strictly transaction-local GUC
 * ({@code set_config(..., true)}) so PgBouncer transaction pooling cannot leak tenants.
 * <p>
 * When the connection is still in autocommit (no Spring transaction), this proxy opens a
 * connection-scoped transaction before binding so the GUC survives subsequent statements
 * until {@code close()}, then commits and restores autocommit.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return proxy(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return proxy(super.getConnection(username, password));
    }

    private Connection proxy(Connection delegate) {
        AtomicBoolean openedLocalTx = new AtomicBoolean(false);
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if (requiresTenantBinding(name)) {
                bindTenantIfNeeded(delegate, openedLocalTx);
            } else if ("close".equals(name)) {
                finalizeLocalTransaction(delegate, openedLocalTx);
                clearTenantQuietly(delegate);
            }
            return method.invoke(delegate, args);
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                handler);
    }

    private boolean requiresTenantBinding(String methodName) {
        return methodName.startsWith("prepareStatement")
                || methodName.startsWith("createStatement")
                || methodName.startsWith("prepareCall");
    }

    private void bindTenantIfNeeded(Connection connection, AtomicBoolean openedLocalTx) throws SQLException {
        UUID tenantId = TenantContext.getTenantId().orElse(null);
        if (tenantId == null) {
            return;
        }
        if (connection.getAutoCommit() && openedLocalTx.compareAndSet(false, true)) {
            connection.setAutoCommit(false);
        }
        TenantConnectionHelper.bindTenant(connection, tenantId);
        TenantConnectionHelper.bindUser(connection, TenantContext.getUserId().orElse(null));
    }

    private void finalizeLocalTransaction(Connection connection, AtomicBoolean openedLocalTx) {
        if (!openedLocalTx.getAndSet(false)) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.commit();
                connection.setAutoCommit(true);
            }
        } catch (SQLException ignored) {
            try {
                if (!connection.isClosed()) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
            } catch (SQLException ignoredAgain) {
                // Pool will validate/discard the connection.
            }
        }
    }

    private void clearTenantQuietly(Connection connection) {
        try {
            if (!connection.isClosed()) {
                TenantConnectionHelper.clearTenant(connection);
            }
        } catch (SQLException ignored) {
            // Connection is being discarded; the pool will validate it before reuse.
        }
    }
}
