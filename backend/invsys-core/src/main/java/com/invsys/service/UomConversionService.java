package com.invsys.service;

import com.invsys.domain.VariantUomConversion;
import com.invsys.repository.VariantUomConversionRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class UomConversionService {

    private final VariantUomConversionRepository conversionRepository;

    public UomConversionService(VariantUomConversionRepository conversionRepository) {
        this.conversionRepository = conversionRepository;
    }

    public List<VariantUomConversion> listForVariant(UUID variantId) {
        return conversionRepository.findByTenantIdAndVariantId(TenantContext.requireTenantId(), variantId);
    }

    @Transactional
    public List<VariantUomConversion> saveForVariant(UUID variantId, List<UomConversionRequest> conversions) {
        UUID tenantId = TenantContext.requireTenantId();
        List<VariantUomConversion> existing = conversionRepository.findByTenantIdAndVariantId(tenantId, variantId);
        conversionRepository.deleteAll(existing);

        for (UomConversionRequest req : conversions) {
            VariantUomConversion conversion = new VariantUomConversion();
            conversion.setTenantId(tenantId);
            conversion.setVariantId(variantId);
            conversion.setUomType(req.uomType());
            conversion.setUnitName(req.unitName());
            conversion.setConversionRatio(req.conversionRatio());
            conversionRepository.save(conversion);
        }
        return conversionRepository.findByTenantIdAndVariantId(tenantId, variantId);
    }

    public BigDecimal toStandardQuantity(UUID variantId, BigDecimal quantity, String uomType) {
        return conversionRepository.findByTenantIdAndVariantIdAndUomType(
                        TenantContext.requireTenantId(), variantId, uomType)
                .map(c -> quantity.multiply(c.getConversionRatio()))
                .orElse(quantity);
    }

    public record UomConversionRequest(String uomType, String unitName, BigDecimal conversionRatio) {
    }
}
