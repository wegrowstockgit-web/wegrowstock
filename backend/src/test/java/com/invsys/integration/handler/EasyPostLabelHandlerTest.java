package com.invsys.integration.handler;

import com.invsys.domain.IntegrationSyncLog;
import com.invsys.integration.IntegrationRateLimiter;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.service.ShippingCredentialService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EasyPostLabelHandlerTest {

    @Mock IntegrationSyncLogRepository syncLogRepository;
    @Mock IntegrationRateLimiter rateLimiter;
    @Mock ShippingCredentialService shippingCredentialService;

    EasyPostLabelHandler handler;
    UUID tenantId;

    @BeforeEach
    void setUp() {
        handler = new EasyPostLabelHandler(syncLogRepository, rateLimiter, shippingCredentialService);
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void resolvesFedExVaultKeyWhenCarrierPresent() {
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shippingCredentialService.resolveApiKey("FEDEX")).thenReturn("fedex-key");

        handler.handle(tenantId, UUID.randomUUID(), "EASYPOST_LABEL", Map.of("carrier", "FEDEX"));

        verify(shippingCredentialService).resolveApiKey("FEDEX");
        ArgumentCaptor<IntegrationSyncLog> captor = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository).save(captor.capture());
        assertThat(captor.getValue().getSystem()).isEqualTo("FEDEX");
        assertThat(captor.getValue().getStatus()).isEqualTo("SYNCED");
    }

    @Test
    void failsWhenVaultKeyMissing() {
        when(shippingCredentialService.resolveApiKey(eq("EASYPOST")))
                .thenThrow(new IllegalStateException("No shipping credentials"));

        assertThatThrownBy(() ->
                handler.handle(tenantId, UUID.randomUUID(), "EASYPOST_LABEL", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shipping credentials");
    }
}
