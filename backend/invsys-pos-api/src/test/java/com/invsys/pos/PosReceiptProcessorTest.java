package com.invsys.pos;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.repository.InventoryLevelDeltaFlushRepository;
import com.invsys.pos.dto.OfflineReceiptDto;
import com.invsys.pos.dto.OfflineReceiptDto.OfflineReceiptLineDto;
import com.invsys.pos.dto.PosSyncResponse;
import com.invsys.pos.event.InventoryLevelDelta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosReceiptProcessorTest {

    @Mock LocationRepository locationRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock InventoryLevelDeltaFlushRepository deltaFlushRepository;
    @Mock JdbcTemplate tenantJdbc;

    private PosReceiptProcessor processor;
    private UUID tenantId;
    private UUID storeId;
    private UUID variantId;
    private Location store;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        store = new Location();
        store.setId(storeId);
        store.setTenantId(tenantId);
        store.setType("WAREHOUSE");
        store.setCode("STORE-01");
        store.setName("Retail Store");
        store.setPath("STORE-01");

        variant = new ProductVariant();
        variant.setId(variantId);
        variant.setTenantId(tenantId);
        variant.setSku("SKU-1");
        variant.setBarcode("7501234567890");
        variant.setPrice(new BigDecimal("12.50"));

        processor = new PosReceiptProcessor(
                locationRepository, variantRepository, deltaFlushRepository, tenantJdbc);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sync_enqueuesNegativeOnHandDeltas() {
        when(locationRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(tenantJdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);

        OfflineReceiptDto receipt = receipt(UUID.randomUUID(), List.of(
                new OfflineReceiptLineDto(variantId, "7501234567890", new BigDecimal("3"), new BigDecimal("12.50"))));

        PosSyncResponse response = processor.sync(List.of(receipt));

        assertThat(response.accepted()).isEqualTo(1);
        assertThat(response.duplicates()).isZero();
        assertThat(response.rejected()).isEmpty();
        verify(deltaFlushRepository).enqueueOnHandDelta(
                eq(tenantId), eq(variantId), eq(storeId), isNull(), isNull(),
                eq(new BigDecimal("-3")), isNull());
    }

    @Test
    void sync_skipsDuplicateReceipts() {
        when(locationRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(tenantJdbc.update(anyString(), any(), any(), any(), any())).thenReturn(0);

        PosSyncResponse response = processor.sync(List.of(receipt(
                UUID.randomUUID(),
                List.of(new OfflineReceiptLineDto(variantId, null, BigDecimal.ONE, BigDecimal.TEN)))));

        assertThat(response.duplicates()).isEqualTo(1);
        assertThat(response.accepted()).isZero();
        verify(deltaFlushRepository, never()).enqueueOnHandDelta(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sync_rejectsUnknownStore() {
        when(locationRepository.findById(storeId)).thenReturn(Optional.empty());

        PosSyncResponse response = processor.sync(List.of(receipt(
                UUID.randomUUID(),
                List.of(new OfflineReceiptLineDto(variantId, null, BigDecimal.ONE, BigDecimal.TEN)))));

        assertThat(response.rejected()).hasSize(1);
        assertThat(response.rejected().get(0).reason()).contains("Store location");
        assertThat(response.accepted()).isZero();
    }

    @Test
    void sync_rejectsStoreFromAnotherTenant() {
        Location other = new Location();
        other.setId(storeId);
        other.setTenantId(UUID.randomUUID());
        when(locationRepository.findById(storeId)).thenReturn(Optional.of(other));

        PosSyncResponse response = processor.sync(List.of(receipt(
                UUID.randomUUID(),
                List.of(new OfflineReceiptLineDto(variantId, null, BigDecimal.ONE, BigDecimal.TEN)))));

        assertThat(response.rejected()).hasSize(1);
        assertThat(response.rejected().get(0).receiptId()).isNotNull();
    }

    @Test
    void sync_resolvesVariantByUpc() {
        when(locationRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(variantRepository.findByTenantIdAndBarcode(tenantId, "7501234567890"))
                .thenReturn(Optional.of(variant));
        when(tenantJdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);

        PosSyncResponse response = processor.sync(List.of(receipt(
                UUID.randomUUID(),
                List.of(new OfflineReceiptLineDto(null, "7501234567890", new BigDecimal("2"), null)))));

        assertThat(response.accepted()).isEqualTo(1);
        ArgumentCaptor<BigDecimal> qty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(deltaFlushRepository).enqueueOnHandDelta(
                eq(tenantId), eq(variantId), eq(storeId), isNull(), isNull(), qty.capture(), isNull());
        assertThat(qty.getValue()).isEqualByComparingTo("-2");
    }

    @Test
    void sync_rejectsUnknownUpc() {
        when(locationRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(variantRepository.findByTenantIdAndBarcode(tenantId, "000")).thenReturn(Optional.empty());

        PosSyncResponse response = processor.sync(List.of(receipt(
                UUID.randomUUID(),
                List.of(new OfflineReceiptLineDto(null, "000", BigDecimal.ONE, null)))));

        assertThat(response.rejected()).hasSize(1);
        assertThat(response.rejected().get(0).reason()).contains("UPC");
    }

    @Test
    void sync_rejectsEmptyLinesAndMissingId() {
        when(locationRepository.findById(storeId)).thenReturn(Optional.of(store));

        PosSyncResponse emptyLines = processor.sync(List.of(
                new OfflineReceiptDto(UUID.randomUUID(), storeId, List.of(), "CASH", "US")));
        assertThat(emptyLines.rejected().get(0).reason()).contains("no line items");

        PosSyncResponse missingId = processor.sync(List.of(
                new OfflineReceiptDto(null, storeId, List.of(
                        new OfflineReceiptLineDto(variantId, null, BigDecimal.ONE, null)), "CASH", "US")));
        assertThat(missingId.rejected().get(0).reason()).contains("Receipt id");
    }

    @Test
    void sync_rejectsMissingStoreIdAndEmptyBatch() {
        OfflineReceiptDto noStore = new OfflineReceiptDto(
                UUID.randomUUID(),
                null,
                List.of(new OfflineReceiptLineDto(variantId, null, BigDecimal.ONE, null)),
                "CARD",
                "MX");
        PosSyncResponse response = processor.sync(List.of(noStore));
        assertThat(response.rejected().get(0).reason()).contains("Store location is required");

        assertThatThrownBy(() -> processor.sync(List.of()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> processor.sync(null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void sync_rejectsNullReceiptInBatch() {
        PosSyncResponse response = processor.sync(java.util.Arrays.asList((OfflineReceiptDto) null));
        assertThat(response.rejected()).hasSize(1);
        assertThat(response.rejected().get(0).receiptId()).isNull();
    }

    @Test
    void toSaleDelta_rejectsNonPositiveQtyAndMissingIdentity() {
        assertThatThrownBy(() -> processor.toSaleDelta(
                tenantId, storeId, new OfflineReceiptLineDto(variantId, null, BigDecimal.ZERO, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> processor.toSaleDelta(
                tenantId, storeId, new OfflineReceiptLineDto(null, "  ", BigDecimal.ONE, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> processor.toSaleDelta(tenantId, storeId, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void toSaleDelta_rejectsUnknownVariantId() {
        when(variantRepository.findById(variantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> processor.toSaleDelta(
                tenantId, storeId, new OfflineReceiptLineDto(variantId, null, BigDecimal.ONE, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void toSaleDelta_rejectsVariantFromAnotherTenant() {
        ProductVariant other = new ProductVariant();
        other.setId(variantId);
        other.setTenantId(UUID.randomUUID());
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> processor.toSaleDelta(
                tenantId, storeId, new OfflineReceiptLineDto(variantId, null, BigDecimal.ONE, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void inventoryLevelDelta_isNegativeSale() {
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        InventoryLevelDelta delta = processor.toSaleDelta(
                tenantId, storeId, new OfflineReceiptLineDto(variantId, "x", new BigDecimal("4"), null));
        assertThat(delta.onHandDelta()).isEqualByComparingTo("-4");
        assertThat(delta.locationId()).isEqualTo(storeId);
        assertThat(delta.variantId()).isEqualTo(variantId);
        assertThat(delta.lotId()).isNull();
        assertThat(delta.lpnId()).isNull();
    }

    @Test
    void tryClaim_returnsTrueWhenInserts() {
        OfflineReceiptDto receipt = receipt(UUID.randomUUID(), List.of(
                new OfflineReceiptLineDto(variantId, null, BigDecimal.ONE, null)));
        when(tenantJdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        assertThat(processor.tryClaim(tenantId, receipt)).isTrue();
        when(tenantJdbc.update(anyString(), any(), any(), any(), any())).thenReturn(0);
        assertThat(processor.tryClaim(tenantId, receipt)).isFalse();
    }

    @Test
    void sync_requiresTenantContext() {
        TenantContext.clear();
        assertThatThrownBy(() -> processor.sync(List.of(receipt(
                UUID.randomUUID(),
                List.of(new OfflineReceiptLineDto(variantId, null, BigDecimal.ONE, null))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context");
    }

    private OfflineReceiptDto receipt(UUID id, List<OfflineReceiptLineDto> lines) {
        return new OfflineReceiptDto(id, storeId, lines, "CASH", "US");
    }
}
