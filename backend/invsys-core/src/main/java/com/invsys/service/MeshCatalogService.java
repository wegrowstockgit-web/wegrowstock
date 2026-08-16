package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.BinReplenishmentRule;
import com.invsys.domain.ExternalReference;
import com.invsys.domain.MeshCatalogListing;
import com.invsys.mesh.CrossTenantMeshBridgeService;
import com.invsys.mesh.MeshCatalogTranslationService;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.repository.BinReplenishmentRuleRepository;
import com.invsys.repository.ExternalReferenceRepository;
import com.invsys.repository.MeshCatalogListingRepository;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MeshCatalogService {

    private final BootstrapJdbc bootstrapJdbc;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final MeshCatalogListingRepository listingRepository;
    private final BinReplenishmentRuleRepository binReplenishmentRuleRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final EntityManager entityManager;

    public MeshCatalogService(BootstrapJdbc bootstrapJdbc,
                              ProductVariantRepository productVariantRepository,
                              ProductRepository productRepository,
                              ExternalReferenceRepository externalReferenceRepository,
                              MeshCatalogListingRepository listingRepository,
                              BinReplenishmentRuleRepository binReplenishmentRuleRepository,
                              InventoryLevelRepository inventoryLevelRepository,
                              EntityManager entityManager) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
        this.externalReferenceRepository = externalReferenceRepository;
        this.listingRepository = listingRepository;
        this.binReplenishmentRuleRepository = binReplenishmentRuleRepository;
        this.inventoryLevelRepository = inventoryLevelRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<DiscoverListing> discoverPublished() {
        UUID me = TenantContext.requireTenantId();
        return bootstrapJdbc.listPublishedMeshListings(me).stream()
                .map(row -> new DiscoverListing(
                        row.variantId(),
                        row.productName(),
                        row.imageUrl(),
                        row.sellerName(),
                        row.sellerTenantId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NetworkRelationship> listNetwork() {
        UUID me = TenantContext.requireTenantId();
        return bootstrapJdbc.listMeshRelationshipsForTenant(me).stream()
                .map(row -> {
                    boolean seller = me.equals(row.partnerTenantId());
                    String display = seller && CrossTenantMeshBridgeService.STATUS_REQUESTED.equals(row.connectionStatus())
                            ? CrossTenantMeshBridgeService.STATUS_PENDING
                            : row.connectionStatus();
                    return new NetworkRelationship(
                            row.id(),
                            seller ? row.tenantId() : row.partnerTenantId(),
                            row.partnerName(),
                            seller ? "SELLER" : "BUYER",
                            display,
                            row.connectionStatus(),
                            row.supplierId(),
                            row.customerId(),
                            seller && (CrossTenantMeshBridgeService.STATUS_REQUESTED.equals(row.connectionStatus())
                                    || CrossTenantMeshBridgeService.STATUS_PENDING.equals(row.connectionStatus())));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SharedCatalogRow> listSharedCatalog() {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, String> productNames = new LinkedHashMap<>();
        for (Product product : productRepository.findAll()) {
            if (tenantId.equals(product.getTenantId())) {
                productNames.put(product.getId(), product.getName());
            }
        }
        Map<UUID, MeshCatalogListing> listings = new LinkedHashMap<>();
        for (MeshCatalogListing listing : listingRepository.findByTenantId(tenantId)) {
            listings.put(listing.getVariantId(), listing);
        }
        List<SharedCatalogRow> rows = new ArrayList<>();
        for (ProductVariant variant : productVariantRepository.findAll()) {
            if (!tenantId.equals(variant.getTenantId()) || variant.getSku().startsWith("MESH-PENDING-")) {
                continue;
            }
            MeshCatalogListing listing = listings.get(variant.getId());
            rows.add(new SharedCatalogRow(
                    variant.getId(),
                    variant.getSku(),
                    productNames.getOrDefault(variant.getProductId(), variant.getSku()),
                    listing != null && listing.isPublished(),
                    listing != null ? listing.getMeshWholesalePrice() : null));
        }
        rows.sort((a, b) -> a.sku().compareToIgnoreCase(b.sku()));
        return rows;
    }

    @Transactional
    public SharedCatalogRow upsertListing(UUID variantId, boolean published, BigDecimal meshWholesalePrice) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductVariant variant = productVariantRepository.findById(variantId)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
        MeshCatalogListing listing = listingRepository.findByTenantIdAndVariantId(tenantId, variantId)
                .orElseGet(() -> {
                    MeshCatalogListing created = new MeshCatalogListing();
                    created.setTenantId(tenantId);
                    created.setVariantId(variantId);
                    return created;
                });
        listing.setPublished(published);
        listing.setMeshWholesalePrice(meshWholesalePrice);
        listingRepository.save(listing);
        String productName = productRepository.findById(variant.getProductId())
                .map(Product::getName)
                .orElse(variant.getSku());
        return new SharedCatalogRow(variant.getId(), variant.getSku(), productName, published, meshWholesalePrice);
    }

    @Transactional(readOnly = true)
    public List<MeshSourcingSuggestion> getMeshSourcingSuggestions() {
        UUID tenantId = TenantContext.requireTenantId();
        List<BootstrapJdbc.MeshPartnerRow> partners = bootstrapJdbc.listConnectedMeshPartnersForBuyer(tenantId);
        if (partners.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> onHandByLocationVariant = new LinkedHashMap<>();
        for (InventoryLevel level : inventoryLevelRepository.findAll()) {
            if (!tenantId.equals(level.getTenantId())) {
                continue;
            }
            UUID key = locationVariantKey(level.getLocationId(), level.getVariantId());
            onHandByLocationVariant.merge(key, level.getOnHand() != null ? level.getOnHand() : BigDecimal.ZERO, BigDecimal::add);
        }
        Map<UUID, ProductVariant> variants = new LinkedHashMap<>();
        for (ProductVariant variant : productVariantRepository.findAll()) {
            if (tenantId.equals(variant.getTenantId())) {
                variants.put(variant.getId(), variant);
            }
        }
        Map<UUID, String> productNames = new LinkedHashMap<>();
        for (Product product : productRepository.findAll()) {
            if (tenantId.equals(product.getTenantId())) {
                productNames.put(product.getId(), product.getName());
            }
        }
        List<MeshSourcingSuggestion> suggestions = new ArrayList<>();
        for (BinReplenishmentRule rule : binReplenishmentRuleRepository.findByTenantId(tenantId)) {
            BigDecimal threshold = rule.getMinQuantity();
            if (threshold == null) {
                continue;
            }
            BigDecimal quantity = onHandByLocationVariant.getOrDefault(
                    locationVariantKey(rule.getLocationId(), rule.getVariantId()), BigDecimal.ZERO);
            if (quantity.compareTo(threshold) >= 0) {
                continue;
            }
            ProductVariant variant = variants.get(rule.getVariantId());
            if (variant == null) {
                continue;
            }
            for (BootstrapJdbc.MeshPartnerRow partner : partners) {
                Optional<BootstrapJdbc.PartnerCatalogSku> match = bootstrapJdbc.findPublishedPartnerSkuMatch(
                        partner.partnerTenantId(), variant.getSku(), variant.getBarcode());
                if (match.isEmpty()) {
                    continue;
                }
                String partnerName = bootstrapJdbc.findTenantNameSlugStatus(partner.partnerTenantId())
                        .map(BootstrapJdbc.TenantNameSlugStatusRow::name)
                        .orElse("Mesh partner");
                suggestions.add(new MeshSourcingSuggestion(
                        variant.getId(),
                        productNames.getOrDefault(variant.getProductId(), variant.getSku()),
                        variant.getSku(),
                        partner.partnerTenantId(),
                        partnerName,
                        partner.supplierId(),
                        match.get().sku()));
                break;
            }
        }
        return suggestions;
    }

    private static UUID locationVariantKey(UUID locationId, UUID variantId) {
        return UUID.nameUUIDFromBytes((locationId + ":" + variantId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public List<MeshPartnerSummary> listPartners() {
        UUID buyer = TenantContext.requireTenantId();
        return bootstrapJdbc.listConnectedMeshPartnersForBuyer(buyer).stream()
                .map(row -> new MeshPartnerSummary(
                        row.id(),
                        row.partnerTenantId(),
                        row.supplierId(),
                        row.customerId(),
                        row.connectionStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerSkuRow> listPartnerCatalog(UUID partnerTenantId) {
        assertConnectedPartner(partnerTenantId);
        return bootstrapJdbc.listPartnerCatalogSkus(partnerTenantId).stream()
                .map(sku -> new PartnerSkuRow(sku.variantId(), sku.sku(), sku.productName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogMappingRow> listMappings(UUID partnerTenantId) {
        UUID buyer = TenantContext.requireTenantId();
        assertConnectedPartner(partnerTenantId);

        List<ProductVariant> localVariants = productVariantRepository.findAll().stream()
                .filter(v -> buyer.equals(v.getTenantId()))
                .filter(v -> !v.getSku().startsWith("MESH-PENDING-"))
                .sorted((a, b) -> a.getSku().compareToIgnoreCase(b.getSku()))
                .toList();

        Map<UUID, String> mapped = loadMappingExternalIds(buyer, partnerTenantId);
        Map<UUID, String> partnerSkuById = new LinkedHashMap<>();
        for (BootstrapJdbc.PartnerCatalogSku sku : bootstrapJdbc.listPartnerCatalogSkus(partnerTenantId)) {
            partnerSkuById.put(sku.variantId(), sku.sku());
        }
        List<CatalogMappingRow> rows = new ArrayList<>();
        for (ProductVariant local : localVariants) {
            String externalId = mapped.get(local.getId());
            UUID partnerVariantId = MeshCatalogTranslationService
                    .parseSellerVariantId(externalId, partnerTenantId)
                    .orElse(null);
            String partnerSku = null;
            if (partnerVariantId != null) {
                partnerSku = partnerSkuById.get(partnerVariantId);
            } else if (externalId != null && !externalId.contains(":")) {
                partnerSku = externalId;
            }
            rows.add(new CatalogMappingRow(
                    local.getId(),
                    local.getSku(),
                    partnerVariantId,
                    partnerSku,
                    externalId));
        }
        return rows;
    }

    @Transactional
    public List<CatalogMappingRow> upsertMappings(UUID partnerTenantId, List<MappingUpsert> upserts) {
        UUID buyer = TenantContext.requireTenantId();
        assertConnectedPartner(partnerTenantId);
        if (upserts == null) {
            upserts = List.of();
        }
        for (MappingUpsert upsert : upserts) {
            ProductVariant local = productVariantRepository.findById(upsert.localVariantId())
                    .filter(v -> buyer.equals(v.getTenantId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                            "Local variant not found"));
            if (upsert.partnerVariantId() == null) {
                deleteMapping(buyer, local.getId(), partnerTenantId);
                continue;
            }
            boolean partnerHasVariant = bootstrapJdbc.listPartnerCatalogSkus(partnerTenantId).stream()
                    .anyMatch(s -> s.variantId().equals(upsert.partnerVariantId()));
            if (!partnerHasVariant) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PARTNER_VARIANT_UNKNOWN",
                        "Partner catalog does not include variant " + upsert.partnerVariantId());
            }
            String externalId = MeshCatalogTranslationService.encodePartnerVariant(
                    partnerTenantId, upsert.partnerVariantId());
            Optional<ExternalReference> existing = findMappingRow(buyer, local.getId(), partnerTenantId);
            if (existing.isPresent()) {
                ExternalReference ref = existing.get();
                ref.setExternalId(externalId);
                externalReferenceRepository.save(ref);
            } else {
                // Clear any stale non-partner-scoped row for this variant under MESH_NETWORK
                externalReferenceRepository
                        .findByTenantIdAndSystemAndEntityTypeAndEntityId(
                                buyer, MeshCatalogTranslationService.MESH_NETWORK,
                                MeshCatalogTranslationService.ENTITY_VARIANT, local.getId())
                        .ifPresent(externalReferenceRepository::delete);
                ExternalReference ref = new ExternalReference();
                ref.setTenantId(buyer);
                ref.setEntityType(MeshCatalogTranslationService.ENTITY_VARIANT);
                ref.setEntityId(local.getId());
                ref.setSystem(MeshCatalogTranslationService.MESH_NETWORK);
                ref.setExternalId(externalId);
                externalReferenceRepository.save(ref);
            }
        }
        // jOOQ reads bypass the persistence context — flush so listMappings sees new rows.
        entityManager.flush();
        return listMappings(partnerTenantId);
    }

    private void assertConnectedPartner(UUID partnerTenantId) {
        UUID buyer = TenantContext.requireTenantId();
        boolean ok = bootstrapJdbc.listConnectedMeshPartnersForBuyer(buyer).stream()
                .anyMatch(p -> p.partnerTenantId().equals(partnerTenantId));
        if (!ok) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MESH_PARTNER_NOT_FOUND",
                    "No CONNECTED mesh partner for " + partnerTenantId);
        }
    }

    private Map<UUID, String> loadMappingExternalIds(UUID buyerTenantId, UUID partnerTenantId) {
        // Use JPA (same persistence context as upserts) — jOOQ can hit another pool
        // connection and miss uncommitted rows after flush.
        Map<UUID, String> map = new LinkedHashMap<>();
        String partnerPrefix = partnerTenantId + ":";
        for (ExternalReference ref : externalReferenceRepository.findByTenantIdAndSystemAndEntityType(
                buyerTenantId,
                MeshCatalogTranslationService.MESH_NETWORK,
                MeshCatalogTranslationService.ENTITY_VARIANT)) {
            String externalId = ref.getExternalId();
            if (externalId == null) {
                continue;
            }
            boolean forPartner = externalId.startsWith(partnerPrefix)
                    || MeshCatalogTranslationService.parseSellerVariantId(externalId, partnerTenantId).isPresent();
            if (forPartner) {
                map.put(ref.getEntityId(), externalId);
            }
        }
        return map;
    }

    private Optional<ExternalReference> findMappingRow(UUID buyer, UUID localVariantId, UUID partnerTenantId) {
        return externalReferenceRepository
                .findByTenantIdAndSystemAndEntityTypeAndEntityId(
                        buyer, MeshCatalogTranslationService.MESH_NETWORK,
                        MeshCatalogTranslationService.ENTITY_VARIANT, localVariantId)
                .filter(ref -> {
                    String ext = ref.getExternalId();
                    return ext != null && (ext.startsWith(partnerTenantId + ":")
                            || MeshCatalogTranslationService.parseSellerVariantId(ext, partnerTenantId).isPresent());
                });
    }

    private void deleteMapping(UUID buyer, UUID localVariantId, UUID partnerTenantId) {
        findMappingRow(buyer, localVariantId, partnerTenantId)
                .ifPresent(externalReferenceRepository::delete);
    }

    public record MeshPartnerSummary(
            UUID meshPartnerId,
            UUID partnerTenantId,
            UUID supplierId,
            UUID customerId,
            String connectionStatus) {
    }

    public record PartnerSkuRow(UUID variantId, String sku, String productName) {
    }

    public record CatalogMappingRow(
            UUID localVariantId,
            String localSku,
            UUID partnerVariantId,
            String partnerSku,
            String externalId) {
    }

    public record MappingUpsert(UUID localVariantId, UUID partnerVariantId) {
    }

    public record DiscoverListing(
            UUID variantId,
            String productName,
            String imageUrl,
            String sellerName,
            UUID sellerTenantId) {
    }

    public record NetworkRelationship(
            UUID id,
            UUID partnerTenantId,
            String partnerName,
            String role,
            String displayStatus,
            String connectionStatus,
            UUID supplierId,
            UUID customerId,
            boolean canApprove) {
    }

    public record SharedCatalogRow(
            UUID variantId,
            String sku,
            String productName,
            boolean published,
            BigDecimal meshWholesalePrice) {
    }

    public record MeshSourcingSuggestion(
            UUID variantId,
            String productName,
            String sku,
            UUID partnerTenantId,
            String partnerName,
            UUID supplierId,
            String meshPartnerSku) {
    }
}
