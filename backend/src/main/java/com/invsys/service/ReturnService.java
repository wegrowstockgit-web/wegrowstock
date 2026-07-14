package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.ReturnLine;
import com.invsys.domain.ReturnOrder;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ReturnLineRepository;
import com.invsys.repository.ReturnOrderRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ReturnService {

    private static final List<String> FINAL_DISPOSITIONS = List.of("RESTOCK", "SCRAP", "REPAIR");
    private static final List<String> RECEIPT_DISPOSITIONS = List.of("QUARANTINE", "RESTOCK", "SCRAP", "REPAIR");

    private final ReturnOrderRepository returnOrderRepository;
    private final ReturnLineRepository returnLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final InventoryService inventoryService;
    private final DocumentSequenceService sequenceService;
    private final LocationRepository locationRepository;
    private final InventoryLedgerRepository ledgerRepository;

    public ReturnService(ReturnOrderRepository returnOrderRepository,
                         ReturnLineRepository returnLineRepository,
                         SalesOrderRepository salesOrderRepository,
                         SalesOrderLineRepository salesOrderLineRepository,
                         InventoryService inventoryService,
                         DocumentSequenceService sequenceService,
                         LocationRepository locationRepository,
                         InventoryLedgerRepository ledgerRepository) {
        this.returnOrderRepository = returnOrderRepository;
        this.returnLineRepository = returnLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.inventoryService = inventoryService;
        this.sequenceService = sequenceService;
        this.locationRepository = locationRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public ReturnOrder create(UUID salesOrderId, List<ReturnLineInput> lines) {
        UUID tenantId = TenantContext.requireTenantId();
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));

        ReturnOrder returnOrder = new ReturnOrder();
        returnOrder.setTenantId(tenantId);
        returnOrder.setSalesOrderId(order.getId());
        returnOrder.setNumber(sequenceService.nextNumber("RMA", "RMA-{YYYY}-{seq:5}"));
        returnOrder.setStatus("REQUESTED");
        returnOrder = returnOrderRepository.save(returnOrder);

        for (ReturnLineInput input : lines) {
            SalesOrderLine sol = salesOrderLineRepository.findById(input.salesOrderLineId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order line not found"));
            validateReturnQuantity(sol, input.quantityExpected());

            ReturnLine line = new ReturnLine();
            line.setTenantId(tenantId);
            line.setReturnId(returnOrder.getId());
            line.setSalesOrderLineId(sol.getId());
            line.setQuantityExpected(input.quantityExpected());
            line.setDisposition("QUARANTINE");
            returnLineRepository.save(line);
        }
        return returnOrder;
    }

    private void validateReturnQuantity(SalesOrderLine sol, BigDecimal quantityExpected) {
        BigDecimal alreadyReturned = returnLineRepository.sumExpectedForLine(sol.getId());
        BigDecimal maxReturnable = sol.getQtyShipped().subtract(alreadyReturned);
        if (quantityExpected.compareTo(maxReturnable) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXCESS_RETURN",
                    "Return quantity exceeds shipped minus already returned");
        }
    }

    @Transactional
    public ReturnOrder approve(UUID returnId) {
        ReturnOrder returnOrder = getReturn(returnId);
        if (!"REQUESTED".equals(returnOrder.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Return is not in REQUESTED status");
        }
        returnOrder.setStatus("APPROVED");
        return returnOrderRepository.save(returnOrder);
    }

    @Transactional
    public ReturnLine processReceipt(UUID returnLineId, UUID locationId, String disposition) {
        ReturnLine line = returnLineRepository.findById(returnLineId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Return line not found"));
        ReturnOrder returnOrder = getReturn(line.getReturnId());
        if (!List.of("APPROVED", "RECEIVED").contains(returnOrder.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Return is not approved");
        }
        if (!RECEIPT_DISPOSITIONS.contains(disposition)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISPOSITION", "Invalid disposition");
        }

        BigDecimal remaining = line.getQuantityExpected().subtract(line.getQuantityReceived());
        if (remaining.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RECEIVED", "Line already fully received");
        }

        UUID destination = locationId;
        if ("QUARANTINE".equals(disposition)) {
            destination = enforceQuarantineDestination(locationId);
        }

        line.setDisposition(disposition);
        line.setQuantityReceived(line.getQuantityExpected());
        applyReceiptMovement(line, destination, remaining, disposition);
        returnLineRepository.save(line);
        updateReturnStatus(returnOrder);
        return line;
    }

    private void updateReturnStatus(ReturnOrder returnOrder) {
        List<ReturnLine> lines = returnLineRepository.findByReturnId(returnOrder.getId());
        boolean allReceived = lines.stream()
                .allMatch(l -> l.getQuantityReceived().compareTo(l.getQuantityExpected()) >= 0);
        if (allReceived) {
            returnOrder.setStatus("RECEIVED");
            returnOrderRepository.save(returnOrder);
        } else if ("APPROVED".equals(returnOrder.getStatus())) {
            returnOrder.setStatus("RECEIVED");
            returnOrderRepository.save(returnOrder);
        }
    }

    @Transactional
    public ReturnLine setDisposition(UUID returnLineId, String disposition) {
        if (!RECEIPT_DISPOSITIONS.contains(disposition)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISPOSITION", "Invalid disposition");
        }
        ReturnLine line = returnLineRepository.findById(returnLineId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Return line not found"));
        line.setDisposition(disposition);
        return returnLineRepository.save(line);
    }

    @Transactional
    public ReturnLine receiveIncrement(UUID returnLineId, BigDecimal quantity, UUID locationId) {
        ReturnLine line = returnLineRepository.findById(returnLineId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Return line not found"));
        ReturnOrder returnOrder = getReturn(line.getReturnId());
        if (!List.of("APPROVED", "RECEIVED").contains(returnOrder.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Return is not approved");
        }

        BigDecimal remaining = line.getQuantityExpected().subtract(line.getQuantityReceived());
        BigDecimal toReceive = quantity != null ? quantity : BigDecimal.ONE;
        if (toReceive.compareTo(remaining) > 0) {
            toReceive = remaining;
        }
        if (toReceive.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RECEIVED", "Line already fully received");
        }

        String disposition = line.getDisposition();
        if (disposition == null || disposition.isBlank()) {
            disposition = "QUARANTINE";
            line.setDisposition(disposition);
        }

        UUID resolvedLocation = resolveLocationId(locationId);
        applyReceiptMovement(line, resolvedLocation, toReceive, disposition);

        line.setQuantityReceived(line.getQuantityReceived().add(toReceive));
        returnLineRepository.save(line);
        updateReturnStatus(returnOrder);
        return line;
    }

    @Transactional
    public ReturnLine releaseFromQuarantine(UUID returnLineId, String disposition) {
        if (!FINAL_DISPOSITIONS.contains(disposition)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISPOSITION",
                    "Release disposition must be RESTOCK, SCRAP, or REPAIR");
        }
        ReturnLine line = returnLineRepository.findById(returnLineId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Return line not found"));
        if (!"QUARANTINE".equals(line.getDisposition())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Return line is not in QUARANTINE disposition");
        }
        if (line.getQuantityReceived().signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_RECEIVED",
                    "Return line has no quarantined quantity to release");
        }

        SalesOrderLine sol = salesOrderLineRepository.findById(line.getSalesOrderLineId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order line not found"));

        UUID locationId = ledgerRepository
                .findByTenantIdAndReferenceTypeAndReferenceId(
                        TenantContext.requireTenantId(), "RETURN", line.getId())
                .stream()
                .filter(l -> "RMA_QUARANTINE".equals(l.getReasonCode()))
                .max(Comparator.comparing(InventoryLedger::getCreatedAt))
                .map(InventoryLedger::getLocationId)
                .orElseGet(() -> resolveLocationId(null));

        if ("REPAIR".equals(disposition)) {
            line.setDisposition("REPAIR");
            return returnLineRepository.save(line);
        }

        inventoryService.releaseQuarantineHold(
                sol.getId(), sol.getVariantId(), locationId, null,
                line.getQuantityReceived(), disposition);
        line.setDisposition(disposition);
        return returnLineRepository.save(line);
    }

    private void applyReceiptMovement(ReturnLine line, UUID locationId, BigDecimal qty, String disposition) {
        SalesOrderLine sol = salesOrderLineRepository.findById(line.getSalesOrderLineId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order line not found"));
        UUID variantId = sol.getVariantId();

        switch (disposition) {
            case "QUARANTINE" -> {
                UUID quarantineLocationId = locationId != null ? locationId : resolveQuarantineLocationId();
                inventoryService.quarantineReceive(
                        variantId, quarantineLocationId, null, qty, "RETURN", line.getId(), sol.getId());
            }
            case "RESTOCK", "REPAIR" -> {
                UUID quarantineLocationId = resolveQuarantineLocationId();
                inventoryService.quarantineReceive(
                        variantId, quarantineLocationId, null, qty, "RETURN", line.getId(), sol.getId());
            }
            case "SCRAP" -> inventoryService.adjust(variantId, locationId, null, qty.negate(), "RMA_SCRAP");
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISPOSITION", "Invalid disposition");
        }
    }

    public ReturnOrder findByBarcode(String barcode) {
        UUID tenantId = TenantContext.requireTenantId();
        return returnOrderRepository.findByTenantIdAndNumber(tenantId, barcode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "RMA not found"));
    }

    private UUID resolveQuarantineLocationId() {
        UUID tenantId = TenantContext.requireTenantId();
        List<Location> quarantine = locationRepository.findByTenantIdAndType(tenantId, "QUARANTINE");
        if (!quarantine.isEmpty()) {
            return quarantine.getFirst().getId();
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QUARANTINE_LOCATION_REQUIRED",
                "A QUARANTINE location must exist before receiving inspection returns");
    }

    /**
     * Inspection receipts must land in a quarantine zone — never a pickable bin.
     */
    private UUID enforceQuarantineDestination(UUID locationId) {
        if (locationId == null) {
            return resolveQuarantineLocationId();
        }
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Location not found"));
        if (!"QUARANTINE".equals(location.getType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QUARANTINE_LOCATION_REQUIRED",
                    "Inspection returns must be routed to a QUARANTINE location");
        }
        return location.getId();
    }

    private UUID resolveLocationId(UUID locationId) {
        if (locationId != null) {
            return locationId;
        }
        List<Location> warehouses = locationRepository.findByTenantIdAndType(
                TenantContext.requireTenantId(), "WAREHOUSE");
        if (!warehouses.isEmpty()) {
            return warehouses.get(0).getId();
        }
        return locationRepository.findByTenantIdOrderByPathAsc(TenantContext.requireTenantId()).stream()
                .findFirst()
                .map(Location::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_LOCATION", "No warehouse configured"));
    }

    private ReturnOrder getReturn(UUID returnId) {
        return returnOrderRepository.findById(returnId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Return not found"));
    }

    public record ReturnLineInput(UUID salesOrderLineId, BigDecimal quantityExpected) {
    }
}
