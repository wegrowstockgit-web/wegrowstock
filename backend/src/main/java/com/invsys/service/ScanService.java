package com.invsys.service;

import com.invsys.api.dto.ScanLookupResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Location;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.VariantBarcode;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.VariantBarcodeRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScanService {

    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final LocationRepository locationRepository;
    private final VariantBarcodeRepository variantBarcodeRepository;

    public ScanService(ProductVariantRepository variantRepository,
                       InventoryLevelRepository levelRepository,
                       LocationRepository locationRepository,
                       VariantBarcodeRepository variantBarcodeRepository) {
        this.variantRepository = variantRepository;
        this.levelRepository = levelRepository;
        this.locationRepository = locationRepository;
        this.variantBarcodeRepository = variantBarcodeRepository;
    }

    @Transactional(readOnly = true)
    public ScanLookupResponse lookup(String barcode) {
        UUID tenantId = TenantContext.requireTenantId();
        Optional<Gs1BarcodeParser.Gs1Elements> gs1 = Gs1BarcodeParser.parse(barcode);
        String lookupKey = gs1.map(e -> e.gtin() != null ? e.gtin() : barcode.trim()).orElse(barcode.trim());

        ProductVariant variant = resolveVariant(tenantId, lookupKey)
                .or(() -> resolveVariant(tenantId, barcode.trim()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Barcode not found"));

        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(tenantId, variant.getId());
        String path = resolvePutawayPath(variant);
        if (gs1.isPresent()) {
            Gs1BarcodeParser.Gs1Elements elements = gs1.get();
            return new ScanLookupResponse(
                    variant,
                    levels,
                    variant.getDefaultLocationId(),
                    path,
                    elements.gtin(),
                    elements.lot(),
                    elements.expiry(),
                    elements.serial(),
                    elements.all());
        }
        return new ScanLookupResponse(variant, levels, variant.getDefaultLocationId(), path);
    }

    private Optional<ProductVariant> resolveVariant(UUID tenantId, String key) {
        Optional<ProductVariant> direct = variantRepository.findByTenantIdAndBarcode(tenantId, key);
        if (direct.isPresent()) {
            return direct;
        }
        // Also try SKU match for GTIN-style keys stored as SKU
        Optional<ProductVariant> bySku = variantRepository.findByTenantIdAndSku(tenantId, key);
        if (bySku.isPresent()) {
            return bySku;
        }
        return variantBarcodeRepository.findByTenantIdAndBarcode(tenantId, key)
                .flatMap(alt -> variantRepository.findById(alt.getVariantId()));
    }

    public String resolvePutawayPath(ProductVariant variant) {
        if (variant.getDefaultLocationId() == null) {
            return null;
        }
        return locationRepository.findById(variant.getDefaultLocationId())
                .map(Location::getPath)
                .orElse(null);
    }
}
