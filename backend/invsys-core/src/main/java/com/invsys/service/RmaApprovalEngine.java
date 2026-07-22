package com.invsys.service;

import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Rules-based auto-approval for B2B portal RMAs.
 * Forces PENDING_REVIEW when a variant is flagged, value exceeds tenant max, or reason is DAMAGED.
 */
@Component
public class RmaApprovalEngine {

    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String REASON_DAMAGED = "DAMAGED";

    private final TenantSettingsRepository tenantSettingsRepository;

    public RmaApprovalEngine(TenantSettingsRepository tenantSettingsRepository) {
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    public record Decision(String status, String reviewReason, BigDecimal maxAutoApproveValue) {
    }

    public Decision evaluate(BigDecimal rmaMerchandiseValue,
                             String reasonCode,
                             List<ProductVariant> variants) {
        UUID tenantId = TenantContext.requireTenantId();
        BigDecimal maxValue = tenantSettingsRepository.findByTenantId(tenantId)
                .map(TenantSettings::getRmaAutoApproveMaxValue)
                .orElse(new BigDecimal("100.00"));
        if (maxValue == null) {
            maxValue = new BigDecimal("100.00");
        }

        String reason = reasonCode == null ? "" : reasonCode.trim().toUpperCase(Locale.ROOT);
        if (REASON_DAMAGED.equals(reason)) {
            return new Decision(STATUS_PENDING_REVIEW, "Reason DAMAGED requires evidence review", maxValue);
        }
        if (variants != null) {
            for (ProductVariant variant : variants) {
                if (variant != null && variant.isRmaRequiresReview()) {
                    return new Decision(STATUS_PENDING_REVIEW,
                            "Variant " + variant.getSku() + " requires manual RMA review", maxValue);
                }
            }
        }
        BigDecimal value = rmaMerchandiseValue != null ? rmaMerchandiseValue : BigDecimal.ZERO;
        if (value.compareTo(maxValue) > 0) {
            return new Decision(STATUS_PENDING_REVIEW,
                    "RMA value " + value + " exceeds auto-approve max " + maxValue, maxValue);
        }
        return new Decision(STATUS_APPROVED, null, maxValue);
    }
}
