package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.ReturnLine;
import com.invsys.domain.ReturnOrder;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.integration.easypost.EasyPostGateway;
import com.invsys.integration.easypost.EasyPostProperties;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.ReturnLineRepository;
import com.invsys.repository.ReturnOrderRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.service.InvoicingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.invsys.modules.inventory.service.InventoryService;

@Service
public class ReturnService {

    private static final List<String> FINAL_DISPOSITIONS = List.of("RESTOCK", "SCRAP", "REPAIR");
    private static final List<String> RECEIPT_DISPOSITIONS = List.of("QUARANTINE", "RESTOCK", "SCRAP", "REPAIR");
    private static final Set<String> REASON_CODES = Set.of(
            "DEFECTIVE_PRODUCT", "WRONG_ITEM_SHIPPED", "DAMAGED_IN_TRANSIT",
            "BUYER_REMORSE", "SIZE_FIT_EXCHANGE");
    private static final Set<String> RESOLUTION_TYPES = Set.of(
            "REFUND_CREDIT_MEMO", "REPLACEMENT_ORDER", "REPAIR");
    private static final Set<String> COMPLETABLE = Set.of("REQUESTED", "APPROVED", "EXPECTED", "RECEIVED");

    private final ReturnOrderRepository returnOrderRepository;
    private final ReturnLineRepository returnLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final InventoryService inventoryService;
    private final DocumentSequenceService sequenceService;
    private final LocationRepository locationRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final EasyPostGateway easyPostClient;
    private final EasyPostProperties easyPostProperties;
    private final CustomerRepository customerRepository;
    private final InvoicingService invoicingService;
    private final ReturnToVendorService returnToVendorService;

    public ReturnService(ReturnOrderRepository returnOrderRepository,
                         ReturnLineRepository returnLineRepository,
                         SalesOrderRepository salesOrderRepository,
                         SalesOrderLineRepository salesOrderLineRepository,
                         InventoryService inventoryService,
                         DocumentSequenceService sequenceService,
                         LocationRepository locationRepository,
                         InventoryLedgerRepository ledgerRepository,
                         EasyPostGateway easyPostClient,
                         EasyPostProperties easyPostProperties,
                         CustomerRepository customerRepository,
                         InvoicingService invoicingService,
                         ReturnToVendorService returnToVendorService) {
        this.returnOrderRepository = returnOrderRepository;
        this.returnLineRepository = returnLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.inventoryService = inventoryService;
        this.sequenceService = sequenceService;
        this.locationRepository = locationRepository;
        this.ledgerRepository = ledgerRepository;
        this.easyPostClient = easyPostClient;
        this.easyPostProperties = easyPostProperties;
        this.customerRepository = customerRepository;
        this.invoicingService = invoicingService;
        this.returnToVendorService = returnToVendorService;
    }

    @Transactional
    public ReturnOrder create(UUID salesOrderId, List<ReturnLineInput> lines) {
        return create(salesOrderId, lines, null, false);
    }

    @Transactional
    public ReturnOrder create(UUID salesOrderId, List<ReturnLineInput> lines,
                              String resolutionType, boolean generateLabel) {
        UUID tenantId = TenantContext.requireTenantId();
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
        if (lines == null || lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "At least one return line is required");
        }

        ReturnOrder returnOrder = new ReturnOrder();
        returnOrder.setTenantId(tenantId);
        returnOrder.setSalesOrderId(order.getId());
        returnOrder.setNumber(sequenceService.nextNumber("RMA", "RMA-{YYYY}-{seq:5}"));
        returnOrder.setStatus("REQUESTED");
        if (resolutionType != null && !resolutionType.isBlank()) {
            String resolution = resolutionType.trim().toUpperCase();
            if (!RESOLUTION_TYPES.contains(resolution)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESOLUTION",
                        "resolutionType must be REFUND_CREDIT_MEMO, REPLACEMENT_ORDER, or REPAIR");
            }
            returnOrder.setResolutionType(resolution);
        }
        returnOrder = returnOrderRepository.save(returnOrder);

        String headerReason = null;
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
            String reason = normalizeReason(input.reasonCode());
            line.setReasonCode(reason);
            if (headerReason == null && reason != null) {
                headerReason = reason;
            }
            returnLineRepository.save(line);
        }
        if (headerReason != null) {
            returnOrder.setReasonCode(headerReason);
        }

        if (generateLabel) {
            applyPurchasedLabel(returnOrder, order);
            returnOrder.setStatus("APPROVED");
        }
        return returnOrderRepository.save(returnOrder);
    }

    void validateReturnQuantityPublic(SalesOrderLine sol, BigDecimal quantityExpected) {
        validateReturnQuantity(sol, quantityExpected);
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
        // PENDING_REVIEW must use review/approve-with-label or approve-without-label
        if (!"REQUESTED".equals(returnOrder.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Return is not awaiting approval — use review endpoints for PENDING_REVIEW");
        }
        returnOrder.setStatus("APPROVED");
        return returnOrderRepository.save(returnOrder);
    }

    @Transactional
    public ReturnOrder approveWithLabel(UUID returnId) {
        ReturnOrder returnOrder = getReturn(returnId);
        if (!"PENDING_REVIEW".equals(returnOrder.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only PENDING_REVIEW RMAs can be approved with a purchased label");
        }
        SalesOrder order = salesOrderRepository.findById(returnOrder.getSalesOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
        EasyPostGateway.ParcelSpec parcel = buildParcel(order, new BigDecimal("2.0"));
        applyPurchasedLabel(returnOrder, order);
        returnOrder.setStatus("APPROVED");
        return returnOrderRepository.save(returnOrder);
    }

    @Transactional
    public ReturnOrder approveWithoutLabel(UUID returnId) {
        ReturnOrder returnOrder = getReturn(returnId);
        if (!"PENDING_REVIEW".equals(returnOrder.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only PENDING_REVIEW RMAs can be approved without a label");
        }
        returnOrder.setLabelPurchaseMode("CUSTOMER");
        returnOrder.setStatus("APPROVED");
        return returnOrderRepository.save(returnOrder);
    }

    @Transactional
    public ReturnOrder denyAndClose(UUID returnId) {
        ReturnOrder returnOrder = getReturn(returnId);
        if (!List.of("PENDING_REVIEW", "REQUESTED").contains(returnOrder.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Return cannot be denied in its current status");
        }
        returnOrder.setLabelPurchaseMode("NONE");
        returnOrder.setStatus("REJECTED");
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
        return setDisposition(returnLineId, disposition, null, null);
    }

    @Transactional
    public ReturnLine setDisposition(UUID returnLineId, String disposition,
                                     UUID restockLocationId, BigDecimal restockingFeePct) {
        if (!RECEIPT_DISPOSITIONS.contains(disposition)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISPOSITION", "Invalid disposition");
        }
        ReturnLine line = returnLineRepository.findById(returnLineId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Return line not found"));
        line.setDisposition(disposition);
        if (restockLocationId != null) {
            line.setRestockLocationId(restockLocationId);
        }
        if (restockingFeePct != null) {
            if (restockingFeePct.signum() < 0 || restockingFeePct.compareTo(new BigDecimal("100")) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEE",
                        "Restocking fee must be between 0 and 100");
            }
            line.setRestockingFeePct(restockingFeePct);
        }
        if ("RESTOCK".equals(disposition) && line.getRestockLocationId() == null && restockLocationId == null) {
            // keep existing bin; completeDisposition will require one
        }
        return returnLineRepository.save(line);
    }

    @Transactional
    public ReturnOrder completeDisposition(UUID returnId) {
        ReturnOrder returnOrder = getReturn(returnId);
        if (!COMPLETABLE.contains(returnOrder.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Return cannot be completed in its current status");
        }
        List<ReturnLine> lines = returnLineRepository.findByReturnId(returnOrder.getId());
        if (lines.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_LINES", "Return has no lines");
        }
        for (ReturnLine line : lines) {
            applyCompleteInventory(line);
            returnLineRepository.save(line);
        }
        if (!"REPAIR".equals(returnOrder.getResolutionType()) && returnOrder.getCreditMemoId() == null) {
            Invoice credit = invoicingService.createDraftCreditMemoForReturn(
                    returnOrder.getSalesOrderId(),
                    lines.stream()
                            .map(line -> new InvoicingService.ReturnCreditLine(
                                    line.getSalesOrderLineId(),
                                    line.getQuantityExpected(),
                                    line.getRestockingFeePct()))
                            .toList());
            returnOrder.setCreditMemoId(credit.getId());
        }
        returnOrder.setStatus("CLOSED");
        return returnOrderRepository.save(returnOrder);
    }

    @Transactional
    public ReturnToVendorService.RtvDetail escalateToRtv(UUID returnId) {
        ReturnOrder returnOrder = getReturn(returnId);
        if (returnOrder.getRtvOrderId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ESCALATED",
                    "Return already has a draft RTV");
        }
        ReturnToVendorService.RtvDetail detail = returnToVendorService.createDraftFromCustomerReturn(returnId);
        returnOrder.setRtvOrderId(detail.order().getId());
        returnOrderRepository.save(returnOrder);
        return detail;
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

    private void applyCompleteInventory(ReturnLine line) {
        String disposition = line.getDisposition();
        if (disposition == null || disposition.isBlank() || "QUARANTINE".equals(disposition)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DISPOSITION_REQUIRED",
                    "Set RESTOCK, SCRAP, or REPAIR on every line before completing");
        }
        SalesOrderLine sol = salesOrderLineRepository.findById(line.getSalesOrderLineId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order line not found"));
        BigDecimal qty = line.getQuantityExpected();
        boolean alreadyReceived = line.getQuantityReceived() != null && line.getQuantityReceived().signum() > 0;

        if ("RESTOCK".equals(disposition)) {
            if (line.getRestockLocationId() == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RESTOCK_BIN_REQUIRED",
                        "Choose a restock target bin for every RESTOCK line");
            }
            if (alreadyReceived) {
                UUID holdLocation = ledgerRepository
                        .findByTenantIdAndReferenceTypeAndReferenceId(
                                TenantContext.requireTenantId(), "RETURN", line.getId())
                        .stream()
                        .filter(l -> "RMA_QUARANTINE".equals(l.getReasonCode()))
                        .max(Comparator.comparing(InventoryLedger::getCreatedAt))
                        .map(InventoryLedger::getLocationId)
                        .orElse(null);
                if (holdLocation != null) {
                    inventoryService.releaseQuarantineHold(
                            sol.getId(), sol.getVariantId(), holdLocation, null,
                            line.getQuantityReceived(), "RESTOCK");
                }
            } else {
                inventoryService.adjust(sol.getVariantId(), line.getRestockLocationId(), null, qty, "RMA_RESTOCK");
                line.setQuantityReceived(qty);
            }
            return;
        }
        if ("SCRAP".equals(disposition)) {
            if (alreadyReceived) {
                List<InventoryLedger> quarantineMoves = ledgerRepository
                        .findByTenantIdAndReferenceTypeAndReferenceId(
                                TenantContext.requireTenantId(), "RETURN", line.getId())
                        .stream()
                        .filter(l -> "RMA_QUARANTINE".equals(l.getReasonCode()))
                        .toList();
                if (!quarantineMoves.isEmpty()) {
                    UUID locationId = quarantineMoves.stream()
                            .max(Comparator.comparing(InventoryLedger::getCreatedAt))
                            .map(InventoryLedger::getLocationId)
                            .orElseGet(() -> resolveLocationId(null));
                    inventoryService.releaseQuarantineHold(
                            sol.getId(), sol.getVariantId(), locationId, null,
                            line.getQuantityReceived(), "SCRAP");
                } else {
                    inventoryService.adjust(sol.getVariantId(), resolveLocationId(null), null,
                            line.getQuantityReceived().negate(), "RMA_SCRAP");
                }
            } else {
                line.setQuantityReceived(qty);
            }
            return;
        }
        if ("REPAIR".equals(disposition) && !alreadyReceived) {
            line.setQuantityReceived(qty);
        }
    }

    private void applyPurchasedLabel(ReturnOrder returnOrder, SalesOrder order) {
        EasyPostGateway.ParcelSpec parcel = buildParcel(order, new BigDecimal("2.0"));
        var label = easyPostClient.purchaseReturnLabel(parcel, returnOrder.getNumber());
        returnOrder.setReturnLabelUrl(label.labelRef());
        returnOrder.setEstimatedLabelCost(label.postageAmount());
        returnOrder.setLabelPurchaseMode("SYSTEM");
        returnOrder.setTrackingNumber(label.trackingNumber());
    }

    private String normalizeReason(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return null;
        }
        String normalized = reasonCode.trim().toUpperCase();
        if (!REASON_CODES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REASON",
                    "reasonCode must be one of: " + String.join(", ", REASON_CODES));
        }
        return normalized;
    }

    private EasyPostGateway.ParcelSpec buildParcel(SalesOrder order, BigDecimal weightLb) {
        Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        EasyPostGateway.AddressSpec to = customer != null
                ? EasyPostGateway.AddressSpec.fromMap(customer.getShippingAddress(), customer.getName())
                : null;
        EasyPostGateway.AddressSpec from = easyPostProperties.defaultFromAddress();
        return new EasyPostGateway.ParcelSpec(
                new BigDecimal("12"), new BigDecimal("10"), new BigDecimal("8"),
                weightLb, to, from, false);
    }

    public record ReturnLineInput(UUID salesOrderLineId, BigDecimal quantityExpected, String reasonCode) {
        public ReturnLineInput(UUID salesOrderLineId, BigDecimal quantityExpected) {
            this(salesOrderLineId, quantityExpected, null);
        }
    }
}
