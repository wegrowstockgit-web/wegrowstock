package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.ExternalReference;
import com.invsys.domain.ProductVariant;
import com.invsys.mesh.MeshCatalogTranslationService;
import com.invsys.repository.ExternalReferenceRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.BootstrapJdbc;
import com.invsys.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ExternalReferenceRepository externalReferenceRepository;
    private final EntityManager entityManager;

    public MeshCatalogService(BootstrapJdbc bootstrapJdbc,
                              ProductVariantRepository productVariantRepository,
                              ExternalReferenceRepository externalReferenceRepository,
                              EntityManager entityManager) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.productVariantRepository = productVariantRepository;
        this.externalReferenceRepository = externalReferenceRepository;
        this.entityManager = entityManager;
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
}
