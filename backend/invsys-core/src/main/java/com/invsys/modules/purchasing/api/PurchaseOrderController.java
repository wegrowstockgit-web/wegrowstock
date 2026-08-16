package com.invsys.modules.purchasing.api;

import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.modules.purchasing.service.PurchaseOrderService;
import com.invsys.service.SupplierPortalService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PurchaseOrderController {

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final SupplierPortalService supplierPortalService;

    public PurchaseOrderController(SupplierRepository supplierRepository,
                                   PurchaseOrderRepository purchaseOrderRepository,
                                   PurchaseOrderLineRepository lineRepository,
                                   PurchaseOrderService purchaseOrderService,
                                   SupplierPortalService supplierPortalService) {
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.purchaseOrderService = purchaseOrderService;
        this.supplierPortalService = supplierPortalService;
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public List<Supplier> suppliers() {
        return supplierRepository.findByTenantIdOrderByNameAsc(TenantContext.requireTenantId());
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public Supplier createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        Supplier supplier = new Supplier();
        supplier.setTenantId(TenantContext.requireTenantId());
        supplier.setName(request.name());
        supplier.setContact(request.contact() != null ? request.contact() : Map.of());
        if (request.paymentTerms() != null && !request.paymentTerms().isBlank()) {
            supplier.setPaymentTerms(normalizePaymentTerms(request.paymentTerms()));
        }
        supplier.setTaxId(request.taxId() != null ? request.taxId() : request.businessRegistration());
        supplier.setBusinessRegistration(request.businessRegistration());
        if (request.bankAccountIban() != null && !request.bankAccountIban().isBlank()) {
            supplier.setBankAccountIban(maskSecret(request.bankAccountIban().trim()));
        }
        if (request.routingNumber() != null && !request.routingNumber().isBlank()) {
            supplier.setBankRoutingNumber(maskSecret(request.routingNumber().trim()));
        }
        supplier.setDefaultLeadTimeDays(request.defaultLeadTimeDays());
        supplier.setMinimumOrderQuantityValue(request.minimumOrderQuantityValue());
        supplier.setSupplierRating(request.supplierRating());
        if (request.defaultCurrency() != null && !request.defaultCurrency().isBlank()) {
            supplier.setDefaultCurrency(request.defaultCurrency().trim().toUpperCase());
        }
        return supplierRepository.save(supplier);
    }

    private static String normalizePaymentTerms(String raw) {
        String key = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return switch (key) {
            case "NET30", "NET_30", "N30" -> "NET30";
            case "NET60", "NET_60", "N60" -> "NET60";
            case "DUE_ON_RECEIPT", "DUEONRECEIPT", "COD", "DUE" -> "DUE_ON_RECEIPT";
            default -> throw new com.invsys.core.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "VALIDATION",
                    "paymentTerms must be NET30, NET60, or DUE_ON_RECEIPT");
        };
    }

    /** Persist only a masked form — never store full bank credentials in clear text. */
    private static String maskSecret(String value) {
        String compact = value.replaceAll("\\s+", "");
        if (compact.length() <= 4) {
            return "****";
        }
        return "****" + compact.substring(compact.length() - 4);
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public List<PurchaseOrderResponse> listPurchaseOrders() {
        Map<UUID, String> supplierNames = supplierRepository
                .findByTenantIdOrderByNameAsc(TenantContext.requireTenantId()).stream()
                .collect(java.util.stream.Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));
        return purchaseOrderRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId()).stream()
                .map(po -> new PurchaseOrderResponse(
                        po.getId(),
                        po.getNumber(),
                        supplierNames.getOrDefault(po.getSupplierId(), "—"),
                        po.getStatus(),
                        po.getExpectedAt()))
                .toList();
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public PurchaseOrder createPurchaseOrder(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(TenantContext.requireTenantId());
        po.setSupplierId(request.supplierId());
        po.setNumber(request.number());
        po.setStatus("DRAFT");
        if (request.destinationLocationId() != null) {
            po.setDestinationLocationId(request.destinationLocationId());
        }
        if (request.freightAmount() != null) {
            po.setFreightAmount(request.freightAmount());
        }
        if (request.dutiesAmount() != null) {
            po.setDutiesAmount(request.dutiesAmount());
        }
        po = purchaseOrderRepository.save(po);
        for (CreateLineRequest line : request.lines()) {
            PurchaseOrderLine pol = new PurchaseOrderLine();
            pol.setTenantId(TenantContext.requireTenantId());
            pol.setPurchaseOrderId(po.getId());
            pol.setVariantId(line.variantId());
            pol.setQtyOrdered(line.qtyOrdered());
            pol.setUnitCost(line.unitCost() != null ? line.unitCost() : BigDecimal.ZERO);
            lineRepository.save(pol);
        }
        return po;
    }

    @PostMapping("/purchase-orders/{id}/submit")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public PurchaseOrder submit(@PathVariable UUID id) {
        return purchaseOrderService.submit(id);
    }

    @PostMapping("/purchase-orders/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public PurchaseOrder confirm(@PathVariable UUID id) {
        return purchaseOrderService.confirmOrder(id);
    }

    @PostMapping("/purchase-orders/{id}/mark-in-transit")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public PurchaseOrder markInTransit(@PathVariable UUID id) {
        return purchaseOrderService.markInTransit(id);
    }

    @PostMapping("/purchase-orders/{id}/send-magic-link")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public SupplierPortalService.MagicLinkResponse sendMagicLink(@PathVariable UUID id) {
        return supplierPortalService.sendMagicLink(id);
    }

    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER','PICKER')")
    public PurchaseOrderDetailResponse getPurchaseOrder(@PathVariable UUID id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new com.invsys.core.common.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
        if (!po.getTenantId().equals(TenantContext.requireTenantId())) {
            throw new com.invsys.core.common.ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found");
        }
        String supplierName = supplierRepository.findById(po.getSupplierId())
                .map(Supplier::getName).orElse("—");
        List<PurchaseOrderLineDetail> lines = lineRepository.findByPurchaseOrderId(id).stream()
                .map(l -> new PurchaseOrderLineDetail(
                        l.getId(), l.getVariantId(), l.getQtyOrdered(), l.getQtyReceived(), l.getUnitCost()))
                .toList();
        return new PurchaseOrderDetailResponse(
                po.getId(), po.getNumber(), supplierName, po.getStatus(), po.getExpectedAt(),
                po.getDestinationLocationId(), po.getFreightAmount(), po.getDutiesAmount(), po.getNotes(), lines);
    }

    @PostMapping("/purchase-orders/lines/{lineId}/receive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public PurchaseOrderLine receive(@PathVariable UUID lineId, @Valid @RequestBody ReceiveLineRequest request) {
        return purchaseOrderService.receiveLine(
                lineId, request.locationId(), request.lotId(), request.quantity(), request.landedCostSurcharge());
    }

    public record CreateSupplierRequest(
            @NotBlank String name,
            Map<String, Object> contact,
            String paymentTerms,
            String taxId,
            String businessRegistration,
            String bankAccountIban,
            String routingNumber,
            Integer defaultLeadTimeDays,
            BigDecimal minimumOrderQuantityValue,
            BigDecimal supplierRating,
            String defaultCurrency
    ) {
    }

    public record CreatePurchaseOrderRequest(
            @NotNull UUID supplierId,
            @NotBlank String number,
            UUID destinationLocationId,
            BigDecimal freightAmount,
            BigDecimal dutiesAmount,
            List<CreateLineRequest> lines
    ) {
    }

    public record CreateLineRequest(@NotNull UUID variantId, @NotNull BigDecimal qtyOrdered, BigDecimal unitCost) {
    }

    public record ReceiveLineRequest(
            @NotNull UUID locationId,
            UUID lotId,
            @NotNull BigDecimal quantity,
            BigDecimal landedCostSurcharge
    ) {
    }

    public record PurchaseOrderResponse(
            UUID id,
            String number,
            String supplierName,
            String status,
            java.time.Instant expectedAt
    ) {
    }

    public record PurchaseOrderLineDetail(
            UUID id,
            UUID variantId,
            BigDecimal qtyOrdered,
            BigDecimal qtyReceived,
            BigDecimal unitCost
    ) {
    }

    public record PurchaseOrderDetailResponse(
            UUID id,
            String number,
            String supplierName,
            String status,
            java.time.Instant expectedAt,
            UUID destinationLocationId,
            BigDecimal freightAmount,
            BigDecimal dutiesAmount,
            String notes,
            List<PurchaseOrderLineDetail> lines
    ) {
    }
}
