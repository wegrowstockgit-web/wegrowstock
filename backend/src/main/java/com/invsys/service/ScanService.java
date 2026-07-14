package com.invsys.service;

import com.invsys.api.dto.ScanLookupResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Location;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScanService {

    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final LocationRepository locationRepository;

    public ScanService(ProductVariantRepository variantRepository,
                       InventoryLevelRepository levelRepository,
                       LocationRepository locationRepository) {
        this.variantRepository = variantRepository;
        this.levelRepository = levelRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public ScanLookupResponse lookup(String barcode) {
        ProductVariant variant = variantRepository.findByTenantIdAndBarcode(TenantContext.requireTenantId(), barcode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Barcode not found"));
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(TenantContext.requireTenantId(), variant.getId());
        String path = resolvePutawayPath(variant);
        return new ScanLookupResponse(variant, levels, variant.getDefaultLocationId(), path);
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
