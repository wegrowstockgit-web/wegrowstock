package com.invsys.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersWhenPeerIsNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "127.0.0.1, 203.0.113.10");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void peelsTrustedHopsFromTheRight() {
        ClientIpResolver resolver = new ClientIpResolver("172.16.0.0/12");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Forwarded-For", "127.0.0.1, 203.0.113.44");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.44");
    }

    @Test
    void prefersRealIpOverSpoofedLeftmostXff() {
        ClientIpResolver resolver = new ClientIpResolver("172.16.0.0/12");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "198.51.100.9");
        request.addHeader("X-Forwarded-For", "127.0.0.1, 198.51.100.9");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.9");
    }
}
