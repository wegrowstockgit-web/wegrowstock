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
        assertThat(resolver.resolveClientIp(request)).isEqualTo("198.51.100.9");
    }

    @Test
    void classifiesPrivateAndPublicNetworks() {
        assertThat(ClientIpResolver.isPrivateNetwork("10.1.2.3")).isTrue();
        assertThat(ClientIpResolver.isPrivateNetwork("192.168.1.20")).isTrue();
        assertThat(ClientIpResolver.isPrivateNetwork("172.16.9.1")).isTrue();
        assertThat(ClientIpResolver.isPrivateNetwork("198.51.100.45")).isFalse();
        assertThat(ClientIpResolver.suggestedCidr("198.51.100.45")).isEqualTo("198.51.100.45/32");
        assertThat(ClientIpResolver.networkHint("10.0.0.8")).isEqualTo("Internal VPN / LAN");
        assertThat(ClientIpResolver.networkHint("198.51.100.45")).isEqualTo("Public Corporate Gateway");
        assertThat(ClientIpResolver.networkHint("unknown")).isEqualTo("Unknown network");
    }
}
