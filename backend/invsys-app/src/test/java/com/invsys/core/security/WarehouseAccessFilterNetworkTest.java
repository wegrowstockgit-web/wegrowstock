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
        MockHttpServletResponse response = run(NetworkAccessPolicy.Decision.ALLOW, true);
        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    private MockHttpServletResponse run(NetworkAccessPolicy.Decision decision, boolean expectChain)
            throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        NetworkAccessPolicy policy = mock(NetworkAccessPolicy.class);
        ClientIpResolver ip = mock(ClientIpResolver.class);
        when(ip.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("203.0.113.9");
        when(policy.highestForRoleCodes(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(NetworkAccessLevel.STRICT_INTERNAL);
        when(policy.allowedCidrBlocks(tenantId)).thenReturn(List.of("10.0.0.0/8"));
        when(policy.evaluate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean()
        )).thenReturn(decision);

        WarehouseAccessFilter filter = new WarehouseAccessFilter(new ObjectMapper(), policy, ip);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setRequestURI("/api/v1/products");
        request.setAttribute(JwtAuthFilter.ATTR_MFA_VERIFIED, false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID(), null, List.of(new SimpleGrantedAuthority("ROLE_PICKER"))));
        AtomicBoolean chained = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chained.set(true);
        filter.doFilter(request, response, chain);
        if (expectChain) {
            assertThat(chained.get()).isTrue();
        } else {
            assertThat(chained.get()).isFalse();
        }
        return response;
    }
}
