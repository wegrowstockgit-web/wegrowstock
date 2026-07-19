package com.invsys.auth;

import com.invsys.repository.CustomerUserMappingRepository;
import com.invsys.repository.SupplierUserMappingRepository;
import com.invsys.tenancy.TenantContext;
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

class JwtAuthFilterTenantCleanupTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void clearsTenantContextEvenWhenChainThrows() {
        AuthCookieService cookies = mock(AuthCookieService.class);
        JwtAuthFilter filter = new JwtAuthFilter(
                mock(JwtService.class),
                cookies,
                mock(CustomerUserMappingRepository.class),
                mock(SupplierUserMappingRepository.class));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/v1/inventory/levels");
        when(cookies.readAccessToken(request)).thenReturn(null);

        FilterChain chain = (req, res) -> {
            TenantContext.setTenantId(UUID.randomUUID());
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class);

        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    void clearsTenantContextAfterSuccessfulChain() throws Exception {
        AuthCookieService cookies = mock(AuthCookieService.class);
        JwtAuthFilter filter = new JwtAuthFilter(
                mock(JwtService.class),
                cookies,
                mock(CustomerUserMappingRepository.class),
                mock(SupplierUserMappingRepository.class));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/v1/inventory/levels");
        when(cookies.readAccessToken(request)).thenReturn(null);

        FilterChain chain = (req, res) -> TenantContext.setTenantId(UUID.randomUUID());
        filter.doFilter(request, response, chain);

        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    void stillClearsTenantContextWhenAsyncStarted() throws Exception {
        AuthCookieService cookies = mock(AuthCookieService.class);
        JwtAuthFilter filter = new JwtAuthFilter(
                mock(JwtService.class),
                cookies,
                mock(CustomerUserMappingRepository.class),
                mock(SupplierUserMappingRepository.class));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/v1/dashboard/stream");
        when(request.isAsyncStarted()).thenReturn(true);
        when(cookies.readAccessToken(request)).thenReturn(null);

        FilterChain chain = (req, res) -> TenantContext.setTenantId(UUID.randomUUID());
        filter.doFilter(request, response, chain);

        // Kickoff thread must not keep tenant ThreadLocals; async dispatch re-binds JWT.
        assertThat(TenantContext.getTenantId()).isEmpty();
    }
}
