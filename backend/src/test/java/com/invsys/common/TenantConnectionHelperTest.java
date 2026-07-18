package com.invsys.common;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantConnectionHelperTest {

    @Test
    void bindTenantAlwaysUsesTransactionLocalSetConfig() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);

        UUID tenantId = UUID.randomUUID();
        TenantConnectionHelper.bindTenant(connection, tenantId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertThat(sql.getValue()).isEqualTo("SELECT set_config('app.current_tenant', ?, true)");
        verify(ps).setString(1, tenantId.toString());
        verify(ps).execute();
    }

    @Test
    void clearTenantWipesSessionGucAndDeallocates() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        Statement st = mock(Statement.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(connection.createStatement()).thenReturn(st);

        TenantConnectionHelper.clearTenant(connection);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertThat(sql.getValue()).isEqualTo("SELECT set_config('app.current_tenant', ?, false)");
        verify(ps).setString(1, "");
        verify(ps).execute();
        verify(st).execute("DEALLOCATE ALL");
    }
}
