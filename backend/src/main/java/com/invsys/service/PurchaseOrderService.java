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
    public PurchaseOrderLine receiveLine(UUID lineId, UUID locationId, UUID lotId, BigDecimal quantity) {
        PurchaseOrderLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "PO line not found"));
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

        PurchaseOrder po = purchaseOrderRepository.findById(line.getPurchaseOrderId()).orElseThrow();
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
}
