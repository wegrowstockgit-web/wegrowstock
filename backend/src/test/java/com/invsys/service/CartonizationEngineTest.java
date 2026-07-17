package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.ShippingCarton;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartonizationEngineTest {

    private final CartonizationEngine engine = new CartonizationEngine();

    @Test
    void selectsSmallestFittingCartonAndComputesBillableWeight() {
        List<ShippingCarton> cartons = List.of(
                carton("Small Mailer", "8", "6", "4", "5"),
                carton("Medium Corrugated", "14", "10", "8", "30"),
                carton("Large Corrugated", "20", "16", "12", "70"));

        CartonizationEngine.CartonizationResult result = engine.selectCarton(
                List.of(item("6", "4", "3", "0.75", "10")),
                cartons);

        // 10×(6×4×3)×1.1 ≈ 792 in³ → Medium (1120) beats Small (192)
        assertThat(result.carton().getName()).isEqualTo("Medium Corrugated");
        assertThat(result.actualWeightLb()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.volumetricWeightLb()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.billableWeightLb())
                .isEqualByComparingTo(result.actualWeightLb().max(result.volumetricWeightLb()));
    }

    @Test
    void rejectsWhenNoCartonFits() {
        List<ShippingCarton> cartons = List.of(carton("Tiny", "2", "2", "2", "1"));
        assertThatThrownBy(() -> engine.selectCarton(
                List.of(item("10", "10", "10", "5", "1")), cartons))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No shipping carton");
    }

    private static CartonizationEngine.LineItem item(
            String l, String w, String h, String weight, String qty) {
        return new CartonizationEngine.LineItem(
                UUID.randomUUID(),
                new BigDecimal(qty),
                new BigDecimal(l),
                new BigDecimal(w),
                new BigDecimal(h),
                "in",
                new BigDecimal(weight),
                "lb");
    }

    private static ShippingCarton carton(String name, String l, String w, String h, String max) {
        ShippingCarton c = new ShippingCarton();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setLength(new BigDecimal(l));
        c.setWidth(new BigDecimal(w));
        c.setHeight(new BigDecimal(h));
        c.setMaxWeight(new BigDecimal(max));
        c.setEmptyWeight(new BigDecimal("0.5"));
        c.setDimUnit("in");
        c.setWeightUnit("lb");
        c.setActive(true);
        return c;
    }
}
