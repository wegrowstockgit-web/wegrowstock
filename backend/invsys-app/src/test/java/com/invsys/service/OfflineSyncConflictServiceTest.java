package com.invsys.service;

import com.invsys.api.dto.OfflineSyncConflictView;
import com.invsys.core.common.ApiException;
import com.invsys.domain.ConflictActionType;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.domain.OfflineSyncConflict;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.User;
import com.invsys.repository.OfflineSyncConflictRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.service.conflict.ConflictSchemaMetadataGenerator;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.invsys.modules.inventory.service.InventoryService;

@ExtendWith(MockitoExtension.class)
class OfflineSyncConflictServiceTest {

    @Mock OfflineSyncConflictRepository repository;
    @Mock InventoryService inventoryService;
    @Mock ProductVariantRepository variantRepository;
    @Mock UserRepository userRepository;

    OfflineSyncConflictService service;
    UUID tenantId;
    UUID pickerId;
    UUID managerId;
    UUID warehouseId;
    UUID variantId;

    @BeforeEach
    void setUp() {
        service = new OfflineSyncConflictService(
                repository,
                new ConflictSchemaMetadataGenerator(),
                inventoryService,
                variantRepository,
                userRepository);
        tenantId = UUID.randomUUID();
        pickerId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(managerId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sinkAttachesSchemaMetadataAndActionType() {
        when(repository.save(any())).thenAnswer(inv -> {
            OfflineSyncConflict row = inv.getArgument(0);
            row.setId(UUID.randomUUID());
            return row;
        });

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", "/api/v1/fulfillment/scan");
        payload.put("body", Map.of(
                "mode", "receive",
                "barcode", "111",
                "quantity", 2,
                "warehouseId", warehouseId.toString()));

        OfflineSyncConflict saved = service.sink(
                payload,
                "BIN_FULL: allocated bin location is full",
                ConflictActionType.INBOUND_RECEIVE,
                pickerId,
                "/api/v1/fulfillment/scan");

        assertThat(saved.getActionType()).isEqualTo(ConflictActionType.INBOUND_RECEIVE);
        assertThat(saved.getPickerUserId()).isEqualTo(pickerId);
        assertThat(saved.getSchemaMetadata()).isNotEmpty();
        assertThat(saved.getSchemaMetadata())
                .anySatisfy(m -> assertThat(m.get("key")).isEqualTo("quantity"));
    }

    @Test
    void resolveConflictPatchesPayloadAndStampsManagerOverride() {
        OfflineSyncConflict pending = pendingReceiveConflict();
        when(repository.findByTenantIdAndId(tenantId, pending.getId())).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        when(variantRepository.findByTenantIdAndBarcode(tenantId, "111"))
                .thenReturn(Optional.of(variant));

        InventoryLedger ledger = new InventoryLedger();
        ledger.setCreatedBy(managerId);
        ledger.setReasonCode(OfflineSyncConflict.REASON_OFFLINE_CONFLICT_OVERRIDE);
        when(inventoryService.adjust(
                eq(variantId),
                eq(warehouseId),
                isNull(),
                any(),
                any(BigDecimal.class),
                eq(OfflineSyncConflict.REASON_OFFLINE_CONFLICT_OVERRIDE),
                any(),
                any()))
                .thenReturn(ledger);

        User picker = new User();
        picker.setDisplayName("Floor Picker");
        when(userRepository.findByTenantIdAndId(tenantId, pickerId)).thenReturn(Optional.of(picker));

        OfflineSyncConflictView view = service.resolveConflict(
                pending.getId(), Map.of("quantity", 5));

        assertThat(view.status()).isEqualTo(OfflineSyncConflict.STATUS_RESOLVED_AND_REPLAYED);
        assertThat(view.resolvedByUserId()).isEqualTo(managerId);
        assertThat(view.humanSummary()).contains("Floor Picker").contains("Inbound Receive");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) pending.getPayload().get("body");
        assertThat(new BigDecimal(String.valueOf(body.get("quantity"))))
                .isEqualByComparingTo("5");

        ArgumentCaptor<BigDecimal> delta = ArgumentCaptor.forClass(BigDecimal.class);
        verify(inventoryService).adjust(
                eq(variantId),
                eq(warehouseId),
                isNull(),
                any(),
                delta.capture(),
                eq(OfflineSyncConflict.REASON_OFFLINE_CONFLICT_OVERRIDE),
                any(),
                any());
        assertThat(delta.getValue()).isEqualByComparingTo("5");
    }

    @Test
    void resolveConflictRejectsImmutableFieldCorrection() {
        OfflineSyncConflict pending = pendingReceiveConflict();
        when(repository.findByTenantIdAndId(tenantId, pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.resolveConflict(
                pending.getId(), Map.of("barcode", "hacked")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void dismissMarksDiscarded() {
        OfflineSyncConflict pending = pendingReceiveConflict();
        when(repository.findByTenantIdAndId(tenantId, pending.getId())).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByTenantIdAndId(tenantId, pickerId)).thenReturn(Optional.empty());

        OfflineSyncConflictView view = service.dismiss(pending.getId());
        assertThat(view.status()).isEqualTo(OfflineSyncConflict.STATUS_DISCARDED);
    }

    private OfflineSyncConflict pendingReceiveConflict() {
        OfflineSyncConflict row = new OfflineSyncConflict();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setPickerUserId(pickerId);
        row.setActionType(ConflictActionType.INBOUND_RECEIVE);
        row.setRequestUrl("/api/v1/fulfillment/scan");
        row.setErrorMessage("BIN_FULL: allocated bin location is full");
        row.setStatus(OfflineSyncConflict.STATUS_PENDING);
        row.setSchemaMetadata(new ConflictSchemaMetadataGenerator()
                .generateAsMaps("/api/v1/fulfillment/scan", ConflictActionType.INBOUND_RECEIVE,
                        Map.of("mode", "receive")));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", "/api/v1/fulfillment/scan");
        payload.put("body", new LinkedHashMap<>(Map.of(
                "mode", "receive",
                "barcode", "111",
                "quantity", 2,
                "warehouseId", warehouseId.toString())));
        row.setPayload(payload);
        return row;
    }
}
