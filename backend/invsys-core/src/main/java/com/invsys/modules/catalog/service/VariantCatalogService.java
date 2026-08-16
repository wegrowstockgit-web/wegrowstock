package com.invsys.modules.catalog.service;

import com.invsys.api.dto.VariantListItemResponse;
import com.invsys.core.common.PageResponse;
import com.invsys.modules.catalog.api.VariantStockView;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.domain.ProductMedia;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.ProductMediaRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
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
    private final VariantStockView variantStockView;
    private final ProductMediaRepository productMediaRepository;

    public VariantCatalogService(ProductVariantRepository variantRepository,
                                 ProductRepository productRepository,
                                 VariantStockView variantStockView,
                                 ProductMediaRepository productMediaRepository) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.variantStockView = variantStockView;
        this.productMediaRepository = productMediaRepository;
    }

    public PageResponse<VariantListItemResponse> list(String query, String cursor, int limit) {
        UUID tenantId = TenantContext.requireTenantId();
        int pageSize = Math.min(Math.max(limit, 1), 200);

        Map<UUID, String> productNames = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));

        VariantStockView.StockTotals stock = variantStockView.totals();
        Map<UUID, BigDecimal> onHandByVariant = stock.onHandByVariant();
        Map<UUID, BigDecimal> allocatedByVariant = stock.allocatedByVariant();

        String q = query != null ? query.trim().toLowerCase() : "";

        Map<UUID, String> primaryMediaByVariant = productMediaRepository
                .findByTenantIdAndVariantIdInAndPrimaryTrue(
                        tenantId,
                        variantRepository.findAll().stream().map(ProductVariant::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProductMedia::getVariantId, ProductMedia::getUrl, (a, b) -> a));

        List<VariantListItemResponse> all = variantRepository.findAll().stream()
                .sorted(Comparator.comparing(ProductVariant::getSku))
                .map(variant -> toItem(variant, productNames, onHandByVariant, allocatedByVariant, primaryMediaByVariant))
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
            Map<UUID, BigDecimal> allocatedByVariant,
            Map<UUID, String> primaryMediaByVariant) {
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
                variant.getLength(),
                variant.getWidth(),
                variant.getHeight(),
                variant.getDimUnit(),
                variant.getDefaultSupplierId(),
                variant.getSupplierLeadTimeDays(),
                variant.getReorderPoint(),
                variant.getReorderQty(),
                primaryMediaByVariant.get(variant.getId()),
                variant.isLotTracked(),
                variant.getHsTariffCode(),
                variant.getCountryOfOrigin(),
                variant.isHazmat(),
                variant.getPalletTie(),
                variant.getPalletHigh(),
                variant.getStorageTempZone(),
                variant.isFragile(),
                variant.getAbcClassification(),
                variant.getLifecycleStatus());
    }
}
