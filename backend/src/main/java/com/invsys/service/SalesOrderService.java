package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.integration.OutboxService;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository lineRepository;
    private final AllocationService allocationService;
    private final LocationRepository locationRepository;
    private final OutboxService outboxService;
    private final AuditService auditService;

    public SalesOrderService(SalesOrderRepository salesOrderRepository,
                             SalesOrderLineRepository lineRepository,
                             AllocationService allocationService,
                             LocationRepository locationRepository,
                             OutboxService outboxService,
                             AuditService auditService) {
        this.salesOrderRepository = salesOrderRepository;
        this.lineRepository = lineRepository;
        this.allocationService = allocationService;
        this.locationRepository = locationRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
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
        SalesOrder order = getOrder(orderId);
        if (!List.of("CONFIRMED", "ALLOCATED").contains(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Order cannot be allocated");
        }
        String before = order.getStatus();
        List<UUID> locationIds = locationRepository.findByTenantIdOrderByPathAsc(TenantContext.requireTenantId())
                .stream().map(l -> l.getId()).toList();
        for (SalesOrderLine line : lineRepository.findBySalesOrderId(orderId)) {
            allocationService.allocate(line, locationIds);
        }
        order.setStatus("ALLOCATED");
        order = salesOrderRepository.save(order);
        auditService.record("SALES_ORDER_ALLOCATE", "SALES_ORDER", order.getId(), Map.of(
                "status", Map.of("before", before, "after", order.getStatus())));
        return order;
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

    private SalesOrder getOrder(UUID orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
    }
}
