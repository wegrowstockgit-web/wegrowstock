package com.invsys.gateway;

import com.invsys.tenancy.BootstrapJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicCorsWhitelistTest {

    BootstrapJdbc bootstrapJdbc;
    DynamicCorsWhitelist whitelist;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        bootstrapJdbc = mock(BootstrapJdbc.class);
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        when(bootstrapJdbc.listActiveVerifiedDomainNames()).thenReturn(List.of("acme.example"));
        whitelist = new DynamicCorsWhitelist(
                bootstrapJdbc,
                provider,
                "http://localhost:5173",
                "http://localhost:3000",
                true,
                30);
    }

    @Test
    void allowsStaticAndVerifiedHttpsOrigins() {
        assertThat(whitelist.isAllowed("http://localhost:5173")).isTrue();
        assertThat(whitelist.isAllowed("http://localhost:3000")).isTrue();
        assertThat(whitelist.isAllowed("https://acme.example")).isTrue();
        assertThat(whitelist.isAllowed("https://www.acme.example")).isTrue();
        assertThat(whitelist.isAllowed("https://evil.example")).isFalse();
        assertThat(whitelist.isAllowed("http://acme.example")).isFalse();
    }

    @Test
    void invalidateForcesRefresh() {
        assertThat(whitelist.isAllowed("https://acme.example")).isTrue();
        when(bootstrapJdbc.listActiveVerifiedDomainNames()).thenReturn(List.of());
        whitelist.invalidate();
        assertThat(whitelist.isAllowed("https://acme.example")).isFalse();
        assertThat(whitelist.isAllowed("http://localhost:5173")).isTrue();
    }
}
