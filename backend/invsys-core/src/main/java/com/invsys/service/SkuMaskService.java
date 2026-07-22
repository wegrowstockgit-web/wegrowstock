package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.TenantSettings;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Alphanumeric sequence mask engine for SKU / barcode auto-minting.
 * Templates use tokens: {PREFIX}, {YYYY}, {ID:N}, {seq:N}.
 */
@Service
public class SkuMaskService {

    private static final Pattern ID_PATTERN = Pattern.compile("\\{(?:ID|seq):(\\d+)\\}");

    private final DocumentSequenceService sequenceService;
    private final TenantSettingsRepository settingsRepository;
    private final ProductVariantRepository variantRepository;

    public SkuMaskService(DocumentSequenceService sequenceService,
                          TenantSettingsRepository settingsRepository,
                          ProductVariantRepository variantRepository) {
        this.sequenceService = sequenceService;
        this.settingsRepository = settingsRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional
    public String mintSku(String explicitSku, String variantLevelTemplate) {
        if (explicitSku != null && !explicitSku.isBlank()) {
            return ensureUnique(explicitSku.trim());
        }
        Map<String, Object> settings = settingsMap();
        String template = firstNonBlank(variantLevelTemplate,
                stringSetting(settings, "sku_template"),
                "SKU-{PREFIX}-{ID:5}");
        String prefix = stringSetting(settings, "sku_prefix");
        if (prefix == null || prefix.isBlank()) {
            prefix = "INV";
        }
        // Normalize {ID:N} → {seq:N} for DocumentSequenceService.
        String format = template.replace("{PREFIX}", prefix);
        format = ID_PATTERN.matcher(format).replaceAll(m -> "{seq:" + m.group(1) + "}");
        String minted = sequenceService.nextNumber("SKU", format);
        return ensureUnique(minted);
    }

    @Transactional
    public String mintBarcode(String explicitBarcode) {
        if (explicitBarcode != null && !explicitBarcode.isBlank()) {
            return explicitBarcode.trim();
        }
        Map<String, Object> settings = settingsMap();
        String template = firstNonBlank(stringSetting(settings, "barcode_template"), "BC-{ID:8}");
        String prefix = stringSetting(settings, "sku_prefix");
        if (prefix == null) {
            prefix = "";
        }
        String format = template.replace("{PREFIX}", prefix);
        format = ID_PATTERN.matcher(format).replaceAll(m -> "{seq:" + m.group(1) + "}");
        return sequenceService.nextNumber("BARCODE", format);
    }

    private String ensureUnique(String sku) {
        UUID tenantId = TenantContext.requireTenantId();
        if (variantRepository.findByTenantIdAndSku(tenantId, sku).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "SKU_COLLISION",
                    "SKU already exists: " + sku);
        }
        return sku;
    }

    private Map<String, Object> settingsMap() {
        return settingsRepository.findByTenantId(TenantContext.requireTenantId())
                .map(TenantSettings::getSettings)
                .orElseGet(() -> TenantSettings.withDefaults(TenantContext.requireTenantId()).getSettings());
    }

    private static String stringSetting(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
