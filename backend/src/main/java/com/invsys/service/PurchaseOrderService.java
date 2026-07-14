package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final InventoryService inventoryService;
    private final UomConversionService uomConversionService;
    private final TenantSettingsRepository tenantSettingsRepository;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                PurchaseOrderLineRepository lineRepository,
                                InventoryService inventoryService,
                                UomConversionService uomConversionService,
                                TenantSettingsRepository tenantSettingsRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.inventoryService = inventoryService;
        this.uomConversionService = uomConversionService;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    @Transactional
    public PurchaseOrder submit(UUID purchaseOrderId) {
        PurchaseOrder po = requirePo(purchaseOrderId);
        if (!"DRAFT".equals(po.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only DRAFT purchase orders can be submitted");
        }
        po.setStatus("SUBMITTED");
        return purchaseOrderRepository.save(po);
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
        inventoryService.receive(line.getVariantId(), locationId, lotId, standardQty,
                "PURCHASE_ORDER_LINE", line.getId(), line.getUnitCost());
        line.setQtyReceived(line.getQtyReceived().add(quantity));
        lineRepository.save(line);

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
        return line;
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
