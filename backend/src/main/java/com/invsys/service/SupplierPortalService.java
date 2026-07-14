package com.invsys.service;

import com.invsys.auth.JwtService;
import com.invsys.common.ApiException;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SupplierPortalService {

    private final JwtService jwtService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final SupplierRepository supplierRepository;
    private final ProductVariantRepository variantRepository;
    private final long tokenExpiryHours;

    public SupplierPortalService(JwtService jwtService,
                                 PurchaseOrderRepository purchaseOrderRepository,
                                 PurchaseOrderLineRepository lineRepository,
                                 SupplierRepository supplierRepository,
                                 ProductVariantRepository variantRepository,
                                 @Value("${invsys.supplier-portal.token-hours:168}") long tokenExpiryHours) {
        this.jwtService = jwtService;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
        this.supplierRepository = supplierRepository;
        this.variantRepository = variantRepository;
        this.tokenExpiryHours = tokenExpiryHours;
    }

    @Transactional
    public MagicLinkResponse sendMagicLink(UUID purchaseOrderId) {
        UUID tenantId = TenantContext.requireTenantId();
        PurchaseOrder po = purchaseOrderRepository.findByTenantIdAndId(tenantId, purchaseOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
        String token = jwtService.generateSupplierPortalToken(tenantId, po.getId(), tokenExpiryHours);
        return new MagicLinkResponse(token, "/supplier-portal/po/" + token);
    }

    public PortalPurchaseOrderView getPurchaseOrder(JwtService.SupplierPortalClaims claims) {
        TenantContext.setTenantId(claims.tenantId());
        try {
            PurchaseOrder po = purchaseOrderRepository.findByTenantIdAndId(claims.tenantId(), claims.purchaseOrderId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
            String supplierName = supplierRepository.findById(po.getSupplierId()).map(Supplier::getName).orElse("—");
            Map<UUID, String> skus = variantRepository.findAll().stream()
                    .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getSku, (a, b) -> a));
            List<PortalLineView> lines = lineRepository.findByPurchaseOrderId(po.getId()).stream()
                    .map(line -> new PortalLineView(
                            line.getId(),
                            line.getVariantId(),
                            skus.getOrDefault(line.getVariantId(), "—"),
                            line.getQtyOrdered(),
                            "PO-" + po.getNumber() + "-" + line.getId().toString().substring(0, 8).toUpperCase()))
                    .toList();
            return new PortalPurchaseOrderView(
                    po.getId(),
                    po.getNumber(),
                    supplierName,
                    po.getStatus(),
                    po.getExpectedAt(),
                    lines);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public PortalPurchaseOrderView updateExpectedDelivery(JwtService.SupplierPortalClaims claims, Instant expectedAt) {
        TenantContext.setTenantId(claims.tenantId());
        try {
            PurchaseOrder po = purchaseOrderRepository.findByTenantIdAndId(claims.tenantId(), claims.purchaseOrderId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
            po.setExpectedAt(expectedAt);
            purchaseOrderRepository.save(po);
            return getPurchaseOrder(claims);
        } finally {
            TenantContext.clear();
        }
    }

    public List<ReceivingLabel> receivingLabels(JwtService.SupplierPortalClaims claims) {
        PortalPurchaseOrderView po = getPurchaseOrder(claims);
        return po.lines().stream()
                .map(line -> new ReceivingLabel(line.barcode(), line.sku(), line.qtyOrdered(), po.number()))
                .toList();
    }

    public record MagicLinkResponse(String token, String path) {
    }

    public record PortalPurchaseOrderView(
            UUID id,
            String number,
            String supplierName,
            String status,
            Instant expectedAt,
            List<PortalLineView> lines
    ) {
    }

    public record PortalLineView(UUID id, UUID variantId, String sku, java.math.BigDecimal qtyOrdered, String barcode) {
    }

    public record ReceivingLabel(String barcode, String sku, java.math.BigDecimal quantity, String poNumber) {
    }
}
