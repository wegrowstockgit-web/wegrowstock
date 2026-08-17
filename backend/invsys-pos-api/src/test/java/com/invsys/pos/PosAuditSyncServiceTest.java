package com.invsys.pos;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.AuditLog;
import com.invsys.pos.dto.PosAuditEventDto;
import com.invsys.repository.AuditLogRepository;
import com.invsys.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosAuditSyncServiceTest {

    @Mock AuditService auditService;
    @Mock AuditLogRepository auditLogRepository;

    private PosAuditSyncService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PosAuditSyncService(auditService, auditLogRepository);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sync_writesImmutableAuditAndDedupesByEventId() {
        UUID eventId = UUID.randomUUID();
        PosAuditEventDto event = new PosAuditEventDto(
                eventId, 99L, "cashier-1", "tx_void", "order-1", "sku-1",
                new BigDecimal("18.00"), "manager-9");

        when(auditService.record(any(), any(), any(), any())).thenReturn(new AuditLog());
        when(auditLogRepository.existsByTenantIdAndEntityId(tenantId, eventId))
                .thenReturn(false, true);

        var first = service.sync(List.of(event));
        var second = service.sync(List.of(event));

        assertThat(first.accepted()).isEqualTo(1);
        assertThat(second.duplicates()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> diff = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq("POS_TX_VOID"), eq("POS_EXCEPTION"), eq(eventId), diff.capture());
        assertThat(diff.getValue()).containsEntry("valueVoided", "18.00");
        assertThat(diff.getValue()).containsEntry("managerOverrideId", "manager-9");
        assertThat(diff.getValue()).containsEntry("source", "POS_OFFLINE_AUDIT");
    }

    @Test
    void sync_rejectsUnknownTypesAndEmptyBatches() {
        UUID eventId = UUID.randomUUID();
        var result = service.sync(List.of(new PosAuditEventDto(
                eventId, 1L, "c", "TIP_OUT", "o", null, BigDecimal.ONE, null)));
        assertThat(result.rejected()).hasSize(1);
        verify(auditService, never()).record(any(), any(), any(), any());

        assertThatThrownBy(() -> service.sync(List.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Audit event batch is required");
    }
}
