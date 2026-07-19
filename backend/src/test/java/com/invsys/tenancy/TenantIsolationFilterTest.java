package com.invsys.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantIsolationFilterTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void clearsTenantContextAfterSuccessfulChain() throws Exception {
        TenantIsolationFilter filter = new TenantIsolationFilter();
        FilterChain chain = (req, res) -> TenantContext.setTenantId(UUID.randomUUID());

        filter.doFilter(mock(HttpServletRequest.class), mock(HttpServletResponse.class), chain);

        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    void clearsTenantContextEvenWhenChainThrows() {
        TenantIsolationFilter filter = new TenantIsolationFilter();
        FilterChain chain = (req, res) -> {
            TenantContext.setTenantId(UUID.randomUUID());
            TenantContext.setUserId(UUID.randomUUID());
            throw new ServletException("downstream failure");
        };

        assertThatThrownBy(() ->
                filter.doFilter(mock(HttpServletRequest.class), mock(HttpServletResponse.class), chain))
                .isInstanceOf(ServletException.class);

        assertThat(TenantContext.getTenantId()).isEmpty();
        assertThat(TenantContext.getUserId()).isEmpty();
    }

    @Test
    void clearsTenantContextWhenAsyncStartedButLeavesSecurityHolderAlone() throws Exception {
        TenantIsolationFilter filter = new TenantIsolationFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.isAsyncStarted()).thenReturn(true);

        FilterChain chain = (req, res) -> TenantContext.setTenantId(UUID.randomUUID());
        filter.doFilter(request, mock(HttpServletResponse.class), chain);

        assertThat(TenantContext.getTenantId()).isEmpty();
    }
}
