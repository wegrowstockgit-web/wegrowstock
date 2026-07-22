package com.invsys.support.tools;

import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.support.tools.SupportCopilotToolModels.AtpRequest;
import com.invsys.support.tools.SupportCopilotToolModels.AtpResponse;
import com.invsys.support.tools.SupportCopilotToolModels.LedgerHistoryRequest;
import com.invsys.support.tools.SupportCopilotToolModels.LedgerHistoryResponse;
import com.invsys.support.tools.SupportCopilotToolModels.LedgerMovementView;
import com.invsys.support.tools.SupportCopilotToolModels.OrderStatusRequest;
import com.invsys.support.tools.SupportCopilotToolModels.OrderStatusResponse;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.invsys.domain.Tenant;

/**
 * Multi-tenant read-only CQRS lookups for the Support Copilot.
 * Tenant ID is always taken from {@link TenantContext} — never from the LLM.
 */
@Service
public class SupportCopilotReadService {

    private static final Set<String> OPEN_PO_STATUSES = Set.of(
            "SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED");

    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final LocationRepository locationRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;

    public SupportCopilotReadService(
            ProductVariantRepository variantRepository,
            InventoryLevelRepository levelRepository,
            LocationRepository locationRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderLineRepository purchaseOrderLineRepository,
            SalesOrderRepository salesOrderRepository,
            SalesOrderLineRepository salesOrderLineRepository,
            CustomerRepository customerRepository,
            InventoryService inventoryService
    ) {
        this.variantRepository = variantRepository;
        this.levelRepository = levelRepository;
        this.locationRepository = locationRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public AtpResponse checkAvailableToPromise(AtpRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String sku = request == null || request.sku() == null ? "" : request.sku().trim();
        if (sku.isBlank()) {
            return new AtpResponse("", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }

        Optional<ProductVariant> variantOpt = variantRepository.findByTenantIdAndSku(tenantId, sku);
        if (variantOpt.isEmpty()) {
            return new AtpResponse(sku, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
        UUID variantId = variantOpt.get().getId();
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(tenantId, variantId);
        levels = filterByWarehouse(tenantId, levels, request.warehouseId());

        BigDecimal onHand = levels.stream()
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = levels.stream()
                .map(InventoryLevel::getAllocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal atp = onHand.subtract(allocated);
        if (atp.signum() < 0) {
            atp = BigDecimal.ZERO;
        }

        String nextPo = findNextInboundPoNumber(tenantId, variantId);
        return new AtpResponse(sku, onHand, allocated, atp, nextPo);
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse checkOrderStatus(OrderStatusRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String number = request == null || request.orderNumber() == null
                ? ""
                : request.orderNumber().trim();
        if (number.isBlank()) {
            return new OrderStatusResponse("", "UNKNOWN", "Order number was empty.", null);
        }

        Optional<SalesOrder> orderOpt = salesOrderRepository.findByTenantIdAndNumberIgnoreCase(tenantId, number);
        if (orderOpt.isEmpty()) {
            return new OrderStatusResponse(number, "NOT_FOUND", "No sales order matches that number.", null);
        }
        SalesOrder order = orderOpt.get();
        String status = order.getStatus() == null ? "UNKNOWN" : order.getStatus();
        String holdReason = null;
        String missingSku = null;

        if ("HOLD".equalsIgnoreCase(status) || "CREDIT_HOLD".equalsIgnoreCase(status)) {
            holdReason = "Customer credit hold — billing must clear the account before stock can be reserved.";
            Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
            if (customer != null && customer.getCreditLimit() != null
                    && customer.getCreditLimit().signum() == 0) {
                holdReason = "Customer credit limit is zero — raise the limit on the Customers page before allocating.";
            }
        } else if ("BACKORDERED".equalsIgnoreCase(status)) {
            holdReason = "Stock allocation hold — not enough on-hand quantity to reserve every line.";
            missingSku = findFirstShortSku(tenantId, order.getId());
        }

        return new OrderStatusResponse(order.getNumber(), status, holdReason, missingSku);
    }

    @Transactional(readOnly = true)
    public LedgerHistoryResponse getLedgerHistorySummary(LedgerHistoryRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String sku = request == null || request.sku() == null ? "" : request.sku().trim();
        int limit = request == null ? 5 : Math.min(Math.max(request.limit(), 1), 20);
        if (sku.isBlank()) {
            return new LedgerHistoryResponse("", List.of());
        }

        Optional<ProductVariant> variantOpt = variantRepository.findByTenantIdAndSku(tenantId, sku);
        if (variantOpt.isEmpty()) {
            return new LedgerHistoryResponse(sku, List.of());
        }

        List<InventoryLedger> rows = inventoryService.listRecentLedger(limit, variantOpt.get().getId());
        List<LedgerMovementView> movements = new ArrayList<>();
        for (InventoryLedger row : rows) {
            movements.add(new LedgerMovementView(
                    row.getMovementType(),
                    row.getQuantityDelta() == null ? "0" : row.getQuantityDelta().toPlainString(),
                    row.getReasonCode() == null ? "" : row.getReasonCode(),
                    row.getCreatedAt() == null ? "" : row.getCreatedAt().toString()));
        }
        return new LedgerHistoryResponse(sku, List.copyOf(movements));
    }

    /** Plain-English summary for heuristic / prompt injection (never exposes table names). */
    public String formatLiveFactsForPrompt(String question, String warehouseIdHint) {
        if (question == null || question.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String orderNumber = extractOrderNumber(question);
        if (orderNumber != null) {
            OrderStatusResponse status = checkOrderStatus(new OrderStatusRequest(orderNumber));
            sb.append("Live order check for ").append(status.orderNumber())
                    .append(": status=").append(status.status());
            if (status.holdReason() != null) {
                sb.append("; hold=").append(status.holdReason());
            }
            if (status.missingSku() != null) {
                sb.append("; short SKU=").append(status.missingSku());
            }
            sb.append('\n');
        }
        String sku = extractSku(question);
        if (sku != null) {
            AtpResponse atp = checkAvailableToPromise(new AtpRequest(sku, warehouseIdHint));
            sb.append("Live ATP for SKU ").append(atp.sku())
                    .append(": on-hand=").append(atp.onHand())
                    .append(", reserved=").append(atp.allocated())
                    .append(", available-to-promise=").append(atp.availableToPromise());
            if (atp.nextInboundPoNumber() != null) {
                sb.append(", next inbound PO=").append(atp.nextInboundPoNumber());
            }
            sb.append('\n');
            if (question.toLowerCase(Locale.ROOT).contains("ledger")
                    || question.toLowerCase(Locale.ROOT).contains("history")
                    || question.toLowerCase(Locale.ROOT).contains("disappear")
                    || question.toLowerCase(Locale.ROOT).contains("where did")) {
                LedgerHistoryResponse hist = getLedgerHistorySummary(new LedgerHistoryRequest(sku, 5));
                if (!hist.movements().isEmpty()) {
                    sb.append("Recent stock movements for ").append(sku).append(':');
                    for (LedgerMovementView m : hist.movements()) {
                        sb.append(" [").append(m.movementType()).append(' ')
                                .append(m.quantityDelta());
                        if (m.reasonCode() != null && !m.reasonCode().isBlank()) {
                            sb.append(' ').append(m.reasonCode());
                        }
                        sb.append(']');
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString().strip();
    }

    static String extractOrderNumber(String question) {
        var matcher = java.util.regex.Pattern
                .compile("\\b(SO-?\\d+(?:-\\d+)*)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(question);
        if (matcher.find()) {
            String raw = matcher.group(1).toUpperCase(Locale.ROOT);
            if (raw.startsWith("SO-")) {
                return raw;
            }
            if (raw.startsWith("SO")) {
                return "SO-" + raw.substring(2);
            }
            return raw;
        }
        return null;
    }

    static String extractSku(String question) {
        var labeled = java.util.regex.Pattern
                .compile("\\b(?:sku|item)\\s*[:=]?\\s*([A-Za-z0-9][A-Za-z0-9._-]{1,40})",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(question);
        if (labeled.find()) {
            return labeled.group(1);
        }
        return null;
    }

    private List<InventoryLevel> filterByWarehouse(
            UUID tenantId,
            List<InventoryLevel> levels,
            String warehouseId
    ) {
        if (warehouseId == null || warehouseId.isBlank() || levels.isEmpty()) {
            return levels;
        }
        UUID warehouseUuid;
        try {
            warehouseUuid = UUID.fromString(warehouseId.trim());
        } catch (IllegalArgumentException ex) {
            return levels;
        }
        Optional<Location> warehouse = locationRepository.findById(warehouseUuid)
                .filter(loc -> tenantId.equals(loc.getTenantId()));
        if (warehouse.isEmpty()) {
            return levels;
        }
        String prefix = warehouse.get().getPath();
        Set<UUID> locationIds = locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .filter(loc -> loc.getPath().equals(prefix) || loc.getPath().startsWith(prefix + "/"))
                .map(Location::getId)
                .collect(Collectors.toSet());
        return levels.stream()
                .filter(level -> locationIds.contains(level.getLocationId()))
                .toList();
    }

    private String findNextInboundPoNumber(UUID tenantId, UUID variantId) {
        List<PurchaseOrder> orders = purchaseOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        for (PurchaseOrder po : orders) {
            if (po.getStatus() == null || !OPEN_PO_STATUSES.contains(po.getStatus())) {
                continue;
            }
            List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPurchaseOrderId(po.getId());
            for (PurchaseOrderLine line : lines) {
                if (!variantId.equals(line.getVariantId())) {
                    continue;
                }
                BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyReceived());
                if (remaining.signum() > 0) {
                    return po.getNumber();
                }
            }
        }
        return null;
    }

    private String findFirstShortSku(UUID tenantId, UUID salesOrderId) {
        List<SalesOrderLine> lines = salesOrderLineRepository.findBySalesOrderId(salesOrderId);
        for (SalesOrderLine line : lines) {
            if (line.getQtyOrdered().compareTo(line.getQtyAllocated()) > 0) {
                return variantRepository.findById(line.getVariantId())
                        .filter(v -> tenantId.equals(v.getTenantId()))
                        .map(ProductVariant::getSku)
                        .orElse(null);
            }
        }
        return null;
    }
}
