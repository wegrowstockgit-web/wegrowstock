package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.WorkstationSettings;
import com.invsys.repository.WorkstationSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkstationSettingsServiceTest {

    @Mock
    private WorkstationSettingsRepository repository;

    private WorkstationSettingsService service;

    private final UUID tenantId = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private final UUID userId = UUID.fromString("a0000000-0000-4000-8000-000000000101");

    @BeforeEach
    void setUp() {
        service = new WorkstationSettingsService(repository);
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getOrDefaultReturnsPdfWhenNoRow() {
        when(repository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.empty());

        WorkstationSettings settings = service.getOrDefaultForCurrentUser();

        assertThat(settings.getPrintMode()).isEqualTo("PDF");
        assertThat(settings.getLabelFormat()).isEqualTo("4x6");
        assertThat(settings.getId()).isNull();
    }

    @Test
    void updatePersistsZplWorkstation() {
        when(repository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkstationSettings saved = service.updateCurrentUser("ZPL", "Zebra ZP450", "4x6");

        assertThat(saved.getPrintMode()).isEqualTo("ZPL");
        assertThat(saved.getZplPrinterName()).isEqualTo("Zebra ZP450");
        ArgumentCaptor<WorkstationSettings> captor = ArgumentCaptor.forClass(WorkstationSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void easypostLabelFormatFollowsPrintMode() {
        WorkstationSettings zpl = new WorkstationSettings();
        zpl.setPrintMode("ZPL");
        zpl.setLabelFormat("4x6");
        assertThat(service.easypostLabelFormat(zpl)).isEqualTo("ZPL");
        assertThat(service.easypostLabelFormat(null)).isEqualTo("PDF");
    }

    @Test
    void rejectsInvalidPrintMode() {
        assertThatThrownBy(() -> service.updateCurrentUser("FAX", null, "4x6"))
                .isInstanceOf(ApiException.class);
    }
}
