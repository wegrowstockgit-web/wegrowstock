package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicShowroomTenantResolverTest {

    @Mock BootstrapJdbc bootstrapJdbc;

    @Test
    void resolvesSlugViaBootstrap() {
        UUID tenantId = UUID.randomUUID();
        when(bootstrapJdbc.findTenantIdBySlug("acme")).thenReturn(Optional.of(tenantId));
        PublicShowroomTenantResolver resolver = new PublicShowroomTenantResolver(bootstrapJdbc);
        assertThat(resolver.resolve("acme", null)).isEqualTo(tenantId);
    }

    @Test
    void fallsBackToSingleActiveTenant() {
        UUID only = UUID.randomUUID();
        when(bootstrapJdbc.listActiveTenantIds()).thenReturn(List.of(only));
        PublicShowroomTenantResolver resolver = new PublicShowroomTenantResolver(bootstrapJdbc);
        assertThat(resolver.resolve(null, null)).isEqualTo(only);
    }

    @Test
    void requiresSlugWhenMultipleTenants() {
        when(bootstrapJdbc.listActiveTenantIds()).thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        PublicShowroomTenantResolver resolver = new PublicShowroomTenantResolver(bootstrapJdbc);
        assertThatThrownBy(() -> resolver.resolve(null, " "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("X-Tenant-Slug");
    }
}
