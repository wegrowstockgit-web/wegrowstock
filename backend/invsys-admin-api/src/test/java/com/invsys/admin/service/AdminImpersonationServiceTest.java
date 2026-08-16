package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.security.ImpersonationHandoffStore;
import com.invsys.core.security.JwtService;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminImpersonationServiceTest {

    @Mock BootstrapJdbc bootstrapJdbc;
    @Mock JwtService jwtService;
    @Mock ImpersonationHandoffStore impersonationHandoffStore;

    AdminImpersonationService service;

    @BeforeEach
    void setUp() {
        service = new AdminImpersonationService(
                bootstrapJdbc, jwtService, impersonationHandoffStore, "http://localhost:3000");
    }

    @Test
    void impersonateMintsTokenForPreferredUser() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        when(bootstrapJdbc.findTenantNameSlugStatus(tenantId))
                .thenReturn(Optional.of(new BootstrapJdbc.TenantNameSlugStatusRow(
                        tenantId, "Acme", "acme", "ACTIVE")));
        when(bootstrapJdbc.findImpersonationUser(tenantId))
                .thenReturn(Optional.of(new BootstrapJdbc.ImpersonationUserRow(
                        userId, "owner@acme.test", List.of("OWNER"))));
        when(bootstrapJdbc.findWarehouseIdsForUser(tenantId, userId))
                .thenReturn(List.of(warehouseId));
        when(jwtService.generateImpersonationAccessToken(eq(userId), eq(tenantId), anyList(), anyList()))
                .thenReturn("impersonation.jwt");
        when(jwtService.validateAndParse("impersonation.jwt"))
                .thenReturn(new JWTClaimsSet.Builder().jwtID("jti-1").build());

        AdminImpersonationService.ImpersonationResponse response = service.impersonate(tenantId);

        assertThat(response.accessToken()).isEqualTo("impersonation.jwt");
        assertThat(response.handoffCode()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(JwtService.IMPERSONATION_TTL_SECONDS);
        assertThat(response.email()).isEqualTo("owner@acme.test");
        assertThat(response.loginUrl()).contains("impersonateCode=");
        assertThat(response.loginUrl()).doesNotContain("impersonateToken=");
        verify(jwtService).generateImpersonationAccessToken(
                userId, tenantId, List.of("OWNER"), List.of(warehouseId));
        verify(impersonationHandoffStore).register(eq("jti-1"), any(), eq("impersonation.jwt"), any());
    }

    @Test
    void impersonateFailsWhenTenantMissing() {
        UUID tenantId = UUID.randomUUID();
        when(bootstrapJdbc.findTenantNameSlugStatus(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.impersonate(tenantId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    void impersonateFailsWhenTenantSuspended() {
        UUID tenantId = UUID.randomUUID();
        when(bootstrapJdbc.findTenantNameSlugStatus(tenantId))
                .thenReturn(Optional.of(new BootstrapJdbc.TenantNameSlugStatusRow(
                        tenantId, "Acme", "acme", "SUSPENDED")));

        assertThatThrownBy(() -> service.impersonate(tenantId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void impersonateFallsBackToAllWarehouses() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID wh = UUID.randomUUID();

        when(bootstrapJdbc.findTenantNameSlugStatus(tenantId))
                .thenReturn(Optional.of(new BootstrapJdbc.TenantNameSlugStatusRow(
                        tenantId, "Acme", "acme", "ACTIVE")));
        when(bootstrapJdbc.findImpersonationUser(tenantId))
                .thenReturn(Optional.of(new BootstrapJdbc.ImpersonationUserRow(
                        userId, "picker@acme.test", List.of("PICKER"))));
        when(bootstrapJdbc.findWarehouseIdsForUser(tenantId, userId)).thenReturn(List.of());
        when(bootstrapJdbc.findAllWarehouseIds(tenantId)).thenReturn(List.of(wh));
        when(jwtService.generateImpersonationAccessToken(any(), any(), anyList(), anyList()))
                .thenReturn("tok");
        when(jwtService.validateAndParse("tok"))
                .thenReturn(new JWTClaimsSet.Builder().jwtID("jti-2").build());

        AdminImpersonationService.ImpersonationResponse response = service.impersonate(tenantId);

        assertThat(response.accessToken()).isEqualTo("tok");
        verify(jwtService).generateImpersonationAccessToken(
                userId, tenantId, List.of("PICKER"), List.of(wh));
    }
}
