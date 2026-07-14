package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final InventoryService inventoryService;
    private final UomConversionService uomConversionService;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                PurchaseOrderLineRepository lineRepository,
                                InventoryService inventoryService,
                                UomConversionService uomConversionService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.inventoryService = inventoryService;
        this.uomConversionService = uomConversionService;
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
        if (quantity.compareTo(remaining) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "OVER_RECEIVE", "Cannot receive more than ordered");
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

    private PurchaseOrder requirePo(UUID purchaseOrderId) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
        if (!po.getTenantId().equals(TenantContext.requireTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found");
        }
        return po;
    }
}
