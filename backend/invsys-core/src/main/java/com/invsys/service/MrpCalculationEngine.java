package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MrpCalculationEngine {

    private static final Set<String> EXCLUDED_SO_STATUSES = Set.of("CANCELLED", "SHIPPED", "CLOSED");
    private static final List<String> OPEN_PO_STATUSES = List.of(
            "DRAFT", "SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED", "APPROVED", "OPEN");

    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final SupplierRepository supplierRepository;
    private final TenantRepository tenantRepository;

    @Value("${invsys.mrp.enabled:false}")
    private boolean mrpEnabled;

    public MrpCalculationEngine(ProductVariantRepository variantRepository,
                                InventoryLevelRepository levelRepository,
                                SalesOrderRepository salesOrderRepository,
                                SalesOrderLineRepository salesOrderLineRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                PurchaseOrderLineRepository purchaseOrderLineRepository,
                                SupplierRepository supplierRepository,
                                TenantRepository tenantRepository) {
        this.variantRepository = variantRepository;
        this.levelRepository = levelRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.supplierRepository = supplierRepository;
        this.tenantRepository = tenantRepository;
    }

    @Scheduled(cron = "${invsys.mrp.cron:0 0 6 * * *}")
    public void calculateAndCreateDraftPosScheduled() {
        if (!mrpEnabled) {
            return;
        }
        for (UUID tenantId : tenantRepository.findAll().stream().map(t -> t.getId()).toList()) {
            TenantContext.setTenantId(tenantId);
            try {
                calculateAndCreateDraftPos();
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Transactional
    public MrpRunResult calculateAndCreateDraftPos() {
        List<MrpSuggestion> suggestions = calculateSuggestions();
        List<MrpSuggestion> shortVariants = suggestions.stream()
                .filter(s -> s.suggestedOrderQty().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (shortVariants.isEmpty()) {
            return new MrpRunResult(List.of(), suggestions);
        }

        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, Supplier> suppliersById = supplierRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(Supplier::getId, s -> s, (a, b) -> a));

        Map<UUID, List<MrpSuggestion>> bySupplier = new LinkedHashMap<>();
        for (MrpSuggestion suggestion : shortVariants) {
            UUID supplierId = suggestion.defaultSupplierId();
            if (supplierId == null) {
                supplierId = supplierRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                        .findFirst()
                        .map(Supplier::getId)
                        .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_SUPPLIER",
                                "No supplier configured for MRP"));
            }
            bySupplier.computeIfAbsent(supplierId, ignored -> new ArrayList<>()).add(suggestion);
        }

        List<PurchaseOrder> created = new ArrayList<>();
        long stamp = Instant.now().toEpochMilli();
        int index = 0;
        for (Map.Entry<UUID, List<MrpSuggestion>> entry : bySupplier.entrySet()) {
            Supplier supplier = suppliersById.get(entry.getKey());
            PurchaseOrder po = new PurchaseOrder();
            po.setTenantId(tenantId);
            po.setSupplierId(entry.getKey());
            po.setNumber("PO-MRP-" + stamp + (index > 0 ? "-" + index : ""));
            po.setStatus("DRAFT");
            if (supplier != null && supplier.getDefaultLeadTimeDays() != null) {
                po.setExpectedAt(Instant.now().plus(supplier.getDefaultLeadTimeDays(), ChronoUnit.DAYS));
            }
            po = purchaseOrderRepository.save(po);

            for (MrpSuggestion suggestion : entry.getValue()) {
                PurchaseOrderLine line = new PurchaseOrderLine();
                line.setTenantId(tenantId);
                line.setPurchaseOrderId(po.getId());
                line.setVariantId(suggestion.variantId());
                line.setQtyOrdered(suggestion.suggestedOrderQty());
                line.setUnitCost(suggestion.unitCost());
                purchaseOrderLineRepository.save(line);
            }
            created.add(po);
            index++;
        }
        return new MrpRunResult(created, suggestions);
    }

    @Transactional(readOnly = true)
    public List<MrpSuggestion> calculateSuggestions() {
        UUID tenantId = TenantContext.requireTenantId();

        Map<UUID, BigDecimal> openSalesByVariant = openSalesDemandByVariant(tenantId);
        Map<UUID, BigDecimal> onHandByVariant = new HashMap<>();
        Map<UUID, BigDecimal> allocatedByVariant = new HashMap<>();
        for (InventoryLevel level : levelRepository.findAll()) {
            if (!tenantId.equals(level.getTenantId())) {
                continue;
            }
            onHandByVariant.merge(level.getVariantId(), level.getOnHand(), BigDecimal::add);
            allocatedByVariant.merge(level.getVariantId(), level.getAllocated(), BigDecimal::add);
        }
        Map<UUID, BigDecimal> inboundPoByVariant = inboundOpenPoByVariant(tenantId);

        Map<UUID, Supplier> suppliersById = supplierRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(Supplier::getId, s -> s, (a, b) -> a));

        List<MrpSuggestion> suggestions = new ArrayList<>();
        for (ProductVariant variant : variantRepository.findAll()) {
            if (!tenantId.equals(variant.getTenantId())) {
                continue;
            }
            BigDecimal openSo = openSalesByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO);
            BigDecimal safetyStock = variant.getSafetyStock() != null ? variant.getSafetyStock() : BigDecimal.ZERO;
            BigDecimal onHand = onHandByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO);
            BigDecimal allocated = allocatedByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO);
            BigDecimal inbound = inboundPoByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO);

            BigDecimal available = onHand.subtract(allocated).add(inbound);
            BigDecimal netRequirement = openSo.add(safetyStock).subtract(available);

            BigDecimal suggestedQty = BigDecimal.ZERO;
            if (netRequirement.compareTo(BigDecimal.ZERO) > 0) {
                suggestedQty = applyMoq(netRequirement, resolveSupplier(variant, suppliersById));
            }

            BigDecimal unitCost = variant.getAvgCost() != null ? variant.getAvgCost() : BigDecimal.ZERO;
            BigDecimal capitalEstimate = suggestedQty.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
            Supplier supplier = variant.getDefaultSupplierId() != null
                    ? suppliersById.get(variant.getDefaultSupplierId()) : null;
            int leadTimeDays = supplier != null && supplier.getDefaultLeadTimeDays() != null
                    ? supplier.getDefaultLeadTimeDays()
                    : variant.getSupplierLeadTimeDays();

            suggestions.add(new MrpSuggestion(
                    variant.getId(),
                    variant.getSku(),
                    openSo,
                    safetyStock,
                    onHand,
                    allocated,
                    inbound,
                    netRequirement.max(BigDecimal.ZERO),
                    suggestedQty,
                    variant.getDefaultSupplierId(),
                    supplier != null ? supplier.getName() : null,
                    leadTimeDays,
                    unitCost,
                    capitalEstimate));
        }
        suggestions.sort((a, b) -> b.netRequirement().compareTo(a.netRequirement()));
        return suggestions;
    }

    private Map<UUID, BigDecimal> openSalesDemandByVariant(UUID tenantId) {
        Map<UUID, SalesOrder> ordersById = salesOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(o -> !EXCLUDED_SO_STATUSES.contains(normalizeStatus(o.getStatus())))
                .collect(java.util.stream.Collectors.toMap(SalesOrder::getId, o -> o, (a, b) -> a));

        Map<UUID, BigDecimal> demand = new HashMap<>();
        for (SalesOrderLine line : salesOrderLineRepository.findAll()) {
            if (!tenantId.equals(line.getTenantId()) || !ordersById.containsKey(line.getSalesOrderId())) {
                continue;
            }
            BigDecimal open = line.getQtyOrdered().subtract(line.getQtyShipped()).max(BigDecimal.ZERO);
            if (open.signum() <= 0) {
                BigDecimal unallocated = line.getQtyOrdered()
                        .subtract(line.getQtyAllocated().max(line.getQtyShipped()))
                        .max(BigDecimal.ZERO);
                open = unallocated;
            }
            if (open.signum() > 0) {
                demand.merge(line.getVariantId(), open, BigDecimal::add);
            }
        }
        return demand;
    }

    private Map<UUID, BigDecimal> inboundOpenPoByVariant(UUID tenantId) {
        List<UUID> openPoIds = purchaseOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(po -> OPEN_PO_STATUSES.contains(normalizeStatus(po.getStatus())))
                .map(PurchaseOrder::getId)
                .toList();
        Map<UUID, BigDecimal> incoming = new HashMap<>();
        for (PurchaseOrderLine line : purchaseOrderLineRepository.findAll()) {
            if (!tenantId.equals(line.getTenantId()) || !openPoIds.contains(line.getPurchaseOrderId())) {
                continue;
            }
            BigDecimal remaining = line.getQtyOrdered().subtract(line.getQtyReceived()).max(BigDecimal.ZERO);
            incoming.merge(line.getVariantId(), remaining, BigDecimal::add);
        }
        return incoming;
    }

    private static BigDecimal applyMoq(BigDecimal qty, Supplier supplier) {
        if (supplier == null || supplier.getMinimumOrderQuantityValue() == null) {
            return qty.setScale(4, RoundingMode.CEILING);
        }
        BigDecimal moq = supplier.getMinimumOrderQuantityValue();
        if (moq.compareTo(BigDecimal.ZERO) <= 0) {
            return qty.setScale(4, RoundingMode.CEILING);
        }
        if (qty.compareTo(moq) < 0) {
            return moq.setScale(4, RoundingMode.UNNECESSARY);
        }
        return qty.setScale(4, RoundingMode.CEILING);
    }

    private static Supplier resolveSupplier(ProductVariant variant, Map<UUID, Supplier> suppliersById) {
        if (variant.getDefaultSupplierId() == null) {
            return null;
        }
        return suppliersById.get(variant.getDefaultSupplierId());
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    public record MrpSuggestion(
            UUID variantId,
            String sku,
            BigDecimal openSalesQty,
            BigDecimal safetyStock,
            BigDecimal onHand,
            BigDecimal allocated,
            BigDecimal inboundOpenPoQty,
            BigDecimal netRequirement,
            BigDecimal suggestedOrderQty,
            UUID defaultSupplierId,
            String defaultSupplierName,
            int leadTimeDays,
            BigDecimal unitCost,
            BigDecimal capitalEstimate
    ) {
    }

    public record MrpRunResult(List<PurchaseOrder> createdPurchaseOrders, List<MrpSuggestion> suggestions) {
    }
}
