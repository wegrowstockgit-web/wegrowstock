package com.invsys.pos;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.MeResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.User;
import com.invsys.domain.subscription.AppModule;
import com.invsys.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosManagerOverrideServiceTest {

    @Mock UserRepository userRepository;
    @Mock AuthService authService;

    private PosManagerOverrideService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PosManagerOverrideService(userRepository, authService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void currentManagers_returnsActiveManagerPinHashes() {
        UUID managerId = UUID.randomUUID();
        User manager = new User();
        manager.setId(managerId);
        manager.setTenantId(tenantId);
        manager.setTerminalPinHash("hash-1");
        when(authService.currentUser()).thenReturn(me(tenantId));
        when(userRepository.findActiveUsersWithPinAndPermission(eq(tenantId), eq("pos.supervise")))
                .thenReturn(List.of(manager));

        var response = service.currentManagers();
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.managers()).hasSize(1);
        assertThat(response.managers().get(0).managerId()).isEqualTo(managerId);
        assertThat(response.managers().get(0).pinHash()).isEqualTo("hash-1");
    }

    private static MeResponse me(UUID tenantId) {
        return new MeResponse(
                UUID.randomUUID(),
                tenantId,
                "owner@demo.test",
                "Owner",
                List.of("OWNER"),
                List.of(),
                null,
                null,
                null,
                "America/New_York",
                "en",
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                List.of(AppModule.RETAIL_POS),
                "ENTERPRISE");
    }
}
