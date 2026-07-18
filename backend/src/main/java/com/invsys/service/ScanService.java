package com.invsys.service;

import com.invsys.api.dto.ScanLookupResponse;
import com.invsys.api.dto.SerialScanResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Location;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Barcode scan hot-path. Variant resolution uses a single jOOQ fetch with a
 * LEFT JOIN to primary {@code product_media} for floor thumbnail rendering.
 * DSCSA: when GTIN miss but AI 21 serial is present, fall back to serial_numbers.
 */
@Service
public class ScanService {

    private final DSLContext dsl;
    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final LocationRepository locationRepository;
    private final SerialScanQueryService serialScanQueryService;

    public ScanService(DSLContext dsl,
                       ProductVariantRepository variantRepository,
                       InventoryLevelRepository levelRepository,
                       LocationRepository locationRepository,
                       SerialScanQueryService serialScanQueryService) {
        this.dsl = dsl;
        this.variantRepository = variantRepository;
        this.levelRepository = levelRepository;
        this.locationRepository = locationRepository;
        this.serialScanQueryService = serialScanQueryService;
    }

    @Transactional(readOnly = true)
    public ScanLookupResponse lookup(String barcode) {
        UUID tenantId = TenantContext.requireTenantId();
        Optional<Gs1BarcodeParser.Gs1Elements> gs1 = Gs1BarcodeParser.parse(barcode);
        String lookupKey = gs1.map(e -> e.gtin() != null ? e.gtin() : barcode.trim()).orElse(barcode.trim());

        Optional<ScanHit> hit = resolveVariantWithMedia(tenantId, lookupKey)
                .or(() -> resolveVariantWithMedia(tenantId, barcode.trim()));

        // DSCSA package-level identity: resolve by AI 21 when GTIN catalog miss
        if (hit.isEmpty() && gs1.isPresent() && gs1.get().serial() != null && !gs1.get().serial().isBlank()) {
            SerialScanResponse serialHit = serialScanQueryService.lookup(gs1.get().serial());
            if (serialHit != null && serialHit.variantId() != null) {
                hit = Optional.of(new ScanHit(serialHit.variantId(), primaryMediaUrl(serialHit.variantId())));
            }
        }

        ScanHit resolved = hit.orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Barcode not found"));

        ProductVariant variant = variantRepository.findById(resolved.variantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Barcode not found"));

        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(tenantId, variant.getId());
        String path = resolvePutawayPath(variant);
        if (gs1.isPresent()) {
            Gs1BarcodeParser.Gs1Elements elements = gs1.get();
            return ScanLookupResponse.of(
                    variant,
                    levels,
                    variant.getDefaultLocationId(),
                    path,
                    elements.gtin(),
                    elements.lot(),
                    elements.expiry(),
                    elements.serial(),
                    elements.variableQuantity(),
                    elements.all(),
                    resolved.primaryMediaUrl());
        }
        return ScanLookupResponse.of(
                variant, levels, variant.getDefaultLocationId(), path,
                null, null, null, null, null, Map.of(), resolved.primaryMediaUrl());
    }

    /**
     * Single-roundtrip barcode/SKU/alt-barcode resolve with primary media join.
     */
    Optional<ScanHit> resolveVariantWithMedia(UUID tenantId, String key) {
        Result<Record> rows = dsl.fetch("""
                SELECT pv.id AS variant_id, pm.url AS primary_media_url
                FROM product_variants pv
                LEFT JOIN product_media pm
                  ON pm.variant_id = pv.id
                 AND pm.tenant_id = pv.tenant_id
                 AND pm.is_primary = true
                WHERE pv.tenant_id = ?
                  AND (
                    pv.barcode = ?
                    OR pv.sku = ?
                    OR EXISTS (
                        SELECT 1 FROM variant_barcodes vb
                        WHERE vb.tenant_id = pv.tenant_id
                          AND vb.variant_id = pv.id
                          AND vb.barcode = ?
                    )
                  )
                LIMIT 1
                """, tenantId, key, key, key);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Record row = rows.getFirst();
        UUID variantId = row.get("variant_id", UUID.class);
        String mediaUrl = row.get("primary_media_url", String.class);
        return Optional.of(new ScanHit(variantId, mediaUrl));
    }

    public String resolvePutawayPath(ProductVariant variant) {
        if (variant.getDefaultLocationId() == null) {
            return null;
        }
        return locationRepository.findById(variant.getDefaultLocationId())
                .map(Location::getPath)
                .orElse(null);
    }

    public String primaryMediaUrl(UUID variantId) {
        UUID tenantId = TenantContext.requireTenantId();
        Result<Record> rows = dsl.fetch("""
                SELECT url FROM product_media
                WHERE tenant_id = ? AND variant_id = ? AND is_primary = true
                LIMIT 1
                """, tenantId, variantId);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.getFirst().get("url", String.class);
    }

    record ScanHit(UUID variantId, String primaryMediaUrl) {
    }
}
