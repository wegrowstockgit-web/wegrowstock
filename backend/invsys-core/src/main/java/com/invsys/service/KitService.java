package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.Bom;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.repository.BomLineRepository;
import com.invsys.repository.BomRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class KitService {

    private final ProductVariantRepository variantRepository;
    private final BomLineRepository bomLineRepository;
    private final BomRepository bomRepository;

    public KitService(ProductVariantRepository variantRepository,
                      BomLineRepository bomLineRepository,
                      BomRepository bomRepository) {
        this.variantRepository = variantRepository;
        this.bomLineRepository = bomLineRepository;
        this.bomRepository = bomRepository;
    }

    public boolean isKit(UUID variantId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (variantRepository.findById(variantId).map(ProductVariant::isKit).orElse(false)) {
            return true;
        }
        return bomRepository.findByTenantIdAndParentVariantId(tenantId, variantId)
                .map(b -> b.isActive() && b.isAutoAssemble())
                .orElse(false);
    }

    public List<BomComponent> explodeComponents(UUID parentVariantId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<Object[]> rows = bomLineRepository.explodeBom(tenantId, parentVariantId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "KIT_NO_BOM",
                    "Kit variant has no active bill of materials");
        }
        List<BomComponent> components = new ArrayList<>();
        for (Object[] row : rows) {
            components.add(new BomComponent(toUuid(row[0]), toDecimal(row[1])));
        }
        return components;
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    public record BomComponent(UUID variantId, BigDecimal quantityPerParent) {
    }
}
