package com.invsys.modules.sales.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.core.integration.OutboxService;
import com.invsys.integration.inbound.CanonicalAddress;
import com.invsys.integration.inbound.CanonicalInboundOrder;
import com.invsys.integration.inbound.CanonicalOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.metrics.WmsMetrics;
import com.invsys.core.tenancy.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.invsys.modules.fulfillment.service.AllocationService;
import com.invsys.service.AuditService;
import com.invsys.service.DocumentSequenceService;
import com.invsys.service.SoftKitExplosionService;

@Service
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository lineRepository;
    private final AllocationService allocationService;
    private final LocationRepository locationRepository;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository variantRepository;
    private final SoftKitExplosionService softKitExplosionService;
    private final DocumentSequenceService sequenceService;
    private final MeterRegistry meterRegistry;
    private final WmsMetrics wmsMetrics;

    public SalesOrderService(SalesOrderRepository salesOrderRepository,
                             SalesOrderLineRepository lineRepository,
                             AllocationService allocationService,
                             LocationRepository locationRepository,
                             OutboxService outboxService,
                             AuditService auditService,
                             CustomerRepository customerRepository,
                             ProductVariantRepository variantRepository,
                             SoftKitExplosionService softKitExplosionService,
                             DocumentSequenceService sequenceService,
                             MeterRegistry meterRegistry,
                             WmsMetrics wmsMetrics) {
        this.salesOrderRepository = salesOrderRepository;
        this.lineRepository = lineRepository;
        this.allocationService = allocationService;
        this.locationRepository = locationRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.customerRepository = customerRepository;
        this.variantRepository = variantRepository;
        this.softKitExplosionService = softKitExplosionService;
        this.sequenceService = sequenceService;
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.wmsMetrics = wmsMetrics;
    }

    /**
     * Persists a channel-agnostic inbound order inside a single transaction boundary.
     */
    @Transactional
    public SalesOrder createFromCanonical(CanonicalInboundOrder inbound) {
        if (inbound == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Canonical order is required");
        }
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = resolveCustomerId(tenantId, inbound.customerIdentifier());

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customerId);
        order.setNumber(sequenceService.nextNumber("SO", "SO-{YYYY}-{seq:5}"));
        order.setStatus("CONFIRMED");
        order.setChannel(inbound.channelSource().name());
        if (inbound.externalOrderRef() != null && !inbound.externalOrderRef().isBlank()) {
            order.setCustomerPoNumber(inbound.externalOrderRef().trim());
        }
        order = salesOrderRepository.save(order);

        boolean needsReview = false;
        List<CanonicalOrderLine> lines = inbound.lines();
        if (lines.isEmpty()) {
            needsReview = true;
        }
        for (CanonicalOrderLine item : lines) {
            String sku = item.sku() != null ? item.sku().trim() : "";
            if (sku.isBlank()) {
                needsReview = true;
                continue;
            }
            BigDecimal qty = item.quantity() != null ? item.quantity() : BigDecimal.ONE;
            BigDecimal unitPrice = item.unitPrice() != null ? item.unitPrice() : BigDecimal.ZERO;
            Optional<ProductVariant> variantOpt = variantRepository.findByTenantIdAndSku(tenantId, sku);
            if (variantOpt.isEmpty()) {
                needsReview = true;
                continue;
            }
            ProductVariant variant = variantOpt.get();
            List<SoftKitExplosionService.ExplodedLine> exploded = softKitExplosionService.explode(
                    tenantId,
                    variant.getId(),
                    qty,
                    unitPrice,
                    false,
                    false);
            if (variant.isSoftKit() && exploded.isEmpty()) {
                needsReview = true;
                continue;
            }
            for (SoftKitExplosionService.ExplodedLine component : exploded) {
                SalesOrderLine line = new SalesOrderLine();
                line.setTenantId(tenantId);
                line.setSalesOrderId(order.getId());
                line.setVariantId(component.variantId());
                line.setQtyOrdered(component.quantity());
                line.setUnitPrice(component.unitPrice());
                lineRepository.save(line);
            }
        }

        if (needsReview) {
            order.setStatus("NEEDS_REVIEW");
            order = salesOrderRepository.save(order);
        }

        auditService.record("SALES_ORDER_INBOUND", "SALES_ORDER", order.getId(), Map.of(
                "channel", inbound.channelSource().name(),
                "externalOrderRef", inbound.externalOrderRef() != null ? inbound.externalOrderRef() : "",
                "customerIdentifier", inbound.customerIdentifier() != null ? inbound.customerIdentifier() : "",
                "status", order.getStatus(),
                "billingAddress", addressSnapshot(inbound.billingAddress()),
                "shippingAddress", addressSnapshot(inbound.shippingAddress()),
                "lineCount", lines.size()));

        outboxService.append("SALES_ORDER", order.getId(), "SALES_ORDER_INBOUND", Map.of(
                "orderId", order.getId(),
                "channel", inbound.channelSource().name(),
                "externalOrderRef", inbound.externalOrderRef() != null ? inbound.externalOrderRef() : ""));

        return order;
    }

    @Transactional
    public SalesOrder confirm(UUID orderId) {
        SalesOrder order = getOrder(orderId);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Order is not in DRAFT");
        }
        String before = order.getStatus();
        order.setStatus("CONFIRMED");
        order = salesOrderRepository.save(order);
        outboxService.append("SALES_ORDER", order.getId(), "SALES_ORDER_CONFIRMED", Map.of("orderId", order.getId()));
        auditService.record("SALES_ORDER_CONFIRM", "SALES_ORDER", order.getId(), Map.of(
                "status", Map.of("before", before, "after", order.getStatus())));
        return order;
    }

    @Transactional
    public SalesOrder allocate(UUID orderId) {
        Timer.Sample allocationSample = wmsMetrics.startAllocation();
        try {
            SalesOrder order = getOrder(orderId);
            if (!List.of("CONFIRMED", "BACKORDERED", "ALLOCATED").contains(order.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Order cannot be allocated");
            }
            String before = order.getStatus();
            List<UUID> locationIds = locationRepository.findByTenantIdOrderByPathAsc(TenantContext.requireTenantId())
                    .stream().map(l -> l.getId()).toList();
            BigDecimal totalOrdered = BigDecimal.ZERO;
            BigDecimal totalAllocated = BigDecimal.ZERO;
            for (SalesOrderLine line : lineRepository.findBySalesOrderId(orderId)) {
                allocationService.allocate(line, locationIds);
                SalesOrderLine refreshed = lineRepository.findById(line.getId()).orElse(line);
                totalOrdered = totalOrdered.add(refreshed.getQtyOrdered());
                totalAllocated = totalAllocated.add(
                        refreshed.getQtyAllocated() != null ? refreshed.getQtyAllocated() : BigDecimal.ZERO);
            }
            // Soft backorder: no stock reserved → BACKORDERED; otherwise ALLOCATED (may still be short).
            String after = totalAllocated.signum() <= 0 ? "BACKORDERED" : "ALLOCATED";
            order.setStatus(after);
            order = salesOrderRepository.save(order);
            if ("ALLOCATED".equals(after)) {
                outboxService.append("SALES_ORDER", order.getId(), "ORDER_ALLOCATED", Map.of(
                        "orderId", order.getId(),
                        "qtyAllocated", totalAllocated));
                wmsMetrics.incrementOrdersProcessed();
            }
            auditService.record("SALES_ORDER_ALLOCATE", "SALES_ORDER", order.getId(), Map.of(
                    "status", Map.of("before", before, "after", order.getStatus()),
                    "qtyOrdered", totalOrdered,
                    "qtyAllocated", totalAllocated));
            return order;
        } finally {
            wmsMetrics.stopAllocation(allocationSample);
        }
    }

    @Transactional
    public SalesOrder cancel(UUID orderId) {
        SalesOrder order = getOrder(orderId);
        if ("CLOSED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Order cannot be cancelled");
        }
        String before = order.getStatus();
        lineRepository.findBySalesOrderId(orderId).forEach(line -> allocationService.releaseForLine(line.getId()));
        order.setStatus("CANCELLED");
        order = salesOrderRepository.save(order);
        auditService.record("SALES_ORDER_CANCEL", "SALES_ORDER", order.getId(), Map.of(
                "status", Map.of("before", before, "after", order.getStatus())));
        return order;
    }

    private UUID resolveCustomerId(UUID tenantId, String customerIdentifier) {
        if (customerIdentifier != null && !customerIdentifier.isBlank()) {
            String key = customerIdentifier.trim();
            try {
                UUID id = UUID.fromString(key);
                Optional<Customer> byId = customerRepository.findById(id)
                        .filter(c -> tenantId.equals(c.getTenantId()));
                if (byId.isPresent()) {
                    return byId.get().getId();
                }
            } catch (IllegalArgumentException ignored) {
                // not a UUID — match email/name below
            }
            List<Customer> customers = customerRepository.findByTenantIdOrderByNameAsc(tenantId);
            String lower = key.toLowerCase();
            Optional<Customer> byEmail = customers.stream()
                    .filter(c -> c.getEmail() != null && lower.equals(c.getEmail().toLowerCase()))
                    .findFirst();
            if (byEmail.isPresent()) {
                return byEmail.get().getId();
            }
            Optional<Customer> byName = customers.stream()
                    .filter(c -> c.getName() != null && lower.equals(c.getName().toLowerCase()))
                    .findFirst();
            if (byName.isPresent()) {
                return byName.get().getId();
            }
        }
        return customerRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .findFirst()
                .map(Customer::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_CUSTOMER",
                        "No customer configured for tenant"));
    }

    private static Map<String, Object> addressSnapshot(CanonicalAddress address) {
        if (address == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "name", address.name());
        putIfPresent(map, "line1", address.line1());
        putIfPresent(map, "line2", address.line2());
        putIfPresent(map, "city", address.city());
        putIfPresent(map, "region", address.region());
        putIfPresent(map, "postalCode", address.postalCode());
        putIfPresent(map, "country", address.country());
        return map;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private SalesOrder getOrder(UUID orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
    }
}
