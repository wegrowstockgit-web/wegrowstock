package com.invsys.service;

import com.invsys.api.dto.BomLineResponse;
import com.invsys.api.dto.BomResponse;
import com.invsys.api.dto.ProductionOrderResponse;
import com.invsys.domain.Bom;
import com.invsys.domain.BomLine;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.domain.ProductMedia;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.ProductionOrder;
import com.invsys.repository.BomLineRepository;
import com.invsys.modules.catalog.repository.ProductMediaRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ManufacturingDtoMapper {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final BomLineRepository bomLineRepository;
    private final ProductMediaRepository productMediaRepository;

    public ManufacturingDtoMapper(ProductVariantRepository variantRepository,
                                  ProductRepository productRepository,
                                  BomLineRepository bomLineRepository,
                                  ProductMediaRepository productMediaRepository) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.bomLineRepository = bomLineRepository;
        this.productMediaRepository = productMediaRepository;
    }

    public BomResponse toBomResponse(Bom bom) {
        VariantInfo parent = resolveVariant(bom.getParentVariantId());
        List<BomLineResponse> lines = bomLineRepository.findByBomId(bom.getId()).stream()
                .map(this::toBomLineResponse)
                .toList();
        return new BomResponse(
                bom.getId(),
                bom.getParentVariantId(),
                parent.sku(),
                parent.name(),
                bom.getName(),
                bom.isActive(),
                bom.isAutoAssemble(),
                lines,
                bom.getCreatedAt()
        );
    }

    public BomLineResponse toBomLineResponse(BomLine line) {
        VariantInfo component = resolveVariant(line.getComponentVariantId());
        return new BomLineResponse(
                line.getId(),
                line.getComponentVariantId(),
                component.sku(),
                component.name(),
                line.getQuantityRequired()
        );
    }

    public ProductionOrderResponse toProductionOrderResponse(ProductionOrder order) {
        VariantInfo parent = resolveVariant(order.getParentVariantId());
        String mediaUrl = productMediaRepository
                .findFirstByTenantIdAndVariantIdAndPrimaryTrue(order.getTenantId(), order.getParentVariantId())
                .map(ProductMedia::getUrl)
                .orElse(null);
        return new ProductionOrderResponse(
                order.getId(),
                order.getNumber(),
                order.getParentVariantId(),
                parent.sku(),
                parent.name(),
                order.getQtyTarget(),
                order.getQtyProduced(),
                order.getStatus(),
                order.getCreatedAt(),
                mediaUrl
        );
    }

    private VariantInfo resolveVariant(UUID variantId) {
        return variantRepository.findById(variantId)
                .map(variant -> new VariantInfo(variant.getSku(), resolveProductName(variant)))
                .orElse(new VariantInfo(null, null));
    }

    private String resolveProductName(ProductVariant variant) {
        return productRepository.findById(variant.getProductId())
                .map(Product::getName)
                .orElse(variant.getSku());
    }

    private record VariantInfo(String sku, String name) {
    }
}
