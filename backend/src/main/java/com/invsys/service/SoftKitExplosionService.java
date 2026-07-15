package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SoftKitComponent;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SoftKitComponentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Explodes soft-kit variants into raw component sales-order lines.
 */
@Service
public class SoftKitExplosionService {

    private final ProductVariantRepository variantRepository;
    private final SoftKitComponentRepository softKitComponentRepository;

    public SoftKitExplosionService(ProductVariantRepository variantRepository,
                                   SoftKitComponentRepository softKitComponentRepository) {
        this.variantRepository = variantRepository;
        this.softKitComponentRepository = softKitComponentRepository;
    }

    /**
     * @param attachPriceToFirst when true, kit unit price is kept on the first component line
     *                           (portal/direct SO totals); when false, component prices are zero
     *                           (channel imports that price the parent elsewhere)
     * @param failIfEmpty        when true, empty soft-kit BOM raises; when false, returns empty list
     */
    public List<ExplodedLine> explode(UUID tenantId,
                                      UUID variantId,
                                      BigDecimal quantity,
                                      BigDecimal unitPrice,
                                      boolean attachPriceToFirst,
                                      boolean failIfEmpty) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
        if (!tenantId.equals(variant.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found");
        }
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        if (!variant.isSoftKit()) {
            return List.of(new ExplodedLine(variant.getId(), quantity, price));
        }

        List<SoftKitComponent> components = softKitComponentRepository
                .findByTenantIdAndParentKitIdOrderByCreatedAtAsc(tenantId, variant.getId());
        if (components.isEmpty()) {
            if (failIfEmpty) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SOFT_KIT_EMPTY",
                        "Soft kit has no components configured");
            }
            return List.of();
        }

        List<ExplodedLine> lines = new ArrayList<>(components.size());
        boolean priced = false;
        for (SoftKitComponent component : components) {
            BigDecimal linePrice = BigDecimal.ZERO;
            if (attachPriceToFirst && !priced) {
                linePrice = price;
                priced = true;
            }
            lines.add(new ExplodedLine(
                    component.getComponentId(),
                    quantity.multiply(component.getQuantity()),
                    linePrice));
        }
        return lines;
    }

    public record ExplodedLine(UUID variantId, BigDecimal quantity, BigDecimal unitPrice) {
    }
}
