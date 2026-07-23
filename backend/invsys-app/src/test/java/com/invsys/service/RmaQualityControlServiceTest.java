package com.invsys.service;

import com.invsys.domain.RmaQcInspection;
import com.invsys.domain.ReturnLine;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.repository.ReturnLineRepository;
import com.invsys.repository.RmaQcInspectionRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmaQualityControlServiceTest {

    private static final UUID TENANT = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID RETURN_LINE_ID = UUID.fromString("d0000000-0000-4000-8000-000000000020");
    private static final UUID SALES_ORDER_LINE_ID = UUID.fromString("d0000000-0000-4000-8000-000000000021");
    private static final UUID VARIANT_ID = UUID.fromString("d0000000-0000-4000-8000-000000000030");
    private static final UUID PICK_BIN_ID = UUID.fromString("d0000000-0000-4000-8000-000000000040");
    private static final UUID QUAR_ID = UUID.fromString("d0000000-0000-4000-8000-000000000041");

    @Mock RmaQcInspectionRepository inspectionRepository;
    @Mock ReturnLineRepository returnLineRepository;
    @Mock SalesOrderLineRepository salesOrderLineRepository;
    @Mock LocationRepository locationRepository;
    @Mock InventoryService inventoryService;

    RmaQualityControlService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        TenantContext.setUserId(UUID.randomUUID());
        service = new RmaQualityControlService(
                inspectionRepository, returnLineRepository, salesOrderLineRepository,
                locationRepository, inventoryService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void gradeANewRestockReceivesIntoSellableBin() {
        stubLineAndVariant();
        Location pickBin = location(PICK_BIN_ID, "BIN", "PICK_FACE");
        when(locationRepository.findByTenantIdAndType(TENANT, "BIN")).thenReturn(List.of(pickBin));
        when(inspectionRepository.save(any(RmaQcInspection.class))).thenAnswer(inv -> {
            RmaQcInspection saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.processInspection(request("GRADE_A_NEW", "RESTOCK", null));

        verify(inventoryService).receive(
                eq(VARIANT_ID), eq(PICK_BIN_ID), eq(null), eq(new BigDecimal("2")),
                eq("RMA_QC"), eq(RETURN_LINE_ID));
        verify(inventoryService, never()).quarantineReceive(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void gradeCDamagedQuarantinesStock() {
        stubLineAndVariant();
        when(locationRepository.findByTenantIdAndCode(TENANT, "QUARANTINE"))
                .thenReturn(Optional.of(location(QUAR_ID, "QUARANTINE", "STANDARD")));
        when(inspectionRepository.save(any(RmaQcInspection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processInspection(request("GRADE_C_DAMAGED", "SCRAP", null));

        verify(inventoryService).quarantineReceive(
                eq(VARIANT_ID), eq(QUAR_ID), eq(null), eq(new BigDecimal("2")),
                eq("RMA_QC"), eq(RETURN_LINE_ID), eq(SALES_ORDER_LINE_ID));
        verify(inventoryService, never()).receive(any(), any(), any(), any(), any(), any());
    }

    @Test
    void gradeBOpenBoxRecordsInspectionOnly() {
        stubLineAndVariant();
        when(inspectionRepository.save(any(RmaQcInspection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processInspection(request("GRADE_B_OPEN_BOX", "RESTOCK", null));

        verify(inventoryService, never()).receive(any(), any(), any(), any(), any(), any());
        verify(inventoryService, never()).quarantineReceive(
                any(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<ReturnLine> lineCaptor = ArgumentCaptor.forClass(ReturnLine.class);
        verify(returnLineRepository).save(lineCaptor.capture());
        assertThat(lineCaptor.getValue().getDisposition()).isEqualTo("QUARANTINE");
    }

    @Test
    void repairDispositionQuarantinesWithoutRestock() {
        stubLineAndVariant();
        when(locationRepository.findByTenantIdAndCode(TENANT, "QUARANTINE"))
                .thenReturn(Optional.of(location(QUAR_ID, "QUARANTINE", "STANDARD")));
        when(inspectionRepository.save(any(RmaQcInspection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processInspection(request("GRADE_A_NEW", "REPAIR", null));

        verify(inventoryService).quarantineReceive(
                eq(VARIANT_ID), eq(QUAR_ID), eq(null), eq(new BigDecimal("2")),
                eq("RMA_QC"), eq(RETURN_LINE_ID), eq(SALES_ORDER_LINE_ID));
    }

    private void stubLineAndVariant() {
        ReturnLine line = new ReturnLine();
        line.setId(RETURN_LINE_ID);
        line.setTenantId(TENANT);
        line.setReturnId(UUID.randomUUID());
        line.setSalesOrderLineId(SALES_ORDER_LINE_ID);
        line.setQuantityExpected(new BigDecimal("2"));
        line.setQuantityReceived(new BigDecimal("2"));
        when(returnLineRepository.findById(RETURN_LINE_ID)).thenReturn(Optional.of(line));

        SalesOrderLine sol = new SalesOrderLine();
        sol.setId(SALES_ORDER_LINE_ID);
        sol.setVariantId(VARIANT_ID);
        when(salesOrderLineRepository.findById(SALES_ORDER_LINE_ID)).thenReturn(Optional.of(sol));
    }

    private static RmaQualityControlService.InspectionRequest request(
            String grade, String disposition, UUID targetLocationId) {
        return new RmaQualityControlService.InspectionRequest(
                RETURN_LINE_ID, grade, disposition, "notes", List.of(), targetLocationId, null);
    }

    private static Location location(UUID id, String type, String zoneBehavior) {
        Location location = new Location();
        location.setId(id);
        location.setTenantId(TENANT);
        location.setType(type);
        location.setZoneBehavior(zoneBehavior);
        location.setCode(type + "-" + id.toString().substring(0, 4));
        location.setName(type);
        location.setPath("/" + type);
        return location;
    }
}
