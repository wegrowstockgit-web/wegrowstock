package com.invsys.modules.fulfillment.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.fulfillment.domain.Shipment;
import com.invsys.modules.fulfillment.domain.ShipmentLine;
import com.invsys.modules.catalog.domain.ShippingCarton;
import com.invsys.domain.WorkstationSettings;
import com.invsys.core.integration.OutboxService;
import com.invsys.integration.easypost.EasyPostGateway;
import com.invsys.integration.easypost.EasyPostProperties;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.fulfillment.repository.ShipmentLineRepository;
import com.invsys.modules.fulfillment.repository.ShipmentRepository;
import com.invsys.modules.catalog.repository.ShippingCartonRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.modules.inventory.service.LpnService;
import com.invsys.service.CartonizationEngine;
import com.invsys.service.KitService;
import com.invsys.service.WorkstationSettingsService;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository shipmentLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final AllocationRepository allocationRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ShippingCartonRepository shippingCartonRepository;
    private final InventoryService inventoryService;
    private final LpnService lpnService;
    private final OutboxService outboxService;
    private final KitService kitService;
    private final CartonizationEngine cartonizationEngine;
    private final EasyPostGateway easyPostClient;
    private final WorkstationSettingsService workstationSettingsService;
    private final CustomerRepository customerRepository;
    private final EasyPostProperties easyPostProperties;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           ShipmentLineRepository shipmentLineRepository,
                           SalesOrderRepository salesOrderRepository,
                           SalesOrderLineRepository salesOrderLineRepository,
                           AllocationRepository allocationRepository,
                           ProductVariantRepository productVariantRepository,
                           ShippingCartonRepository shippingCartonRepository,
                           InventoryService inventoryService,
                           LpnService lpnService,
                           OutboxService outboxService,
                           KitService kitService,
                           CartonizationEngine cartonizationEngine,
                           EasyPostGateway easyPostClient,
                           WorkstationSettingsService workstationSettingsService,
                           CustomerRepository customerRepository,
                           EasyPostProperties easyPostProperties) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentLineRepository = shipmentLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.allocationRepository = allocationRepository;
        this.productVariantRepository = productVariantRepository;
        this.shippingCartonRepository = shippingCartonRepository;
        this.inventoryService = inventoryService;
        this.lpnService = lpnService;
        this.easyPostClient = easyPostClient;
        this.outboxService = outboxService;
        this.kitService = kitService;
        this.cartonizationEngine = cartonizationEngine;
        this.workstationSettingsService = workstationSettingsService;
        this.customerRepository = customerRepository;
        this.easyPostProperties = easyPostProperties;
    }

    @Transactional
    public Shipment createShipment(UUID salesOrderId, String carrier, String trackingNumber,
                                   List<ShipLineRequest> lines) {
        return createShipment(salesOrderId, carrier, trackingNumber, lines, null);
    }

    @Transactional
    public Shipment createShipment(UUID salesOrderId, String carrier, String trackingNumber,
                                   List<ShipLineRequest> lines, String lpnBarcode) {
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));

        boolean hasLpn = lpnBarcode != null && !lpnBarcode.isBlank();
        List<ShipLineRequest> shipLines = lines != null ? lines : List.of();
        if (!hasLpn && shipLines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHIP_LINES_REQUIRED",
                    "Provide shipment lines and/or an lpnBarcode");
        }

        Shipment shipment = new Shipment();
        shipment.setTenantId(TenantContext.requireTenantId());
        shipment.setSalesOrderId(salesOrderId);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus("SHIPPED");
        shipment = shipmentRepository.save(shipment);

        if (hasLpn) {
            shipByLpn(order, shipment, lpnBarcode.trim());
        }

        for (ShipLineRequest req : shipLines) {
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

    private void shipByLpn(SalesOrder order, Shipment shipment, String lpnBarcode) {
        LpnService.LpnContents contents = lpnService.contents(lpnBarcode);
        List<SalesOrderLine> soLines = salesOrderLineRepository.findBySalesOrderId(order.getId());

        lpnService.shipLpn(lpnBarcode, order.getId(), shipment.getId());

        for (InventoryLevel level : contents.levels()) {
            SalesOrderLine soLine = soLines.stream()
                    .filter(l -> level.getVariantId().equals(l.getVariantId()))
                    .findFirst()
                    .orElse(null);
            if (soLine != null) {
                soLine.setQtyShipped(soLine.getQtyShipped().add(level.getOnHand()));
                salesOrderLineRepository.save(soLine);

                ShipmentLine sl = new ShipmentLine();
                sl.setTenantId(TenantContext.requireTenantId());
                sl.setShipmentId(shipment.getId());
                sl.setSalesOrderLineId(soLine.getId());
                sl.setQuantity(level.getOnHand());
                shipmentLineRepository.save(sl);
            }
        }
    }

    /**
     * Preview carton selection for a sales order without purchasing a label.
     */
    @Transactional(readOnly = true)
    public CartonizationEngine.CartonizationResult previewCartonization(UUID salesOrderId) {
        salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
        return cartonizationEngine.selectCarton(buildLineItems(salesOrderId), loadCartons());
    }

    /**
     * Cartonize the SO, rate-shop EasyPost, auto-buy the cheapest label.
     *
     * @param scaleWeightLb optional scale reading; billable weight is max(scale, computed)
     */
    @Transactional
    public Shipment createPackLabel(UUID salesOrderId, BigDecimal scaleWeightLb, String carrier) {
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));

        List<CartonizationEngine.LineItem> lineItems = buildLineItems(salesOrderId);
        boolean requiresDgDocs = orderContainsHazmat(salesOrderId);
        CartonizationEngine.CartonizationResult pack =
                cartonizationEngine.selectCarton(lineItems, loadCartons());

        BigDecimal billable = pack.billableWeightLb();
        if (scaleWeightLb != null && scaleWeightLb.signum() > 0) {
            billable = billable.max(scaleWeightLb);
        }

        WorkstationSettings workstation = workstationSettingsService.getOrDefaultForCurrentUser();
        EasyPostGateway.LabelOptions labelOptions = EasyPostGateway.LabelOptions.fromWorkstation(
                workstation.getPrintMode(), workstation.getLabelFormat());

        EasyPostGateway.ParcelSpec parcel = buildParcelSpec(order, pack, billable);
        EasyPostGateway.ShopResult shopped = easyPostClient.shopAndBuyCheapest(
                parcel,
                salesOrderId.toString(),
                labelOptions);
        EasyPostGateway.LabelResult label = shopped.purchased();

        String resolvedCarrier = carrier != null && !carrier.isBlank()
                ? carrier
                : (label.carrier() != null ? label.carrier() : "EASYPOST");

        Shipment shipment = new Shipment();
        shipment.setTenantId(TenantContext.requireTenantId());
        shipment.setSalesOrderId(salesOrderId);
        shipment.setCarrier(resolvedCarrier);
        shipment.setServiceLevel(label.service());
        shipment.setTrackingNumber(label.trackingNumber());
        shipment.setLabelRef(label.labelRef());
        shipment.setLabelFileType(label.labelFileType() != null
                ? label.labelFileType()
                : labelOptions.normalizedFormat());
        shipment.setStatus("LABEL_CREATED");
        shipment.setRequiresDgDocumentation(requiresDgDocs);
        shipment.setTotalWeight(billable);
        shipment.setPostageAmount(label.postageAmount());
        shipment.setCartonId(pack.carton().getId());
        shipment.setCartonName(pack.carton().getName());
        shipment.setLength(pack.lengthIn());
        shipment.setWidth(pack.widthIn());
        shipment.setHeight(pack.heightIn());
        shipment.setVolumetricWeight(pack.volumetricWeightLb());
        return shipmentRepository.save(shipment);
    }

    private boolean orderContainsHazmat(UUID salesOrderId) {
        for (SalesOrderLine line : salesOrderLineRepository.findBySalesOrderId(salesOrderId)) {
            BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyShipped());
            if (remaining.signum() <= 0) {
                continue;
            }
            ProductVariant variant = productVariantRepository.findById(line.getVariantId()).orElse(null);
            if (variant != null && variant.isHazmat()) {
                return true;
            }
        }
        return false;
    }

    private List<CartonizationEngine.LineItem> buildLineItems(UUID salesOrderId) {
        List<SalesOrderLine> lines = salesOrderLineRepository.findBySalesOrderId(salesOrderId);
        List<CartonizationEngine.LineItem> items = new ArrayList<>();
        for (SalesOrderLine line : lines) {
            BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyShipped());
            if (remaining.signum() <= 0) {
                continue;
            }
            // Prefer allocated qty when present (floor pack of what was picked)
            List<Allocation> active = allocationRepository.findBySalesOrderLineIdAndStatus(line.getId(), "ACTIVE");
            BigDecimal allocated = active.stream()
                    .map(Allocation::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal qty = allocated.signum() > 0 ? allocated.min(remaining) : remaining;

            ProductVariant variant = productVariantRepository.findById(line.getVariantId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                            "Variant not found for sales order line"));
            items.add(new CartonizationEngine.LineItem(
                    variant.getId(),
                    qty,
                    variant.getLength(),
                    variant.getWidth(),
                    variant.getHeight(),
                    variant.getDimUnit(),
                    variant.getWeight(),
                    variant.getWeightUnit()));
        }
        return items;
    }

    private List<ShippingCarton> loadCartons() {
        return shippingCartonRepository.findByTenantIdAndActiveTrueOrderByLengthAscWidthAscHeightAsc(
                TenantContext.requireTenantId());
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

    private EasyPostGateway.ParcelSpec buildParcelSpec(
            SalesOrder order,
            CartonizationEngine.CartonizationResult pack,
            BigDecimal billable) {
        Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        EasyPostGateway.AddressSpec to = customer != null
                ? EasyPostGateway.AddressSpec.fromMap(customer.getShippingAddress(), customer.getName())
                : null;
        EasyPostGateway.AddressSpec from = easyPostProperties.defaultFromAddress();
        return new EasyPostGateway.ParcelSpec(
                pack.lengthIn(), pack.widthIn(), pack.heightIn(), billable, to, from, false);
    }

    public record ShipLineRequest(UUID salesOrderLineId, BigDecimal quantity) {
    }
}
