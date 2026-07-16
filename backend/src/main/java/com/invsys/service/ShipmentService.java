package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.Shipment;
import com.invsys.domain.ShipmentLine;
import com.invsys.integration.OutboxService;
import com.invsys.integration.easypost.EasyPostGateway;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.ShipmentLineRepository;
import com.invsys.repository.ShipmentRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository shipmentLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final AllocationRepository allocationRepository;
    private final InventoryService inventoryService;
    private final OutboxService outboxService;
    private final KitService kitService;
    private final EasyPostGateway easyPostClient;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           ShipmentLineRepository shipmentLineRepository,
                           SalesOrderRepository salesOrderRepository,
                           SalesOrderLineRepository salesOrderLineRepository,
                           AllocationRepository allocationRepository,
                           InventoryService inventoryService,
                           OutboxService outboxService,
                           KitService kitService,
                           EasyPostGateway easyPostClient) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentLineRepository = shipmentLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.allocationRepository = allocationRepository;
        this.inventoryService = inventoryService;
        this.easyPostClient = easyPostClient;
        this.outboxService = outboxService;
        this.kitService = kitService;
    }

    @Transactional
    public Shipment createShipment(UUID salesOrderId, String carrier, String trackingNumber,
                                   List<ShipLineRequest> lines) {
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));

        Shipment shipment = new Shipment();
        shipment.setTenantId(TenantContext.requireTenantId());
        shipment.setSalesOrderId(salesOrderId);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus("SHIPPED");
        shipment = shipmentRepository.save(shipment);

        for (ShipLineRequest req : lines) {
            SalesOrderLine soLine = salesOrderLineRepository.findById(req.salesOrderLineId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "SO line not found"));
            List<Allocation> allocations = allocationRepository.findBySalesOrderLineIdAndStatus(soLine.getId(), "ACTIVE");
            if (kitService.isKit(soLine.getVariantId())) {
                shipKitLine(soLine, req.quantity(), allocations);
            } else {
                shipStandardLine(soLine, req.quantity(), allocations);
            }
            soLine.setQtyShipped(soLine.getQtyShipped().add(req.quantity()));
            salesOrderLineRepository.save(soLine);

            ShipmentLine sl = new ShipmentLine();
            sl.setTenantId(TenantContext.requireTenantId());
            sl.setShipmentId(shipment.getId());
            sl.setSalesOrderLineId(soLine.getId());
            sl.setQuantity(req.quantity());
            shipmentLineRepository.save(sl);
        }

        updateOrderStatus(order);
        outboxService.append("SHIPMENT", shipment.getId(), "SHIPMENT_CREATED",
                Map.of("shipmentId", shipment.getId(), "salesOrderId", salesOrderId));
        if ("SHIPPED".equals(order.getStatus())) {
            outboxService.append("SALES_ORDER", salesOrderId, "SALES_ORDER_SHIPPED", Map.of(
                    "salesOrderId", salesOrderId,
                    "shipmentId", shipment.getId(),
                    "carrier", carrier == null ? "" : carrier,
                    "trackingNumber", trackingNumber == null ? "" : trackingNumber));
        }
        return shipment;
    }

    private void shipStandardLine(SalesOrderLine soLine, BigDecimal quantity, List<Allocation> allocations) {
        BigDecimal remaining = quantity;
        for (Allocation allocation : allocations) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal shipQty = allocation.getQuantity().min(remaining);
            inventoryService.ship(allocation, shipQty);
            remaining = remaining.subtract(shipQty);
        }
    }

    private void shipKitLine(SalesOrderLine soLine, BigDecimal kitQuantity, List<Allocation> allocations) {
        List<KitService.BomComponent> components = kitService.explodeComponents(soLine.getVariantId());
        for (KitService.BomComponent component : components) {
            BigDecimal componentQty = kitQuantity.multiply(component.quantityPerParent());
            BigDecimal remaining = componentQty;
            for (Allocation allocation : allocations) {
                if (remaining.signum() <= 0) {
                    break;
                }
                if (!allocation.getVariantId().equals(component.variantId())) {
                    continue;
                }
                if (!"ACTIVE".equals(allocation.getStatus())) {
                    continue;
                }
                BigDecimal shipQty = allocation.getQuantity().min(remaining);
                inventoryService.ship(allocation, shipQty);
                remaining = remaining.subtract(shipQty);
            }
        }
    }

    @Transactional
    public Shipment createPackLabel(UUID salesOrderId, BigDecimal totalWeightLb, String carrier) {
        salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));

        EasyPostGateway.LabelResult label = easyPostClient.purchaseLabel(
                carrier != null ? carrier : "EASYPOST",
                totalWeightLb,
                salesOrderId.toString());

        Shipment shipment = new Shipment();
        shipment.setTenantId(TenantContext.requireTenantId());
        shipment.setSalesOrderId(salesOrderId);
        shipment.setCarrier(carrier != null ? carrier : "EASYPOST");
        shipment.setTrackingNumber(label.trackingNumber());
        shipment.setLabelRef(label.labelRef());
        shipment.setStatus("LABEL_CREATED");
        shipment.setTotalWeight(totalWeightLb);
        shipment.setPostageAmount(label.postageAmount());
        return shipmentRepository.save(shipment);
    }

    private void updateOrderStatus(SalesOrder order) {
        List<SalesOrderLine> lines = salesOrderLineRepository.findBySalesOrderId(order.getId());
        boolean allShipped = lines.stream().allMatch(l -> l.getQtyShipped().compareTo(l.getQtyOrdered()) >= 0);
        boolean anyShipped = lines.stream().anyMatch(l -> l.getQtyShipped().signum() > 0);
        if (allShipped) {
            order.setStatus("SHIPPED");
        } else if (anyShipped) {
            order.setStatus("PARTIALLY_SHIPPED");
        }
        salesOrderRepository.save(order);
    }

    public record ShipLineRequest(UUID salesOrderLineId, BigDecimal quantity) {
    }
}
