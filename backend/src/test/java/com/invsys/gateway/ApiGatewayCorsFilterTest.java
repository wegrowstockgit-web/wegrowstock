package com.invsys.gateway;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiGatewayCorsFilterTest {

    DynamicCorsWhitelist whitelist;
    ApiGatewayCorsFilter filter;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        whitelist = mock(DynamicCorsWhitelist.class);
        filter = new ApiGatewayCorsFilter(whitelist);
        chain = mock(FilterChain.class);
    }

    @Test
    void rejectsUnknownOriginBeforeHandlers() throws Exception {
        when(whitelist.isAllowed("https://evil.example")).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CORS_ORIGIN_DENIED");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void preflightSucceedsForAllowedOrigin() throws Exception {
        when(whitelist.isAllowed("http://localhost:5173")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:5173");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void sameOriginWithoutHeaderPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
