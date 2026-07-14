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
 * Binds {@code app.current_tenant} on first SQL executed through the connection (M6).
 * Inside a transaction the GUC is transaction-local; in autocommit it is bound at session
 * level and reset to '' before the connection returns to the pool, so RLS policies
 * (which use nullif(current_setting(...), '')) fail closed on recycled connections.
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
        AtomicBoolean sessionBound = new AtomicBoolean(false);
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if (requiresTenantBinding(name)) {
                bindTenantIfNeeded(delegate, sessionBound);
            } else if ("close".equals(name)) {
                clearSessionBinding(delegate, sessionBound);
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

    private void bindTenantIfNeeded(Connection connection, AtomicBoolean sessionBound) throws SQLException {
        UUID tenantId = TenantContext.getTenantId().orElse(null);
        if (tenantId != null) {
            TenantConnectionHelper.bindTenant(connection, tenantId);
            if (connection.getAutoCommit()) {
                sessionBound.set(true);
            }
        }
    }

    private void clearSessionBinding(Connection connection, AtomicBoolean sessionBound) {
        if (!sessionBound.getAndSet(false)) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                TenantConnectionHelper.clearTenant(connection);
            }
        } catch (SQLException ignored) {
            // Connection is being discarded; the pool will validate it before reuse.
        }
    }
}
