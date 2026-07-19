package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.rtls.RtlsTelemetryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundReceivingServiceTest {

    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock PurchaseOrderLineRepository lineRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock LocationRepository locationRepository;
    @Mock InventoryService inventoryService;
    @Mock PutawayStrategyService putawayStrategyService;
    @Mock UomConversionService uomConversionService;
    @Mock PurchaseOrderService purchaseOrderService;
    @Mock RtlsTelemetryService rtlsTelemetryService;
    @Mock TenantSettingsRepository tenantSettingsRepository;

    InboundReceivingService service;
    UUID tenantId;
    UUID poId;
    UUID lineId;
    UUID variantId;
    UUID locationId;

    @BeforeEach
    void setUp() {
        service = new InboundReceivingService(
                purchaseOrderRepository,
                lineRepository,
                variantRepository,
                locationRepository,
                inventoryService,
                putawayStrategyService,
                uomConversionService,
                purchaseOrderService,
                rtlsTelemetryService,
                tenantSettingsRepository);
        tenantId = UUID.randomUUID();
        poId = UUID.randomUUID();
        lineId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void lookupPoByNumber() {
        PurchaseOrder po = po("IN_TRANSIT");
        when(purchaseOrderRepository.findByTenantIdAndNumberIgnoreCase(tenantId, "PO-1"))
                .thenReturn(Optional.of(po));
        when(lineRepository.findByPurchaseOrderId(poId)).thenReturn(List.of());

        var view = service.lookupPo("PO-1");
        assertThat(view.number()).isEqualTo("PO-1");
        assertThat(view.status()).isEqualTo("IN_TRANSIT");
    }

    @Test
    void lookupPoRejectsDraft() {
        when(purchaseOrderRepository.findByTenantIdAndNumberIgnoreCase(tenantId, "PO-D"))
                .thenReturn(Optional.of(po("DRAFT")));
        assertThatThrownBy(() -> service.lookupPo("PO-D"))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_STATE");
    }

    @Test
    void resolveItemMatchesBarcodeOnPo() {
        PurchaseOrder po = po("SUBMITTED");
        PurchaseOrderLine line = line();
        ProductVariant variant = variant("SKU-1", "UPC-1");
        when(purchaseOrderRepository.findByTenantIdAndId(tenantId, poId)).thenReturn(Optional.of(po));
        when(variantRepository.findByTenantIdAndBarcode(tenantId, "UPC-1")).thenReturn(Optional.of(variant));
        when(lineRepository.findByPurchaseOrderId(poId)).thenReturn(List.of(line));

        var match = service.resolveItem(poId, "UPC-1");
        assertThat(match.lineId()).isEqualTo(lineId);
        assertThat(match.qtyRemaining()).isEqualByComparingTo("7");
    }

    @Test
    void confirmPutawayWritesReceiptAndAnnouncesRtls() {
        PurchaseOrder po = po("IN_TRANSIT");
        PurchaseOrderLine line = line();
        Location bin = bin("B-01");
        var directive = new PutawayStrategyService.PutawayDirective(
                locationId, "/WH/B-01", "B-01", "EMPTY_BIN", "Put away", "A", "R", "B-01");
        InventoryLedger ledger = new InventoryLedger();
        ledger.setId(UUID.randomUUID());

        when(lineRepository.findById(lineId)).thenReturn(Optional.of(line));
        when(purchaseOrderRepository.findByTenantIdAndId(tenantId, poId)).thenReturn(Optional.of(po));
        when(putawayStrategyService.suggest(variantId)).thenReturn(directive);
        when(locationRepository.findByTenantIdAndCode(tenantId, "B-01")).thenReturn(Optional.of(bin));
        when(tenantSettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(uomConversionService.toStandardQuantity(eq(variantId), any(), eq("PURCHASING")))
                .thenAnswer(inv -> inv.getArgument(1));
        when(inventoryService.receive(eq(variantId), eq(locationId), isNull(), isNull(),
                any(), eq("PO_RECEIPT"), eq("PURCHASE_ORDER"), eq(poId), any(), isNull(), any()))
                .thenReturn(ledger);
        when(lineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rtlsTelemetryService.announceAssetAtLocation(poId, locationId)).thenReturn(Optional.empty());

        var result = service.confirmPutaway(new InboundReceivingService.ConfirmPutawayRequest(
                lineId, new BigDecimal("3"), null, "B-01", null, null));

        assertThat(result.action()).isEqualTo("PO_RECEIPT");
        assertThat(result.quantityChange()).isEqualByComparingTo("3");
        verify(purchaseOrderService).refreshStatusPublic(po);
        verify(inventoryService).receive(eq(variantId), eq(locationId), isNull(), isNull(),
                any(), eq("PO_RECEIPT"), eq("PURCHASE_ORDER"), eq(poId), any(), isNull(), any());
    }

    @Test
    void confirmRejectsWrongBinScan() {
        PurchaseOrder po = po("IN_TRANSIT");
        PurchaseOrderLine line = line();
        Location wrong = bin("WRONG");
        wrong.setId(UUID.randomUUID());
        var directive = new PutawayStrategyService.PutawayDirective(
                locationId, "/WH/B-01", "B-01", "EMPTY_BIN", "Put away", "A", "R", "B-01");

        when(lineRepository.findById(lineId)).thenReturn(Optional.of(line));
        when(purchaseOrderRepository.findByTenantIdAndId(tenantId, poId)).thenReturn(Optional.of(po));
        when(putawayStrategyService.suggest(variantId)).thenReturn(directive);
        when(locationRepository.findByTenantIdAndCode(tenantId, "WRONG")).thenReturn(Optional.of(wrong));

        assertThatThrownBy(() -> service.confirmPutaway(new InboundReceivingService.ConfirmPutawayRequest(
                        lineId, BigDecimal.ONE, null, "WRONG", null, null)))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("PUTAWAY_LOCATION_MISMATCH");
    }

    @Test
    void getPoAndSuggestPutaway() {
        when(purchaseOrderRepository.findByTenantIdAndId(tenantId, poId)).thenReturn(Optional.of(po("SUBMITTED")));
        when(lineRepository.findByPurchaseOrderId(poId)).thenReturn(List.of(line()));
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant("SKU-1", "UPC-1")));
        var directive = new PutawayStrategyService.PutawayDirective(
                locationId, "/WH/B-01", "B-01", "EMPTY_BIN", "Put away", "A", "R", "B-01");
        when(putawayStrategyService.suggest(variantId)).thenReturn(directive);

        assertThat(service.getPo(poId).lines()).hasSize(1);
        assertThat(service.suggestPutaway(variantId).code()).isEqualTo("B-01");
    }

    @Test
    void resolveItemBySkuAndRejectsFullyReceived() {
        PurchaseOrder po = po("SUBMITTED");
        PurchaseOrderLine full = line();
        full.setQtyReceived(new BigDecimal("10"));
        ProductVariant variant = variant("SKU-1", "UPC-1");
        when(purchaseOrderRepository.findByTenantIdAndId(tenantId, poId)).thenReturn(Optional.of(po));
        when(variantRepository.findByTenantIdAndBarcode(tenantId, "SKU-1")).thenReturn(Optional.empty());
        when(variantRepository.findByTenantIdAndSku(tenantId, "SKU-1")).thenReturn(Optional.of(variant));
        when(lineRepository.findByPurchaseOrderId(poId)).thenReturn(List.of(full));

        assertThatThrownBy(() -> service.resolveItem(poId, "SKU-1"))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("LINE_FULLY_RECEIVED");
    }

    @Test
    void confirmWithTagBindsRtls() {
        PurchaseOrder po = po("IN_TRANSIT");
        PurchaseOrderLine line = line();
        Location bin = bin("B-01");
        var directive = new PutawayStrategyService.PutawayDirective(
                locationId, "/WH/B-01", "B-01", "EMPTY_BIN", "Put away", "A", "R", "B-01");
        InventoryLedger ledger = new InventoryLedger();
        ledger.setId(UUID.randomUUID());

        when(lineRepository.findById(lineId)).thenReturn(Optional.of(line));
        when(purchaseOrderRepository.findByTenantIdAndId(tenantId, poId)).thenReturn(Optional.of(po));
        when(putawayStrategyService.suggest(variantId)).thenReturn(directive);
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(bin));
        when(tenantSettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(uomConversionService.toStandardQuantity(eq(variantId), any(), eq("PURCHASING")))
                .thenAnswer(inv -> inv.getArgument(1));
        when(inventoryService.receive(eq(variantId), eq(locationId), isNull(), isNull(),
                any(), eq("PO_RECEIPT"), eq("PURCHASE_ORDER"), eq(poId), any(), isNull(), any()))
                .thenReturn(ledger);
        when(lineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rtlsTelemetryService.announceAssetAtLocation(poId, locationId))
                .thenReturn(Optional.of(new RtlsTelemetryService.PositionFrame(
                        UUID.randomUUID(), "TAG-1", "BLE_AOA", BigDecimal.ONE, BigDecimal.ONE, null,
                        "PALLET", poId, null, java.time.Instant.now())));

        var result = service.confirmPutaway(new InboundReceivingService.ConfirmPutawayRequest(
                lineId, new BigDecimal("2"), locationId, null, null, "TAG-1"));

        assertThat(result.rtlsTriggered()).isTrue();
        verify(rtlsTelemetryService).bindPalletTag("TAG-1", poId, "PO PO-1");
    }

    @Test
    void lookupRequiresBarcode() {
        assertThatThrownBy(() -> service.lookupPo("  "))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("VALIDATION");
    }

    private PurchaseOrder po(String status) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(poId);
        po.setTenantId(tenantId);
        po.setNumber("PO-1");
        po.setStatus(status);
        return po;
    }

    private PurchaseOrderLine line() {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setId(lineId);
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(poId);
        line.setVariantId(variantId);
        line.setQtyOrdered(new BigDecimal("10"));
        line.setQtyReceived(new BigDecimal("3"));
        line.setUnitCost(new BigDecimal("1.00"));
        return line;
    }

    private ProductVariant variant(String sku, String barcode) {
        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setTenantId(tenantId);
        variant.setSku(sku);
        variant.setBarcode(barcode);
        return variant;
    }

    private Location bin(String code) {
        Location location = new Location();
        location.setId(locationId);
        location.setTenantId(tenantId);
        location.setCode(code);
        location.setPath("/WH/" + code);
        location.setType("BIN");
        return location;
    }
}
