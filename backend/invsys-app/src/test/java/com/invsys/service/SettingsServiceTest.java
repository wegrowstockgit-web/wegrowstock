package com.invsys.service;

import com.invsys.api.dto.TenantSettingsDto;
import com.invsys.core.common.ApiException;
import com.invsys.core.integration.OutboxService;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.TenantSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock TenantSettingsRepository repository;
    @Mock OutboxService outboxService;
    @Mock TenantSettingsCacheService cacheService;

    SettingsService service;
    UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new SettingsService(repository, outboxService, cacheService);
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void patchPersistsRetailPosFieldsAndReturnsNormalizedDtoKeys() {
        TenantSettings existing = TenantSettings.withDefaults(tenantId);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.save(any(TenantSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> saved = service.patchSettings(Map.of(
                TenantSettingsDto.KEY_RECEIPT_HEADER, "Demo Corp\nRFC DEMO010101AAA",
                TenantSettingsDto.KEY_RECEIPT_FOOTER, "Gracias por su compra",
                TenantSettingsDto.KEY_DEFAULT_CURRENCY, "mxn",
                TenantSettingsDto.KEY_REQUIRE_BLIND_CLOSEOUT, true,
                TenantSettingsDto.KEY_ENABLE_CFDI, "true"));

        assertThat(saved.get(TenantSettingsDto.KEY_DEFAULT_CURRENCY)).isEqualTo("MXN");
        assertThat(saved.get(TenantSettingsDto.KEY_REQUIRE_BLIND_CLOSEOUT)).isEqualTo(true);
        assertThat(saved.get(TenantSettingsDto.KEY_ENABLE_CFDI)).isEqualTo(true);
        assertThat(saved.get(TenantSettingsDto.KEY_RECEIPT_HEADER)).asString().contains("RFC");

        ArgumentCaptor<TenantSettings> captor = ArgumentCaptor.forClass(TenantSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPosDefaultCurrency()).isEqualTo("MXN");
        assertThat(captor.getValue().getPosRequireBlindCloseout()).isTrue();
        assertThat(captor.getValue().getPosEnableCfdiInvoicing()).isTrue();
        verify(cacheService).invalidate(tenantId);
    }

    @Test
    void patchRejectsUnsupportedPosCurrency() {
        TenantSettings existing = TenantSettings.withDefaults(tenantId);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.patchSettings(Map.of(
                TenantSettingsDto.KEY_DEFAULT_CURRENCY, "EUR")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("USD or MXN");
    }

    @Test
    void getSettingsMergesPosDefaultsWhenKeysMissing() {
        TenantSettings existing = TenantSettings.withDefaults(tenantId);
        existing.getSettings().remove(TenantSettingsDto.KEY_DEFAULT_CURRENCY);
        when(cacheService.get(tenantId)).thenReturn(Optional.empty());
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        Map<String, Object> loaded = service.getSettings();
        assertThat(loaded.get(TenantSettingsDto.KEY_DEFAULT_CURRENCY)).isEqualTo("USD");
        assertThat(loaded.get(TenantSettingsDto.KEY_REQUIRE_BLIND_CLOSEOUT)).isEqualTo(false);
        assertThat(loaded.get(TenantSettingsDto.KEY_ENABLE_CFDI)).isEqualTo(false);
    }

    @Test
    void dtoFromMapFallsBackForBlankCurrency() {
        TenantSettingsDto dto = TenantSettingsDto.fromSettingsMap(new HashMap<>(Map.of(
                TenantSettingsDto.KEY_DEFAULT_CURRENCY, "  ")));
        assertThat(dto.posDefaultCurrency()).isEqualTo("USD");
        assertThat(TenantSettingsDto.fromSettingsMap(null).posDefaultCurrency()).isEqualTo("USD");
    }

    @Test
    void applyPatchRejectsOversizedReceiptText() {
        Map<String, Object> settings = new HashMap<>();
        assertThatThrownBy(() -> TenantSettingsDto.applyPatch(settings, Map.of(
                TenantSettingsDto.KEY_RECEIPT_HEADER, "x".repeat(2001))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("2000");
        assertThatThrownBy(() -> TenantSettingsDto.applyPatch(settings, Map.of(
                TenantSettingsDto.KEY_RECEIPT_FOOTER, "y".repeat(2001))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void dtoWriteAndPatchCoverNullAndBooleanCoercion() {
        TenantSettingsDto.defaults().writeTo(null);
        TenantSettingsDto.applyPatch(null, Map.of(TenantSettingsDto.KEY_DEFAULT_CURRENCY, "USD"));
        TenantSettingsDto.applyPatch(new HashMap<>(), null);

        Map<String, Object> settings = new HashMap<>();
        TenantSettingsDto.putDefaults(settings);
        Map<String, Object> patch = new HashMap<>();
        patch.put(TenantSettingsDto.KEY_RECEIPT_HEADER, "");
        patch.put(TenantSettingsDto.KEY_RECEIPT_FOOTER, "Thanks");
        patch.put(TenantSettingsDto.KEY_REQUIRE_BLIND_CLOSEOUT, "true");
        patch.put(TenantSettingsDto.KEY_ENABLE_CFDI, null);
        TenantSettingsDto.applyPatch(settings, patch);
        assertThat(settings.get(TenantSettingsDto.KEY_RECEIPT_HEADER)).isEqualTo("");
        assertThat(settings.get(TenantSettingsDto.KEY_REQUIRE_BLIND_CLOSEOUT)).isEqualTo(true);
        assertThat(settings.get(TenantSettingsDto.KEY_ENABLE_CFDI)).isEqualTo(false);

        TenantSettingsDto nullable = new TenantSettingsDto(null, null, null, null, null);
        Map<String, Object> written = new HashMap<>();
        nullable.writeTo(written);
        assertThat(written.get(TenantSettingsDto.KEY_DEFAULT_CURRENCY)).isEqualTo("USD");
        assertThat(written.get(TenantSettingsDto.KEY_REQUIRE_BLIND_CLOSEOUT)).isEqualTo(false);
    }

    @Test
    void flushCacheInvalidatesThenReloads() {
        TenantSettings existing = TenantSettings.withDefaults(tenantId);
        when(cacheService.get(tenantId)).thenReturn(Optional.empty());
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        Map<String, Object> reloaded = service.flushCache();
        verify(cacheService).invalidate(tenantId);
        assertThat(reloaded.get(TenantSettingsDto.KEY_DEFAULT_CURRENCY)).isEqualTo("USD");
    }

    @Test
    void patchAppliesTypedColumnsAndEmitsCostingChange() {
        TenantSettings existing = TenantSettings.withDefaults(tenantId);
        existing.getSettings().put("costing_method", "MOVING_AVERAGE");
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.save(any(TenantSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> saved = service.patchSettings(Map.of(
                "blind_cycle_counts", "true",
                "max_auto_adjust_value", "25.50",
                "rma_auto_approve_max_value", "10",
                "predictive_replenishment_enabled", "true",
                "costing_method", "FIFO"));

        assertThat(saved.get("blind_cycle_counts")).isEqualTo(true);
        assertThat(existing.isPredictiveReplenishmentEnabled()).isTrue();
        verify(outboxService).append(org.mockito.ArgumentMatchers.eq("TENANT"),
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq("COSTING_METHOD_CHANGED"),
                any());
    }

    @Test
    void patchRejectsOversizedPayload() {
        TenantSettings existing = TenantSettings.withDefaults(tenantId);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        Map<String, Object> huge = new HashMap<>();
        for (int i = 0; i < 65; i++) {
            huge.put("k" + i, i);
        }
        assertThatThrownBy(() -> service.patchSettings(huge))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("64");
    }
}
