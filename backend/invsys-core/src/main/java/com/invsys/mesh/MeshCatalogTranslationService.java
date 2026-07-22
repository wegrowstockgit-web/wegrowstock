package com.invsys.mesh;

import com.invsys.core.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves buyer → seller variant links via {@code external_references}
 * where {@code system = MESH_NETWORK}.
 */
@Service
public class MeshCatalogTranslationService {

    public static final String MESH_NETWORK = "MESH_NETWORK";
    public static final String ENTITY_VARIANT = "PRODUCT_VARIANT";

    private final DSLContext dsl;

    public MeshCatalogTranslationService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Look up the seller target for a buyer variant. {@code external_id} may be:
     * <ul>
     *   <li>{@code partnerTenantId:sellerVariantUuid}</li>
     *   <li>plain seller variant UUID</li>
     *   <li>seller SKU string</li>
     * </ul>
     */
    public Optional<String> findMappedExternalId(UUID buyerTenantId, UUID buyerVariantId, UUID partnerTenantId) {
        UUID tenantId = buyerTenantId != null ? buyerTenantId : TenantContext.requireTenantId();
        Record row = dsl.fetchOne("""
                SELECT external_id
                FROM external_references
                WHERE tenant_id = ?
                  AND system = ?
                  AND entity_type = ?
                  AND entity_id = ?
                ORDER BY
                  CASE
                    WHEN external_id LIKE ? THEN 0
                    ELSE 1
                  END,
                  updated_at DESC
                LIMIT 1
                """,
                tenantId,
                MESH_NETWORK,
                ENTITY_VARIANT,
                buyerVariantId,
                partnerTenantId + ":%");
        if (row == null || row.get("external_id") == null) {
            return Optional.empty();
        }
        return Optional.of(row.get("external_id", String.class).trim());
    }

    public static String encodePartnerVariant(UUID partnerTenantId, UUID sellerVariantId) {
        return partnerTenantId + ":" + sellerVariantId;
    }

    public static Optional<UUID> parseSellerVariantId(String externalId, UUID partnerTenantId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = externalId.trim();
        String prefix = partnerTenantId + ":";
        if (trimmed.startsWith(prefix)) {
            try {
                return Optional.of(UUID.fromString(trimmed.substring(prefix.length())));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(UUID.fromString(trimmed));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
