package com.invsys.integration.easypost;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.WorkstationSettings;
import com.invsys.modules.catalog.domain.ShippingCarton;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.catalog.repository.ShippingCartonRepository;
import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.fulfillment.domain.Shipment;
import com.invsys.modules.inventory.repository.AllocationRepository;
import com.invsys.modules.fulfillment.repository.ShipmentRepository;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.service.CartonizationEngine;
import com.invsys.service.WorkstationSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Carrier rate shopping: ranks EasyPost quotes and can auto-buy the cheapest
 * rate that meets the sales order's requested ship date.
 */
@Service
public class RateShoppingService {

    private final EasyPostGateway easyPostGateway;
    private final EasyPostProperties easyPostProperties;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ShippingCartonRepository shippingCartonRepository;
    private final AllocationRepository allocationRepository;
    private final ShipmentRepository shipmentRepository;
    private final CartonizationEngine cartonizationEngine;
    private final WorkstationSettingsService workstationSettingsService;

    public RateShoppingService(
            EasyPostGateway easyPostGateway,
            EasyPostProperties easyPostProperties,
            SalesOrderRepository salesOrderRepository,
            SalesOrderLineRepository salesOrderLineRepository,
            CustomerRepository customerRepository,
            ProductVariantRepository productVariantRepository,
            ShippingCartonRepository shippingCartonRepository,
            AllocationRepository allocationRepository,
            ShipmentRepository shipmentRepository,
            CartonizationEngine cartonizationEngine,
            WorkstationSettingsService workstationSettingsService) {
        this.easyPostGateway = easyPostGateway;
        this.easyPostProperties = easyPostProperties;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.customerRepository = customerRepository;
        this.productVariantRepository = productVariantRepository;
        this.shippingCartonRepository = shippingCartonRepository;
        this.allocationRepository = allocationRepository;
        this.shipmentRepository = shipmentRepository;
        this.cartonizationEngine = cartonizationEngine;
        this.workstationSettingsService = workstationSettingsService;
    }

    @Transactional(readOnly = true)
    public RateQuoteResponse shopRates(UUID salesOrderId, UUID cartonId) {
        PackContext ctx = buildPackContext(salesOrderId, cartonId, null);
        List<EasyPostGateway.RateQuote> raw = easyPostGateway.shopRates(
                ctx.parcel(), salesOrderId.toString(), ctx.labelOptions());
        Instant shipBy = ctx.order().getRequestedShipDate() != null
                ? ctx.order().getRequestedShipDate()
                : Instant.now().plus(2, ChronoUnit.DAYS);
        List<RankedRate> ranked = rankRates(raw, shipBy);
        RankedRate recommended = ranked.stream().filter(RankedRate::meetsSla).findFirst()
                .orElse(ranked.isEmpty() ? null : ranked.getFirst());
        return new RateQuoteResponse(
                salesOrderId,
                ctx.pack().carton().getId(),
                ctx.pack().carton().getName(),
                ctx.billable(),
                ctx.pack().volumetricWeightLb(),
                ranked,
                recommended);
    }

    @Transactional
    public Shipment buyCheapestLabel(UUID salesOrderId, UUID cartonId) {
        PackContext ctx = buildPackContext(salesOrderId, cartonId, null);
        Instant shipBy = ctx.order().getRequestedShipDate() != null
                ? ctx.order().getRequestedShipDate()
                : Instant.now().plus(2, ChronoUnit.DAYS);
        List<EasyPostGateway.RateQuote> raw = easyPostGateway.shopRates(
                ctx.parcel(), salesOrderId.toString(), ctx.labelOptions());
        RankedRate recommended = rankRates(raw, shipBy).stream()
                .filter(RankedRate::meetsSla)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_RATE",
                        "No carrier rate meets the requested ship date"));

        EasyPostGateway.ShopResult shopped = easyPostGateway.shopAndBuyCheapest(
                ctx.parcel(), salesOrderId.toString(), ctx.labelOptions());
        EasyPostGateway.LabelResult label = shopped.purchased();

        Shipment shipment = new Shipment();
        shipment.setTenantId(TenantContext.requireTenantId());
        shipment.setSalesOrderId(salesOrderId);
        shipment.setCarrier(label.carrier() != null ? label.carrier() : recommended.carrier());
        shipment.setServiceLevel(label.service() != null ? label.service() : recommended.service());
        shipment.setTrackingNumber(label.trackingNumber());
        shipment.setLabelRef(label.labelRef());
        shipment.setLabelFileType(label.labelFileType() != null
                ? label.labelFileType()
                : ctx.labelOptions().normalizedFormat());
        shipment.setStatus("LABEL_CREATED");
        shipment.setTotalWeight(ctx.billable());
        shipment.setPostageAmount(label.postageAmount() != null ? label.postageAmount() : recommended.rate());
        shipment.setCartonId(ctx.pack().carton().getId());
        shipment.setCartonName(ctx.pack().carton().getName());
        shipment.setLength(ctx.pack().lengthIn());
        shipment.setWidth(ctx.pack().widthIn());
        shipment.setHeight(ctx.pack().heightIn());
        shipment.setVolumetricWeight(ctx.pack().volumetricWeightLb());
        return shipmentRepository.save(shipment);
    }

    private List<RankedRate> rankRates(List<EasyPostGateway.RateQuote> raw, Instant shipBy) {
        Instant now = Instant.now();
        long maxTransitDays = Math.max(1, ChronoUnit.DAYS.between(now, shipBy) + 1);
        List<RankedRate> ranked = new ArrayList<>();
        for (EasyPostGateway.RateQuote q : raw) {
            int transitDays = estimateTransitDays(q.service());
            ranked.add(new RankedRate(
                    q.carrier(),
                    q.service(),
                    q.rateId(),
                    q.rate(),
                    q.currency() != null ? q.currency() : "USD",
                    transitDays,
                    transitDays <= maxTransitDays,
                    false));
        }
        ranked.sort(Comparator
                .comparing(RankedRate::meetsSla).reversed()
                .thenComparing(RankedRate::rate));
        return ranked;
    }

    private static int estimateTransitDays(String service) {
        if (service == null) {
            return 5;
        }
        String s = service.toLowerCase();
        if (s.contains("overnight") || s.contains("next") || s.contains("express")) {
            return 1;
        }
        if (s.contains("2") || s.contains("second")) {
            return 2;
        }
        if (s.contains("3") || s.contains("priority")) {
            return 3;
        }
        return 5;
    }

    private PackContext buildPackContext(UUID salesOrderId, UUID cartonId, BigDecimal scaleWeightLb) {
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
        List<CartonizationEngine.LineItem> lineItems = buildLineItems(salesOrderId);
        CartonizationEngine.CartonizationResult pack;
        if (cartonId != null) {
            ShippingCarton carton = shippingCartonRepository.findById(cartonId)
                    .filter(c -> TenantContext.requireTenantId().equals(c.getTenantId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Carton not found"));
            pack = cartonizationEngine.selectCarton(lineItems, List.of(carton));
        } else {
            pack = cartonizationEngine.selectCarton(lineItems, loadCartons());
        }
        BigDecimal billable = pack.billableWeightLb();
        if (scaleWeightLb != null && scaleWeightLb.signum() > 0) {
            billable = billable.max(scaleWeightLb);
        }
        WorkstationSettings workstation = workstationSettingsService.getOrDefaultForCurrentUser();
        EasyPostGateway.LabelOptions labelOptions = EasyPostGateway.LabelOptions.fromWorkstation(
                workstation.getPrintMode(), workstation.getLabelFormat());
        Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        EasyPostGateway.AddressSpec to = customer != null
                ? EasyPostGateway.AddressSpec.fromMap(customer.getShippingAddress(), customer.getName())
                : null;
        EasyPostGateway.ParcelSpec parcel = new EasyPostGateway.ParcelSpec(
                pack.lengthIn(), pack.widthIn(), pack.heightIn(), billable,
                to, easyPostProperties.defaultFromAddress(), false);
        return new PackContext(order, pack, billable, parcel, labelOptions);
    }

    private List<CartonizationEngine.LineItem> buildLineItems(UUID salesOrderId) {
        List<SalesOrderLine> lines = salesOrderLineRepository.findBySalesOrderId(salesOrderId);
        List<CartonizationEngine.LineItem> items = new ArrayList<>();
        for (SalesOrderLine line : lines) {
            BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyShipped());
            if (remaining.signum() <= 0) {
                continue;
            }
            List<Allocation> active = allocationRepository.findBySalesOrderLineIdAndStatus(line.getId(), "ACTIVE");
            BigDecimal allocated = active.stream()
                    .map(Allocation::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal qty = allocated.signum() > 0 ? allocated.min(remaining) : remaining;
            ProductVariant variant = productVariantRepository.findById(line.getVariantId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                            "Variant not found for sales order line"));
            items.add(new CartonizationEngine.LineItem(
                    variant.getId(), qty,
                    variant.getLength(), variant.getWidth(), variant.getHeight(), variant.getDimUnit(),
                    variant.getWeight(), variant.getWeightUnit()));
        }
        return items;
    }

    private List<ShippingCarton> loadCartons() {
        return shippingCartonRepository.findByTenantIdAndActiveTrueOrderByLengthAscWidthAscHeightAsc(
                TenantContext.requireTenantId());
    }

    private record PackContext(
            SalesOrder order,
            CartonizationEngine.CartonizationResult pack,
            BigDecimal billable,
            EasyPostGateway.ParcelSpec parcel,
            EasyPostGateway.LabelOptions labelOptions
    ) {
    }

    public record RankedRate(
            String carrier,
            String service,
            String rateId,
            BigDecimal rate,
            String currency,
            int transitDays,
            boolean meetsSla,
            boolean recommended
    ) {
    }

    public record RateQuoteResponse(
            UUID salesOrderId,
            UUID cartonId,
            String cartonName,
            BigDecimal billableWeightLb,
            BigDecimal volumetricWeightLb,
            List<RankedRate> rates,
            RankedRate recommended
    ) {
        public RateQuoteResponse {
            if (rates != null && recommended != null) {
                rates = rates.stream()
                        .map(r -> new RankedRate(
                                r.carrier(), r.service(), r.rateId(), r.rate(), r.currency(),
                                r.transitDays(), r.meetsSla(),
                                recommended.rateId() != null && recommended.rateId().equals(r.rateId())
                                        || (recommended.carrier().equals(r.carrier())
                                        && recommended.service().equals(r.service())
                                        && recommended.rate().compareTo(r.rate()) == 0)))
                        .toList();
            }
        }
    }
}
