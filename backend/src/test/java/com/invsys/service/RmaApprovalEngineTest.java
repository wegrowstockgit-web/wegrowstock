package com.invsys.service;

import com.invsys.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmaApprovalEngineTest {

    @Mock
    private TenantSettingsRepository tenantSettingsRepository;

    private RmaApprovalEngine engine;
    private final UUID tenantId = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @BeforeEach
    void setUp() {
        engine = new RmaApprovalEngine(tenantSettingsRepository);
        TenantContext.setTenantId(tenantId);
        TenantSettings settings = new TenantSettings();
        settings.setRmaAutoApproveMaxValue(new BigDecimal("100.00"));
        when(tenantSettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void autoApprovesWhenUnderThreshold() {
        ProductVariant variant = new ProductVariant();
        variant.setSku("OK-1");
        variant.setRmaRequiresReview(false);

        RmaApprovalEngine.Decision decision = engine.evaluate(
                new BigDecimal("40.00"), "CHANGED_MIND", List.of(variant));

        assertThat(decision.status()).isEqualTo("APPROVED");
    }

    @Test
    void pendingWhenDamaged() {
        RmaApprovalEngine.Decision decision = engine.evaluate(
                new BigDecimal("10.00"), "DAMAGED", List.of());
        assertThat(decision.status()).isEqualTo("PENDING_REVIEW");
        assertThat(decision.reviewReason()).contains("DAMAGED");
    }

    @Test
    void pendingWhenValueExceedsMax() {
        RmaApprovalEngine.Decision decision = engine.evaluate(
                new BigDecimal("150.00"), "WRONG_ITEM", List.of());
        assertThat(decision.status()).isEqualTo("PENDING_REVIEW");
        assertThat(decision.reviewReason()).contains("exceeds");
    }

    @Test
    void pendingWhenVariantFlagged() {
        ProductVariant variant = new ProductVariant();
        variant.setSku("FLAG-1");
        variant.setRmaRequiresReview(true);

        RmaApprovalEngine.Decision decision = engine.evaluate(
                new BigDecimal("10.00"), "OTHER", List.of(variant));
        assertThat(decision.status()).isEqualTo("PENDING_REVIEW");
        assertThat(decision.reviewReason()).contains("FLAG-1");
    }
}
