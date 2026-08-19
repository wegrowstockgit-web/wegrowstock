package com.invsys.core.security;

import com.invsys.domain.NetworkAccessLevel;
import com.invsys.core.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarehouseAccessFilterNetworkTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void externalStrictRoleReturns403() throws Exception {
        MockHttpServletResponse response = run(NetworkAccessPolicy.Decision.DENY_STRICT, false);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("internal corporate network");
    }

    @Test
    void externalMfaWithoutClaimReturns401() throws Exception {
        MockHttpServletResponse response = run(NetworkAccessPolicy.Decision.MFA_REQUIRED, false);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MFA_REQUIRED_FOR_EXTERNAL_ACCESS");
    }

    @Test
    void roamingAllowsChain() throws Exception {
        MockHttpServletResponse response = run(NetworkAccessPolicy.Decision.ALLOW, true, false);
        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    @Test
    void supportImpersonationBypassesStrictFence() throws Exception {
        MockHttpServletResponse response = run(NetworkAccessPolicy.Decision.DENY_STRICT, true, true);
        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    private MockHttpServletResponse run(NetworkAccessPolicy.Decision decision, boolean expectChain)
            throws Exception {
        return run(decision, expectChain, false);
    }

    private MockHttpServletResponse run(NetworkAccessPolicy.Decision decision, boolean expectChain,
                                        boolean supportImpersonation)
            throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);
        NetworkAccessPolicy policy = mock(NetworkAccessPolicy.class);
        ClientIpResolver ip = mock(ClientIpResolver.class);
        GeoIpService geoIpService = mock(GeoIpService.class);
        LoginSecurityService loginSecurityService = mock(LoginSecurityService.class);
        when(ip.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("203.0.113.9");
        when(ip.normalizeOrUnknown("203.0.113.9")).thenReturn("203.0.113.9");
        when(ip.resolveDetailed(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ClientIpResolver.ResolvedClientIp("203.0.113.9", false));
        when(geoIpService.resolveLocation("203.0.113.9")).thenReturn("Dallas, TX, US");
        when(policy.highestForRoleCodes(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(NetworkAccessLevel.STRICT_INTERNAL);
        when(policy.allowedCidrBlocks(tenantId)).thenReturn(List.of("10.0.0.0/8"));
        when(policy.evaluate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()
        )).thenReturn(decision);

        WarehouseAccessFilter filter = new WarehouseAccessFilter(
                new ObjectMapper(), policy, ip, geoIpService, loginSecurityService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setRequestURI("/api/v1/products");
        request.setAttribute(JwtAuthFilter.ATTR_MFA_VERIFIED, false);
        request.setAttribute(JwtAuthFilter.ATTR_SUPPORT_IMPERSONATION, supportImpersonation);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_PICKER"))));
        AtomicBoolean chained = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chained.set(true);
        filter.doFilter(request, response, chain);
        if (expectChain) {
            assertThat(chained.get()).isTrue();
        } else {
            assertThat(chained.get()).isFalse();
        }
        if (decision == NetworkAccessPolicy.Decision.DENY_STRICT && !supportImpersonation) {
            org.mockito.Mockito.verify(loginSecurityService)
                    .recordLoginBlockedCidr(userId, "203.0.113.9", "Dallas, TX, US");
        } else {
            org.mockito.Mockito.verify(loginSecurityService, org.mockito.Mockito.never())
                    .recordLoginBlockedCidr(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
        return response;
    }
}
