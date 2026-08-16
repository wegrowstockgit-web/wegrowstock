package com.invsys.mesh;

import com.invsys.core.common.ApiException;
import com.invsys.domain.ExternalReference;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.domain.SalesOrderStatus;
import com.invsys.repository.ExternalReferenceRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
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
    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONNECTED = "CONNECTED";

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
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
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
                                        SupplierRepository supplierRepository,
                                        CustomerRepository customerRepository,
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
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.documentSequenceService = documentSequenceService;
        this.platformAlertService = platformAlertService;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public BootstrapJdbc.MeshPartnerRow requestConnection(UUID partnerTenantId, UUID variantId) {
        UUID buyer = TenantContext.requireTenantId();
        UUID seller = resolveSellerTenant(partnerTenantId, variantId);
        if (buyer.equals(seller)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESH_SELF_CONNECT",
                    "Cannot request a mesh connection with your own tenant");
        }
        Optional<BootstrapJdbc.MeshPartnerRow> existing = bootstrapJdbc.findMeshByBuyerAndSeller(buyer, seller);
        if (existing.isPresent()) {
            String status = existing.get().connectionStatus();
            if (STATUS_CONNECTED.equals(status) || STATUS_REQUESTED.equals(status) || STATUS_PENDING.equals(status)) {
                if (STATUS_CONNECTED.equals(status)) {
                    throw new ApiException(HttpStatus.CONFLICT, "MESH_ALREADY_CONNECTED",
                            "A mesh connection already exists with this partner");
                }
                return existing.get();
            }
        }
        UUID id = bootstrapJdbc.upsertMeshPartner(buyer, seller, null, null, STATUS_REQUESTED);
        return bootstrapJdbc.findMeshPartnerById(id).orElseThrow(() ->
                new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MESH_REQUEST_FAILED",
                        "Mesh connection request could not be stored"));
    }

    public BootstrapJdbc.MeshPartnerRow approveConnection(UUID meshPartnerId) {
        UUID seller = TenantContext.requireTenantId();
        BootstrapJdbc.MeshPartnerRow row = bootstrapJdbc.findMeshPartnerById(meshPartnerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Mesh connection not found"));
        if (!seller.equals(row.partnerTenantId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MESH_APPROVE_FORBIDDEN",
                    "Only the selling partner can approve this connection");
        }
        if (STATUS_CONNECTED.equals(row.connectionStatus())) {
            return row;
        }
        if (!STATUS_REQUESTED.equals(row.connectionStatus()) && !STATUS_PENDING.equals(row.connectionStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only REQUESTED or PENDING connections can be approved");
        }

        String sellerName = bootstrapJdbc.findTenantNameSlugStatus(row.partnerTenantId())
                .map(BootstrapJdbc.TenantNameSlugStatusRow::name)
                .orElse("Mesh partner");
        String buyerName = bootstrapJdbc.findTenantNameSlugStatus(row.tenantId())
                .map(BootstrapJdbc.TenantNameSlugStatusRow::name)
                .orElse("Mesh buyer");

        UUID supplierId = row.supplierId() != null
                ? row.supplierId()
                : createSupplierInBuyer(row.tenantId(), sellerName);
        UUID customerId = row.customerId() != null
                ? row.customerId()
                : createCustomerInSeller(row.partnerTenantId(), buyerName);

        UUID id = bootstrapJdbc.upsertMeshPartner(
                row.tenantId(), row.partnerTenantId(), supplierId, customerId, STATUS_CONNECTED);
        return bootstrapJdbc.findMeshPartnerById(id).orElseThrow(() ->
                new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MESH_APPROVE_FAILED",
                        "Mesh connection could not be approved"));
    }

    public void onPurchaseOrderSubmitted(UUID buyerTenantId, UUID purchaseOrderId, Map<String, Object> payload) {
        bridgePurchaseOrder(buyerTenantId, purchaseOrderId, "CONFIRMED", false);
    }

    /**
     * Synchronous confirm path: mapped lines become an UNALLOCATED seller SO and the PO
     * is annotated with the partner sales-order number.
     */
    public String confirmMeshPurchaseOrder(UUID buyerTenantId, UUID purchaseOrderId) {
        return bridgePurchaseOrder(buyerTenantId, purchaseOrderId, SalesOrderStatus.UNALLOCATED.name(), false);
    }

    private String bridgePurchaseOrder(UUID buyerTenantId, UUID purchaseOrderId,
                                       String mappedStatus, boolean appendNote) {
        PurchaseOrderSnapshot snapshot = loadBuyerPo(buyerTenantId, purchaseOrderId);
        Optional<BootstrapJdbc.MeshPartnerRow> mesh = bootstrapJdbc.findConnectedMeshByBuyerSupplier(
                buyerTenantId, snapshot.supplierId());
        if (mesh.isEmpty()) {
            log.debug("No CONNECTED mesh partner for buyer={} supplier={}", buyerTenantId, snapshot.supplierId());
            return null;
        }
        BootstrapJdbc.MeshPartnerRow partner = mesh.get();

        Optional<ExternalReference> existing = findBuyerPoLink(buyerTenantId, purchaseOrderId);
        if (existing.isPresent()) {
            log.info("Mesh SO already linked for PO {} — skipping", purchaseOrderId);
            if (appendNote) {
                String soNumber = lookupSellerSoNumber(existing.get());
                if (soNumber != null) {
                    appendPoNote(buyerTenantId, purchaseOrderId, soNumber);
                }
                return soNumber;
            }
            return null;
        }

        TranslatedLines translated = translateLines(buyerTenantId, partner.partnerTenantId(), snapshot.lines());
        CreatedSalesOrder primary = null;

        if (!translated.mapped().isEmpty()) {
            primary = createPartnerSalesOrder(partner, snapshot, translated.mapped(), CHANNEL_MESH, mappedStatus);
        }
        if (!translated.unmapped().isEmpty()) {
            CreatedSalesOrder exception = createExceptionSalesOrder(partner, snapshot, translated.unmapped());
            if (primary == null) {
                primary = exception;
            }
        }

        if (primary != null) {
            persistBuyerLink(buyerTenantId, purchaseOrderId, partner, primary.id());
            if (appendNote) {
                appendPoNote(buyerTenantId, purchaseOrderId, primary.number());
            }
            log.info("Mesh bridged PO {} → seller={} primarySO={} mapped={} unmapped={}",
                    purchaseOrderId, partner.partnerTenantId(), primary.id(),
                    translated.mapped().size(), translated.unmapped().size());
            return primary.number();
        }
        return null;
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

    private CreatedSalesOrder createPartnerSalesOrder(BootstrapJdbc.MeshPartnerRow partner,
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
                return new CreatedSalesOrder(order.getId(), order.getNumber());
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private CreatedSalesOrder createExceptionSalesOrder(BootstrapJdbc.MeshPartnerRow partner,
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

                return new CreatedSalesOrder(order.getId(), order.getNumber());
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

    private UUID resolveSellerTenant(UUID partnerTenantId, UUID variantId) {
        if (partnerTenantId != null) {
            return partnerTenantId;
        }
        if (variantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "partnerTenantId or variantId is required");
        }
        return bootstrapJdbc.findPublishedListingByVariant(variantId)
                .map(BootstrapJdbc.PublishedMeshListingRow::sellerTenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MESH_LISTING_NOT_FOUND",
                        "Published mesh listing not found for variant"));
    }

    private UUID createSupplierInBuyer(UUID buyerTenantId, String sellerName) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            return requiresNew.execute(status -> {
                TenantContext.setTenantId(buyerTenantId);
                Supplier supplier = new Supplier();
                supplier.setTenantId(buyerTenantId);
                supplier.setName(sellerName);
                return supplierRepository.save(supplier).getId();
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private UUID createCustomerInSeller(UUID sellerTenantId, String buyerName) {
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            return requiresNew.execute(status -> {
                TenantContext.setTenantId(sellerTenantId);
                Customer customer = new Customer();
                customer.setTenantId(sellerTenantId);
                customer.setName(buyerName);
                customer.setCustomerStatus("ACTIVE");
                return customerRepository.save(customer).getId();
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private void appendPoNote(UUID buyerTenantId, UUID purchaseOrderId, String soNumber) {
        if (soNumber == null || soNumber.isBlank()) {
            return;
        }
        String note = "Linked to Mesh Partner Sales Order #" + soNumber;
        UUID previous = TenantContext.getTenantId().orElse(null);
        try {
            requiresNew.executeWithoutResult(status -> {
                TenantContext.setTenantId(buyerTenantId);
                PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "PO not found"));
                String existing = po.getNotes();
                if (existing != null && existing.contains(note)) {
                    return;
                }
                po.setNotes(existing == null || existing.isBlank() ? note : existing + "\n" + note);
                purchaseOrderRepository.save(po);
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private String lookupSellerSoNumber(ExternalReference buyerLink) {
        try {
            UUID sellerSoId = parsePeerId(buyerLink.getExternalId());
            String[] parts = buyerLink.getExternalId().split(":", 2);
            UUID sellerTenantId = UUID.fromString(parts[0]);
            UUID previous = TenantContext.getTenantId().orElse(null);
            try {
                return requiresNew.execute(status -> {
                    TenantContext.setTenantId(sellerTenantId);
                    return salesOrderRepository.findById(sellerSoId)
                            .map(SalesOrder::getNumber)
                            .orElse(null);
                });
            } finally {
                restoreTenant(previous);
            }
        } catch (RuntimeException ex) {
            log.debug("Could not resolve seller SO number from mesh link {}", buyerLink.getExternalId());
            return null;
        }
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

    private record CreatedSalesOrder(UUID id, String number) {
    }
}
