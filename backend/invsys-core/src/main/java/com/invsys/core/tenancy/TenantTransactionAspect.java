package com.invsys.core.tenancy;

import com.invsys.core.common.TenantConnectionHelper;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Aspect
@Component
public class TenantTransactionAspect {

    private final DataSource dataSource;

    public TenantTransactionAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void bindTenantToTransaction() {
        TenantContext.getTenantId().ifPresent(tenantId -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                TenantConnectionHelper.bindTenant(connection, tenantId);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to bind tenant for transaction", e);
            }
        });
    }
}
