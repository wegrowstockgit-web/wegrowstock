package com.invsys.service;

import com.invsys.api.dto.VariantListItemResponse;
import com.invsys.common.PageResponse;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VariantCatalogService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final InventoryLevelRepository levelRepository;

    public VariantCatalogService(ProductVariantRepository variantRepository,
                                 ProductRepository productRepository,
                                 InventoryLevelRepository levelRepository) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.levelRepository = levelRepository;
    }

    public PageResponse<VariantListItemResponse> list(String query, String cursor, int limit) {
        TenantContext.requireTenantId();
        int pageSize = Math.min(Math.max(limit, 1), 200);

        Map<UUID, String> productNames = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));

        Map<UUID, BigDecimal> onHandByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getOnHand,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        Map<UUID, BigDecimal> allocatedByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getAllocated,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        String q = query != null ? query.trim().toLowerCase() : "";

        List<VariantListItemResponse> all = variantRepository.findAll().stream()
                .sorted(Comparator.comparing(ProductVariant::getSku))
                .map(variant -> toItem(variant, productNames, onHandByVariant, allocatedByVariant))
                .filter(item -> matchesQuery(item, q))
                .toList();

        int startIndex = 0;
        if (cursor != null && !cursor.isBlank()) {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().toString().equals(cursor)) {
                    startIndex = i + 1;
                    break;
                }
            }
        }

        int endIndex = Math.min(startIndex + pageSize, all.size());
        List<VariantListItemResponse> page = all.subList(startIndex, endIndex);
        String nextCursor = endIndex < all.size() ? page.get(page.size() - 1).id().toString() : null;

        return new PageResponse<>(page, nextCursor, nextCursor != null);
    }

    private boolean matchesQuery(VariantListItemResponse item, String q) {
        if (q.isEmpty()) {
            return true;
        }
        return item.sku().toLowerCase().contains(q)
                || item.name().toLowerCase().contains(q)
                || (item.barcode() != null && item.barcode().toLowerCase().contains(q));
    }

    private VariantListItemResponse toItem(
            ProductVariant variant,
            Map<UUID, String> productNames,
            Map<UUID, BigDecimal> onHandByVariant,
            Map<UUID, BigDecimal> allocatedByVariant) {
        BigDecimal onHand = onHandByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO);
        BigDecimal allocated = allocatedByVariant.getOrDefault(variant.getId(), BigDecimal.ZERO);
        return new VariantListItemResponse(
                variant.getId(),
                variant.getSku(),
                productNames.getOrDefault(variant.getProductId(), variant.getSku()),
                variant.getBarcode(),
                onHand,
                allocated,
                onHand.subtract(allocated),
                variant.getPrice(),
                variant.getCurrency(),
                variant.isExternalSyncEnabled(),
                variant.getWeight(),
                variant.getWeightUnit(),
                variant.getDefaultSupplierId(),
                variant.getSupplierLeadTimeDays(),
                variant.getReorderPoint(),
                variant.getReorderQty());
    }
}
