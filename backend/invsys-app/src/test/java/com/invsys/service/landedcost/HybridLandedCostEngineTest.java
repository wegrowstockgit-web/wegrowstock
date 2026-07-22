package com.invsys.service.landedcost;

import com.invsys.core.common.Money;
import com.invsys.domain.ProductCategory;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.catalog.repository.ProductCategoryRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridLandedCostEngineTest {

    @Mock ProductVariantRepository variantRepository;
    @Mock ProductCategoryRepository categoryRepository;

    HybridLandedCostEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HybridLandedCostEngine(variantRepository, categoryRepository);
    }

    @Test
    void customsUsesValueStrategyOnly() {
        PurchaseOrderLine cheap = line(new BigDecimal("10"), new BigDecimal("5"));
        PurchaseOrderLine pricey = line(new BigDecimal("10"), new BigDecimal("15"));

        Map<UUID, Money> shares = engine.allocate(
                Money.of("100"),
                List.of(cheap, pricey),
                HybridLandedCostEngine.CostEventType.CUSTOMS_DUTY);

        assertThat(shares.get(cheap.getId()).toBigDecimal())
                .isEqualByComparingTo("25.0000");
        assertThat(shares.get(pricey.getId()).toBigDecimal())
                .isEqualByComparingTo("75.0000");
    }

    @Test
    void freightRejectsValueStrategy() {
        PurchaseOrderLine line = line(new BigDecimal("5"), new BigDecimal("10"));
        assertThatThrownBy(() -> engine.allocateWithStrategy(
                Money.of("50"),
                List.of(line),
                "VALUE",
                HybridLandedCostEngine.CostEventType.FREIGHT))
                .hasMessageContaining("Customs");
    }

    @Test
    void hybridFallsBackToCategoryMedianWeightThenQuantity() {
        UUID categoryId = UUID.randomUUID();
        ProductCategory category = new ProductCategory();
        category.setId(categoryId);
        category.setMedianWeight(new BigDecimal("2.0"));
        category.setMedianVolume(null);

        ProductVariant withWeight = variant(new BigDecimal("4"), null, null);
        ProductVariant missingDims = variant(null, null, categoryId);
        ProductVariant noCategory = variant(null, null, null);

        PurchaseOrderLine lineA = line(withWeight.getId(), new BigDecimal("10"), new BigDecimal("1"));
        PurchaseOrderLine lineB = line(missingDims.getId(), new BigDecimal("10"), new BigDecimal("1"));
        PurchaseOrderLine lineC = line(noCategory.getId(), new BigDecimal("10"), new BigDecimal("1"));

        when(variantRepository.findById(withWeight.getId())).thenReturn(Optional.of(withWeight));
        when(variantRepository.findById(missingDims.getId())).thenReturn(Optional.of(missingDims));
        when(variantRepository.findById(noCategory.getId())).thenReturn(Optional.of(noCategory));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        Map<UUID, Money> shares = engine.allocate(
                Money.of("90"),
                List.of(lineA, lineB, lineC),
                HybridLandedCostEngine.CostEventType.FREIGHT);

        // A+B dimensional (2/3 of pool = 60), C quantity (1/3 = 30)
        assertThat(shares.get(lineC.getId()).toBigDecimal()).isEqualByComparingTo("30.0000");
        Money dimTotal = shares.get(lineA.getId()).add(shares.get(lineB.getId()));
        assertThat(dimTotal.toBigDecimal()).isEqualByComparingTo("60.0000");
        // Heavier A (4) vs category median B (2) â†’ 40 / 20 of the 60
        assertThat(shares.get(lineA.getId()).toBigDecimal()).isEqualByComparingTo("40.0000");
        assertThat(shares.get(lineB.getId()).toBigDecimal()).isEqualByComparingTo("20.0000");
        // No line received $0
        assertThat(shares.values()).noneMatch(Money::isZero);
    }

    @Test
    void missingWeightDoesNotDefaultToZeroBasis() {
        ProductVariant bare = variant(null, null, null);
        PurchaseOrderLine line = line(bare.getId(), new BigDecimal("5"), new BigDecimal("10"));
        when(variantRepository.findById(bare.getId())).thenReturn(Optional.of(bare));

        Map<UUID, Money> shares = engine.allocateWithStrategy(
                Money.of("50"),
                List.of(line),
                "WEIGHT",
                HybridLandedCostEngine.CostEventType.FREIGHT);

        // Cascade to quantity â€” full 50, not $0
        assertThat(shares.get(line.getId()).toBigDecimal()).isEqualByComparingTo("50.0000");
    }

    @Test
    void byVolumeAliasAllocatesByResolvedVolumes() {
        ProductVariant large = variant(null, new BigDecimal("3"), null);
        ProductVariant small = variant(null, new BigDecimal("1"), null);
        PurchaseOrderLine lineA = line(large.getId(), new BigDecimal("10"), new BigDecimal("1"));
        PurchaseOrderLine lineB = line(small.getId(), new BigDecimal("10"), new BigDecimal("1"));
        when(variantRepository.findById(large.getId())).thenReturn(Optional.of(large));
        when(variantRepository.findById(small.getId())).thenReturn(Optional.of(small));

        Map<UUID, Money> shares = engine.allocateWithStrategy(
                Money.of("40"),
                List.of(lineA, lineB),
                "BY_VOLUME",
                HybridLandedCostEngine.CostEventType.FREIGHT);

        assertThat(shares.get(lineA.getId()).toBigDecimal()).isEqualByComparingTo("30.0000");
        assertThat(shares.get(lineB.getId()).toBigDecimal()).isEqualByComparingTo("10.0000");
    }

    private static PurchaseOrderLine line(BigDecimal qty, BigDecimal unitCost) {
        return line(UUID.randomUUID(), qty, unitCost);
    }

    private static PurchaseOrderLine line(UUID variantId, BigDecimal qty, BigDecimal unitCost) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setId(UUID.randomUUID());
        line.setVariantId(variantId);
        line.setQtyOrdered(qty);
        line.setQtyReceived(qty);
        line.setUnitCost(unitCost);
        return line;
    }

    private static ProductVariant variant(BigDecimal weight, BigDecimal volume, UUID categoryId) {
        ProductVariant v = new ProductVariant();
        v.setId(UUID.randomUUID());
        v.setSku("SKU-" + v.getId().toString().substring(0, 4));
        v.setWeight(weight);
        v.setVolume(volume);
        v.setCategoryId(categoryId);
        return v;
    }
}
