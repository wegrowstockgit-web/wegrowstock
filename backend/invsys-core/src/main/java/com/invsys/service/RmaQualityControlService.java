package com.invsys.service;

import com.invsys.core.common.ApiException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class RmaQualityControlService {

    private static final List<String> GRADES = List.of("GRADE_A_NEW", "GRADE_B_OPEN_BOX", "GRADE_C_DAMAGED");
    private static final List<String> DISPOSITIONS = List.of("RESTOCK", "SCRAP", "REPAIR", "REFURBISH");

    private final RmaQcInspectionRepository inspectionRepository;
    private final ReturnLineRepository returnLineRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final LocationRepository locationRepository;
    private final InventoryService inventoryService;

    public RmaQualityControlService(RmaQcInspectionRepository inspectionRepository,
                                    ReturnLineRepository returnLineRepository,
                                    SalesOrderLineRepository salesOrderLineRepository,
                                    LocationRepository locationRepository,
                                    InventoryService inventoryService) {
        this.inspectionRepository = inspectionRepository;
        this.returnLineRepository = returnLineRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.locationRepository = locationRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public RmaQcInspection processInspection(InspectionRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        validateRequest(request);

        ReturnLine line = returnLineRepository.findById(request.returnLineId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Return line not found"));

        SalesOrderLine sol = salesOrderLineRepository.findById(line.getSalesOrderLineId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order line not found"));

        BigDecimal qty = resolveQuantity(request.quantity(), line);

        RmaQcInspection inspection = new RmaQcInspection();
        inspection.setTenantId(tenantId);
        inspection.setReturnLineId(line.getId());
        inspection.setInspectorUserId(TenantContext.getUserId().orElse(null));
        inspection.setGrade(request.grade());
        inspection.setInspectionNotes(request.inspectionNotes());
        inspection.setPhotoAttachmentIds(request.photoAttachmentIds());
        inspection.setDispositionAction(request.dispositionAction());
        inspection = inspectionRepository.save(inspection);

        applyDisposition(line, sol, qty, request);
        line.setDisposition(mapLineDisposition(request.grade(), request.dispositionAction()));
        returnLineRepository.save(line);

        return inspection;
    }

    private void applyDisposition(ReturnLine line, SalesOrderLine sol, BigDecimal qty, InspectionRequest request) {
        UUID variantId = sol.getVariantId();
        String grade = request.grade();
        String disposition = request.dispositionAction();

        if ("GRADE_A_NEW".equals(grade) && "RESTOCK".equals(disposition)) {
            UUID sellableLocation = resolveSellableLocation(request.targetLocationId());
            inventoryService.receive(
                    variantId, sellableLocation, null, qty, "RMA_QC", line.getId());
            return;
        }

        if ("GRADE_C_DAMAGED".equals(grade) || "SCRAP".equals(disposition)) {
            UUID quarantineLocation = resolveQuarantineLocationId();
            inventoryService.quarantineReceive(
                    variantId, quarantineLocation, null, qty, "RMA_QC", line.getId(), sol.getId());
            return;
        }

        if ("REPAIR".equals(disposition) || "REFURBISH".equals(disposition)) {
            UUID quarantineLocation = resolveQuarantineLocationId();
            inventoryService.quarantineReceive(
                    variantId, quarantineLocation, null, qty, "RMA_QC", line.getId(), sol.getId());
            return;
        }

        if ("GRADE_B_OPEN_BOX".equals(grade)) {
            return;
        }
    }

    private String mapLineDisposition(String grade, String disposition) {
        if ("GRADE_A_NEW".equals(grade) && "RESTOCK".equals(disposition)) {
            return "RESTOCK";
        }
        if ("SCRAP".equals(disposition) || "GRADE_C_DAMAGED".equals(grade)) {
            return "SCRAP";
        }
        if ("REPAIR".equals(disposition)) {
            return "REPAIR";
        }
        if ("REFURBISH".equals(disposition)) {
            return "REFURBISH";
        }
        return "QUARANTINE";
    }

    private void validateRequest(InspectionRequest request) {
        if (!GRADES.contains(request.grade())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRADE", "Invalid QC grade");
        }
        if (!DISPOSITIONS.contains(request.dispositionAction())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISPOSITION", "Invalid disposition action");
        }
    }

    private BigDecimal resolveQuantity(BigDecimal requested, ReturnLine line) {
        if (requested != null && requested.signum() > 0) {
            return requested;
        }
        if (line.getQuantityReceived().signum() > 0) {
            return line.getQuantityReceived();
        }
        return line.getQuantityExpected();
    }

    private UUID resolveSellableLocation(UUID targetLocationId) {
        if (targetLocationId != null) {
            Location location = locationRepository.findById(targetLocationId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Location not found"));
            return location.getId();
        }
        UUID tenantId = TenantContext.requireTenantId();
        List<Location> pickFaceBins = locationRepository.findByTenantIdAndType(tenantId, "BIN").stream()
                .filter(l -> "PICK_FACE".equals(l.getZoneBehavior()))
                .toList();
        if (!pickFaceBins.isEmpty()) {
            return pickFaceBins.getFirst().getId();
        }
        List<Location> bins = locationRepository.findByTenantIdAndType(tenantId, "BIN");
        if (!bins.isEmpty()) {
            return bins.getFirst().getId();
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SELLABLE_LOCATION_REQUIRED",
                "A BIN location (preferably PICK_FACE) must exist before restocking inspected returns");
    }

    private UUID resolveQuarantineLocationId() {
        UUID tenantId = TenantContext.requireTenantId();
        return locationRepository.findByTenantIdAndCode(tenantId, "QUARANTINE")
                .map(Location::getId)
                .orElseGet(() -> {
                    List<Location> quarantine = locationRepository.findByTenantIdAndType(tenantId, "QUARANTINE");
                    if (!quarantine.isEmpty()) {
                        return quarantine.getFirst().getId();
                    }
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QUARANTINE_LOCATION_REQUIRED",
                            "A QUARANTINE location must exist before QC quarantine routing");
                });
    }

    public record InspectionRequest(
            UUID returnLineId,
            String grade,
            String dispositionAction,
            String inspectionNotes,
            List<UUID> photoAttachmentIds,
            UUID targetLocationId,
            BigDecimal quantity
    ) {
    }
}
