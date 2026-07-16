package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.TenantSettings;
import com.invsys.integration.OutboxService;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final InventoryService inventoryService;
    private final UomConversionService uomConversionService;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final OutboxService outboxService;
    private final CrossDockService crossDockService;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                PurchaseOrderLineRepository lineRepository,
                                InventoryService inventoryService,
                                UomConversionService uomConversionService,
                                TenantSettingsRepository tenantSettingsRepository,
                                OutboxService outboxService,
                                CrossDockService crossDockService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.inventoryService = inventoryService;
        this.uomConversionService = uomConversionService;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.outboxService = outboxService;
        this.crossDockService = crossDockService;
    }

    @Transactional
    public PurchaseOrder submit(UUID purchaseOrderId) {
        PurchaseOrder po = requirePo(purchaseOrderId);
        if (!"DRAFT".equals(po.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only DRAFT purchase orders can be submitted");
        }
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purchaseOrderId", po.getId());
        payload.put("supplierId", po.getSupplierId());
        payload.put("number", po.getNumber());
        outboxService.append("PURCHASE_ORDER", po.getId(), "PURCHASE_ORDER_SUBMITTED", payload);
        return po;
    }

    @Transactional
    public PurchaseOrder markInTransit(UUID purchaseOrderId) {
        PurchaseOrder po = requirePo(purchaseOrderId);
        if (!"SUBMITTED".equals(po.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only SUBMITTED purchase orders can be marked in transit");
        }
        po.setStatus("IN_TRANSIT");
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrderLine receiveLine(UUID lineId, UUID locationId, UUID lotId, BigDecimal quantity) {
        return receiveLine(lineId, locationId, lotId, quantity, null);
    }

    @Transactional
    public PurchaseOrderLine receiveLine(UUID lineId, UUID locationId, UUID lotId, BigDecimal quantity,
                                         BigDecimal landedCostSurcharge) {
        PurchaseOrderLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "PO line not found"));
        PurchaseOrder po = requirePo(line.getPurchaseOrderId());
        if (!List.of("SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED").contains(po.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Receiving requires SUBMITTED, IN_TRANSIT, or PARTIALLY_RECEIVED status");
        }
        BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyReceived());
        BigDecimal tolerancePercent = overReceiptTolerancePercent();
        BigDecimal maxAllowed = remaining.multiply(
                BigDecimal.ONE.add(tolerancePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
        if (quantity.compareTo(maxAllowed) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "OVER_RECEIPT_TOLERANCE",
                    "Quantity exceeds over-receipt tolerance");
        }
        BigDecimal standardQty = uomConversionService.toStandardQuantity(
                line.getVariantId(), quantity, "PURCHASING");
        BigDecimal unitCost = applySurchargeToUnitCost(line.getUnitCost(), quantity, landedCostSurcharge);

        // Cross-dock intercept: open SO demand → receive into shipping staging, skip reserve put-away.
        UUID receiveLocationId = locationId;
        String reasonCode = "PO_RECEIVE";
        var openDemand = crossDockService.previewOpenDemand(line.getVariantId());
        if (openDemand.isPresent()) {
            receiveLocationId = openDemand.get().stagingLocationId();
            reasonCode = CrossDockService.REASON_CROSS_DOCK_ROUTING;
        }

        inventoryService.receive(
                line.getVariantId(),
                receiveLocationId,
                lotId,
                null,
                standardQty,
                reasonCode,
                "PURCHASE_ORDER_LINE",
                line.getId(),
                unitCost,
                null,
                null);
        line.setQtyReceived(line.getQtyReceived().add(quantity));
        lineRepository.save(line);
        refreshPoStatus(po);

        if (openDemand.isPresent()) {
            crossDockService.fulfillOpenDemand(line.getVariantId(), receiveLocationId, standardQty);
        }
        return line;
    }

    /**
     * Multi-line receive with optional freight/customs surcharge distributed by line value
     * (qty × unit cost), falling back to quantity when all line values are zero.
     */
    @Transactional
    public List<PurchaseOrderLine> receiveWithLandedCost(UUID purchaseOrderId,
                                                         UUID locationId,
                                                         BigDecimal landedCostSurcharge,
                                                         List<ReceiveLineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "At least one receive line is required");
        }
        PurchaseOrder po = requirePo(purchaseOrderId);
        if (!List.of("SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED").contains(po.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Receiving requires SUBMITTED, IN_TRANSIT, or PARTIALLY_RECEIVED status");
        }

        List<PreparedReceive> prepared = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        boolean useValue = false;
        for (ReceiveLineInput input : lines) {
            PurchaseOrderLine line = lineRepository.findById(input.lineId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "PO line not found"));
            if (!po.getId().equals(line.getPurchaseOrderId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Line does not belong to purchase order");
            }
            BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyReceived());
            BigDecimal tolerancePercent = overReceiptTolerancePercent();
            BigDecimal maxAllowed = remaining.multiply(
                    BigDecimal.ONE.add(tolerancePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
            if (input.quantity().compareTo(maxAllowed) > 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "OVER_RECEIPT_TOLERANCE",
                        "Quantity exceeds over-receipt tolerance");
            }
            BigDecimal lineValue = line.getUnitCost().multiply(input.quantity());
            if (lineValue.signum() > 0) {
                useValue = true;
            }
            prepared.add(new PreparedReceive(line, input.quantity(), input.lotId(), lineValue));
        }
        for (PreparedReceive row : prepared) {
            BigDecimal weight = useValue ? row.lineValue() : row.quantity();
            totalWeight = totalWeight.add(weight);
        }
        if (totalWeight.signum() <= 0) {
            totalWeight = BigDecimal.valueOf(prepared.size());
        }

        BigDecimal surcharge = landedCostSurcharge != null ? landedCostSurcharge : BigDecimal.ZERO;
        List<PurchaseOrderLine> updated = new ArrayList<>();
        for (PreparedReceive row : prepared) {
            BigDecimal weight = useValue ? row.lineValue() : row.quantity();
            if (weight.signum() <= 0) {
                weight = BigDecimal.ONE;
            }
            BigDecimal share = surcharge.signum() > 0
                    ? surcharge.multiply(weight).divide(totalWeight, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal unitCost = applySurchargeToUnitCost(row.line().getUnitCost(), row.quantity(), share);
            BigDecimal standardQty = uomConversionService.toStandardQuantity(
                    row.line().getVariantId(), row.quantity(), "PURCHASING");
            inventoryService.receive(row.line().getVariantId(), locationId, row.lotId(), standardQty,
                    "PURCHASE_ORDER_LINE", row.line().getId(), unitCost);
            row.line().setQtyReceived(row.line().getQtyReceived().add(row.quantity()));
            updated.add(lineRepository.save(row.line()));
        }
        refreshPoStatus(po);
        return updated;
    }

    private void refreshPoStatus(PurchaseOrder po) {
        boolean fullyReceived = lineRepository.findByPurchaseOrderId(po.getId()).stream()
                .allMatch(l -> l.getQtyReceived().compareTo(l.getQtyOrdered()) >= 0);
        boolean anyReceived = lineRepository.findByPurchaseOrderId(po.getId()).stream()
                .anyMatch(l -> l.getQtyReceived().signum() > 0);
        if (fullyReceived) {
            po.setStatus("RECEIVED");
        } else if (anyReceived) {
            po.setStatus("PARTIALLY_RECEIVED");
        }
        purchaseOrderRepository.save(po);
    }

    static BigDecimal applySurchargeToUnitCost(BigDecimal baseUnitCost, BigDecimal quantity, BigDecimal surcharge) {
        if (surcharge == null || surcharge.signum() <= 0 || quantity == null || quantity.signum() <= 0) {
            return baseUnitCost;
        }
        BigDecimal perUnit = surcharge.divide(quantity, 6, RoundingMode.HALF_UP);
        return baseUnitCost.add(perUnit);
    }

    public record ReceiveLineInput(UUID lineId, BigDecimal quantity, UUID lotId) {
    }

    private record PreparedReceive(PurchaseOrderLine line, BigDecimal quantity, UUID lotId, BigDecimal lineValue) {
    }

    private BigDecimal overReceiptTolerancePercent() {
        return tenantSettingsRepository.findByTenantId(TenantContext.requireTenantId())
                .map(TenantSettings::getSettings)
                .map(settings -> settings.get("over_receipt_tolerance_percent"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(n -> BigDecimal.valueOf(n.doubleValue()))
                .orElse(BigDecimal.ZERO);
    }

    private PurchaseOrder requirePo(UUID purchaseOrderId) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
        if (!po.getTenantId().equals(TenantContext.requireTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found");
        }
        return po;
    }
}
