package com.invsys.core.security;

import com.invsys.core.tenancy.TenantContext;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterAppContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void posTokenCannotCallWmsApi() throws Exception {
        MockHttpServletResponse response = filter("POS", "/api/v1/products", true);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ACCESS_DENIED");
        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    void posTokenCanCallPosAndAuthApis() throws Exception {
        assertThat(filter("POS", "/api/v1/pos/session", true).getStatus()).isNotEqualTo(403);
        assertThat(filter("POS", "/api/v1/auth/me", true).getStatus()).isNotEqualTo(403);
    }

    @Test
    void wmsTokenCannotCallPosApi() throws Exception {
        MockHttpServletResponse response = filter("WMS", "/api/v1/pos/session", true);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void missingAppContextRemainsUnrestricted() throws Exception {
        assertThat(filter(null, "/api/v1/products", true).getStatus()).isNotEqualTo(403);
        assertThat(filter(null, "/api/v1/pos/session", true).getStatus()).isNotEqualTo(403);
    }

    private MockHttpServletResponse filter(String appContext, String uri, boolean expectChain)
            throws Exception {
        JwtService jwt = mock(JwtService.class);
        AuthCookieService cookies = mock(AuthCookieService.class);
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_TENANT_ID, UUID.randomUUID().toString())
                .claim("roles", List.of("OWNER"));
        if (appContext != null) {
            claims.claim(JwtService.CLAIM_APP_CONTEXT, appContext);
        }
        when(cookies.readAccessToken(org.mockito.ArgumentMatchers.any())).thenReturn("tok");
        when(jwt.validateAndParse(anyString())).thenReturn(claims.build());

        JwtAuthFilter filter = new JwtAuthFilter(jwt, cookies, mock(PortalIdentityResolver.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chained.set(true);
        filter.doFilter(request, response, chain);
        if (response.getStatus() == 403) {
            assertThat(chained.get()).isFalse();
        } else if (expectChain) {
            assertThat(chained.get()).isTrue();
        }
        return response;
    }
}
