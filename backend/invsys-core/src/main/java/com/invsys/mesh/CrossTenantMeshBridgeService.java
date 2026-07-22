package com.invsys.mesh;

import com.invsys.core.common.ApiException;
import com.invsys.domain.ExternalReference;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.repository.ExternalReferenceRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.service.DocumentSequenceService;
import com.invsys.service.PlatformAlertService;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Asynchronous cross-tenant mesh bridge with catalog translation:
 * mapped lines → CONFIRMED SO; unmapped lines → DRAFT exception SO + seller alert.
 */
@Service
public class CrossTenantMeshBridgeService {

    public static final String MESH_SYSTEM = "MESH";
    public static final String ENTITY_PO = "PURCHASE_ORDER";
    public static final String ENTITY_SO = "SALES_ORDER";
    public static final String CHANNEL_MESH = "MESH";
    public static final String CHANNEL_MESH_EXCEPTION = "MESH_EXCEPTION";
    public static final String ALERT_MESH_CATALOG = "MESH_CATALOG_MAPPING_REQUIRED";

    private static final Logger log = LoggerFactory.getLogger(CrossTenantMeshBridgeService.class);

    private final BootstrapJdbc bootstrapJdbc;
    private final MeshCatalogTranslationService catalogTranslation;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final DocumentSequenceService documentSequenceService;
    private final PlatformAlertService platformAlertService;
    private final TransactionTemplate requiresNew;

    public CrossTenantMeshBridgeService(BootstrapJdbc bootstrapJdbc,
                                        MeshCatalogTranslationService catalogTranslation,
                                        PurchaseOrderRepository purchaseOrderRepository,
                                        PurchaseOrderLineRepository purchaseOrderLineRepository,
                                        SalesOrderRepository salesOrderRepository,
                                        SalesOrderLineRepository salesOrderLineRepository,
                                        ProductVariantRepository productVariantRepository,
                                        ProductRepository productRepository,
                                        ExternalReferenceRepository externalReferenceRepository,
                                        DocumentSequenceService documentSequenceService,
                                        PlatformAlertService platformAlertService,
                                        PlatformTransactionManager transactionManager) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.catalogTranslation = catalogTranslation;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
        this.externalReferenceRepository = externalReferenceRepository;
        this.documentSequenceService = documentSequenceService;
        this.platformAlertService = platformAlertService;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void onPurchaseOrderSubmitted(UUID buyerTenantId, UUID purchaseOrderId, Map<String, Object> payload) {
        PurchaseOrderSnapshot snapshot = loadBuyerPo(buyerTenantId, purchaseOrderId);
        Optional<BootstrapJdbc.MeshPartnerRow> mesh = bootstrapJdbc.findConnectedMeshByBuyerSupplier(
                buyerTenantId, snapshot.supplierId());
        if (mesh.isEmpty()) {
            log.debug("No CONNECTED mesh partner for buyer={} supplier={}", buyerTenantId, snapshot.supplierId());
            return;
        }
        BootstrapJdbc.MeshPartnerRow partner = mesh.get();

        Optional<ExternalReference> existing = findBuyerPoLink(buyerTenantId, purchaseOrderId);
        if (existing.isPresent()) {
            log.info("Mesh SO already linked for PO {} — skipping", purchaseOrderId);
            return;
        }

        TranslatedLines translated = translateLines(buyerTenantId, partner.partnerTenantId(), snapshot.lines());
        UUID primarySoId = null;

        if (!translated.mapped().isEmpty()) {
            primarySoId = createPartnerSalesOrder(partner, snapshot, translated.mapped(), CHANNEL_MESH, "CONFIRMED");
        }
        if (!translated.unmapped().isEmpty()) {
            UUID exceptionSoId = createExceptionSalesOrder(partner, snapshot, translated.unmapped());
            if (primarySoId == null) {
                primarySoId = exceptionSoId;
            }
        }

        if (primarySoId != null) {
            persistBuyerLink(buyerTenantId, purchaseOrderId, partner, primarySoId);
            log.info("Mesh bridged PO {} → seller={} primarySO={} mapped={} unmapped={}",
                    purchaseOrderId, partner.partnerTenantId(), primarySoId,
                    translated.mapped().size(), translated.unmapped().size());
        }
    }

    public void onSalesOrderShipped(UUID sellerTenantId, UUID salesOrderId, Map<String, Object> payload) {
        SalesOrder sellerOrder = loadSellerOrder(sellerTenantId, salesOrderId);
        if (CHANNEL_MESH_EXCEPTION.equals(sellerOrder.getChannel())) {
            return;
        }
        Optional<BootstrapJdbc.MeshPartnerRow> mesh = bootstrapJdbc.findConnectedMeshBySellerCustomer(
                sellerTenantId, sellerOrder.getCustomerId());
        if (mesh.isEmpty()) {
            return;
        }
        BootstrapJdbc.MeshPartnerRow partner = mesh.get();

        Optional<ExternalReference> soLink = findSellerSoLink(sellerTenantId, salesOrderId);
        if (soLink.isEmpty()) {
            log.debug("SO {} has no MESH external reference — not a mesh order", salesOrderId);
            return;
        }
        UUID buyerPoId = parsePeerId(soLink.get().getExternalId());
        Map<String, Object> tracking = buildTrackingEntry(payload, salesOrderId, sellerOrder.getNumber());

        markBuyerPoInTransit(partner.tenantId(), buyerPoId, tracking);
        log.info("Mesh ship update SO {} → buyer PO {} IN_TRANSIT", salesOrderId, buyerPoId);
    }

    private TranslatedLines translateLines(UUID buyerTenantId, UUID partnerTenantId, List<LineSnapshot> lines) {
        List<MappedLine> mapped = new ArrayList<>();
        List<LineSnapshot> unmapped = new ArrayList<>();
        for (LineSnapshot line : lines) {
            Optional<String> externalId = catalogTranslation.findMappedExternalId(
                    buyerTenantId, line.buyerVariantId(), partnerTenantId);
            if (externalId.isEmpty()) {
                // Legacy fallback: identical SKU in seller catalog (only when no MESH_NETWORK row)
                unmapped.add(line);
                continue;
            }
            Optional<UUID> sellerVariantId = MeshCatalogTranslationService.parseSellerVariantId(
                    externalId.get(), partnerTenantId);
            if (sellerVariantId.isPresent()) {
                mapped.add(new MappedLine(line, sellerVariantId.get(), null));
                continue;
            }
            // external_id treated as seller SKU
            mapped.add(new MappedLine(line, null, externalId.get()));
        }
        return new TranslatedLines(mapped, unmapped);
    }

    private PurchaseOrderSnapshot loadBuyerPo(UUID buyerTenantId, UUID purchaseOrderId) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            return requiresNew.execute(status -> {
                TenantContext.setTenantId(buyerTenantId);
                PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "PO not found"));
                List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrderId);
                List<LineSnapshot> lineSnapshots = new ArrayList<>();
                for (PurchaseOrderLine line : lines) {
                    ProductVariant variant = productVariantRepository.findById(line.getVariantId())
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
                    lineSnapshots.add(new LineSnapshot(
                            line.getVariantId(),
                            variant.getSku(),
                            line.getQtyOrdered(),
                            line.getUnitCost()));
                }
                return new PurchaseOrderSnapshot(
                        po.getId(),
                        po.getNumber(),
                        po.getSupplierId(),
                        po.getExpectedAt(),
                        lineSnapshots);
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private UUID createPartnerSalesOrder(BootstrapJdbc.MeshPartnerRow partner,
                                         PurchaseOrderSnapshot snapshot,
                                         List<MappedLine> lines,
                                         String channel,
                                         String status) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            return requiresNew.execute(statusTx -> {
                TenantContext.setTenantId(partner.partnerTenantId());

                SalesOrder order = new SalesOrder();
                order.setTenantId(partner.partnerTenantId());
                order.setCustomerId(partner.customerId());
                order.setNumber(documentSequenceService.nextNumber("SO", "SO-MESH-{YYYY}-{seq:5}"));
                order.setStatus(status);
                order.setChannel(channel);
                order.setCustomerPoNumber(snapshot.number());
                order.setRequestedShipDate(snapshot.expectedAt());
                order = salesOrderRepository.save(order);

                for (MappedLine mapped : lines) {
                    ProductVariant sellerVariant = resolveSellerVariant(partner.partnerTenantId(), mapped);
                    SalesOrderLine soLine = new SalesOrderLine();
                    soLine.setTenantId(partner.partnerTenantId());
                    soLine.setSalesOrderId(order.getId());
                    soLine.setVariantId(sellerVariant.getId());
                    soLine.setQtyOrdered(mapped.line().qtyOrdered());
                    soLine.setUnitPrice(mapped.line().unitCost());
                    Map<String, Object> tax = new LinkedHashMap<>();
                    tax.put("meshBuyerVariantId", mapped.line().buyerVariantId().toString());
                    tax.put("meshBuyerSku", mapped.line().sku());
                    soLine.setTax(tax);
                    salesOrderLineRepository.save(soLine);
                }

                if (CHANNEL_MESH.equals(channel)) {
                    ExternalReference ref = new ExternalReference();
                    ref.setTenantId(partner.partnerTenantId());
                    ref.setEntityType(ENTITY_SO);
                    ref.setEntityId(order.getId());
                    ref.setSystem(MESH_SYSTEM);
                    ref.setExternalId(partner.tenantId() + ":" + snapshot.purchaseOrderId());
                    externalReferenceRepository.save(ref);
                }
                return order.getId();
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private UUID createExceptionSalesOrder(BootstrapJdbc.MeshPartnerRow partner,
                                           PurchaseOrderSnapshot snapshot,
                                           List<LineSnapshot> unmapped) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            return requiresNew.execute(statusTx -> {
                TenantContext.setTenantId(partner.partnerTenantId());

                SalesOrder order = new SalesOrder();
                order.setTenantId(partner.partnerTenantId());
                order.setCustomerId(partner.customerId());
                order.setNumber(documentSequenceService.nextNumber("SO", "SO-MESH-EX-{YYYY}-{seq:5}"));
                order.setStatus("DRAFT");
                order.setChannel(CHANNEL_MESH_EXCEPTION);
                order.setCustomerPoNumber(snapshot.number() + "-EXCEPTION");
                order.setRequestedShipDate(snapshot.expectedAt());
                order = salesOrderRepository.save(order);

                List<String> skus = new ArrayList<>();
                for (LineSnapshot line : unmapped) {
                    ProductVariant pending = findOrCreatePendingVariant(partner.partnerTenantId(), line.sku());
                    SalesOrderLine soLine = new SalesOrderLine();
                    soLine.setTenantId(partner.partnerTenantId());
                    soLine.setSalesOrderId(order.getId());
                    soLine.setVariantId(pending.getId());
                    soLine.setQtyOrdered(line.qtyOrdered());
                    soLine.setUnitPrice(line.unitCost());
                    Map<String, Object> tax = new LinkedHashMap<>();
                    tax.put("meshUnmapped", true);
                    tax.put("meshBuyerVariantId", line.buyerVariantId().toString());
                    tax.put("meshBuyerSku", line.sku());
                    soLine.setTax(tax);
                    salesOrderLineRepository.save(soLine);
                    skus.add(line.sku());
                }

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("exceptionSalesOrderId", order.getId().toString());
                details.put("buyerPurchaseOrderNumber", snapshot.number());
                details.put("buyerTenantId", partner.tenantId().toString());
                details.put("unmappedSkus", skus);
                details.put("message", "New mesh items require catalog mapping.");
                platformAlertService.raise(
                        ALERT_MESH_CATALOG,
                        "WARNING",
                        "MESH_NETWORK",
                        "New mesh items require catalog mapping.",
                        details);

                return order.getId();
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private ProductVariant resolveSellerVariant(UUID sellerTenantId, MappedLine mapped) {
        if (mapped.sellerVariantId() != null) {
            return productVariantRepository.findById(mapped.sellerVariantId())
                    .filter(v -> sellerTenantId.equals(v.getTenantId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MESH_VARIANT_MISSING",
                            "Mapped seller variant not found: " + mapped.sellerVariantId()));
        }
        return productVariantRepository.findByTenantIdAndSku(sellerTenantId, mapped.sellerSku())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MESH_SKU_MISSING",
                        "Seller catalog missing SKU " + mapped.sellerSku()));
    }

    private ProductVariant findOrCreatePendingVariant(UUID sellerTenantId, String buyerSku) {
        String pendingSku = "MESH-PENDING-" + buyerSku;
                return productVariantRepository.findByTenantIdAndSku(sellerTenantId, pendingSku)
                .orElseGet(() -> {
                    Product product = productRepository.findByTenantIdAndSkuRoot(sellerTenantId, "MESH-PENDING")
                            .orElseGet(() -> {
                                Product created = new Product();
                                created.setTenantId(sellerTenantId);
                                created.setSkuRoot("MESH-PENDING");
                                created.setName("Mesh pending catalog items");
                                created.setDescription("Auto-created stubs for unmapped mesh PO lines");
                                return productRepository.save(created);
                            });
                    ProductVariant variant = new ProductVariant();
                    variant.setTenantId(sellerTenantId);
                    variant.setProductId(product.getId());
                    variant.setSku(pendingSku);
                    return productVariantRepository.save(variant);
                });
    }

    private void persistBuyerLink(UUID buyerTenantId, UUID purchaseOrderId,
                                  BootstrapJdbc.MeshPartnerRow partner, UUID sellerSoId) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            requiresNew.executeWithoutResult(status -> {
                TenantContext.setTenantId(buyerTenantId);
                ExternalReference ref = new ExternalReference();
                ref.setTenantId(buyerTenantId);
                ref.setEntityType(ENTITY_PO);
                ref.setEntityId(purchaseOrderId);
                ref.setSystem(MESH_SYSTEM);
                ref.setExternalId(partner.partnerTenantId() + ":" + sellerSoId);
                externalReferenceRepository.save(ref);
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private SalesOrder loadSellerOrder(UUID sellerTenantId, UUID salesOrderId) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            return requiresNew.execute(status -> {
                TenantContext.setTenantId(sellerTenantId);
                return salesOrderRepository.findById(salesOrderId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sales order not found"));
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private void markBuyerPoInTransit(UUID buyerTenantId, UUID purchaseOrderId, Map<String, Object> tracking) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            requiresNew.executeWithoutResult(status -> {
                TenantContext.setTenantId(buyerTenantId);
                PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "PO not found"));
                if (!"SUBMITTED".equals(po.getStatus()) && !"IN_TRANSIT".equals(po.getStatus())) {
                    log.warn("Mesh ship skipped PO {} status={}", purchaseOrderId, po.getStatus());
                    return;
                }
                List<Map<String, Object>> metadata = new ArrayList<>(
                        po.getTrackingMetadata() != null ? po.getTrackingMetadata() : List.of());
                metadata.add(tracking);
                po.setTrackingMetadata(metadata);
                if ("SUBMITTED".equals(po.getStatus())) {
                    po.setStatus("IN_TRANSIT");
                }
                purchaseOrderRepository.save(po);
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private Optional<ExternalReference> findBuyerPoLink(UUID buyerTenantId, UUID purchaseOrderId) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            return requiresNew.execute(status -> {
                TenantContext.setTenantId(buyerTenantId);
                return externalReferenceRepository.findByTenantIdAndSystemAndEntityTypeAndEntityId(
                        buyerTenantId, MESH_SYSTEM, ENTITY_PO, purchaseOrderId);
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private Optional<ExternalReference> findSellerSoLink(UUID sellerTenantId, UUID salesOrderId) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            return requiresNew.execute(status -> {
                TenantContext.setTenantId(sellerTenantId);
                return externalReferenceRepository.findByTenantIdAndSystemAndEntityTypeAndEntityId(
                        sellerTenantId, MESH_SYSTEM, ENTITY_SO, salesOrderId);
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private static UUID parsePeerId(String externalId) {
        if (externalId == null || !externalId.contains(":")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MESH_REF_INVALID",
                    "Invalid MESH external_id");
        }
        String[] parts = externalId.split(":", 2);
        return UUID.fromString(parts[1]);
    }

    private static Map<String, Object> buildTrackingEntry(Map<String, Object> payload,
                                                          UUID salesOrderId,
                                                          String soNumber) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("source", "MESH");
        entry.put("salesOrderId", salesOrderId.toString());
        entry.put("salesOrderNumber", soNumber);
        if (payload != null) {
            if (payload.get("shipmentId") != null) {
                entry.put("shipmentId", String.valueOf(payload.get("shipmentId")));
            }
            if (payload.get("carrier") != null) {
                entry.put("carrier", String.valueOf(payload.get("carrier")));
            }
            if (payload.get("trackingNumber") != null) {
                entry.put("trackingNumber", String.valueOf(payload.get("trackingNumber")));
            }
        }
        entry.put("recordedAt", java.time.Instant.now().toString());
        return entry;
    }

    private static void restoreTenant(UUID previous) {
        if (previous != null) {
            TenantContext.setTenantId(previous);
        } else {
            TenantContext.clear();
        }
    }

    private record PurchaseOrderSnapshot(
            UUID purchaseOrderId,
            String number,
            UUID supplierId,
            java.time.Instant expectedAt,
            List<LineSnapshot> lines) {
    }

    private record LineSnapshot(
            UUID buyerVariantId,
            String sku,
            java.math.BigDecimal qtyOrdered,
            java.math.BigDecimal unitCost) {
    }

    private record MappedLine(LineSnapshot line, UUID sellerVariantId, String sellerSku) {
    }

    private record TranslatedLines(List<MappedLine> mapped, List<LineSnapshot> unmapped) {
    }
}
