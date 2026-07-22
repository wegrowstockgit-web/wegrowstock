package com.invsys.service.conflict;

import com.invsys.domain.ConflictActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictSchemaMetadataGeneratorTest {

    private final ConflictSchemaMetadataGenerator generator = new ConflictSchemaMetadataGenerator();

    @Test
    void inboundReceiveExposesMutableQuantityAndImmutableBarcode() {
        List<ConflictFieldDescriptor> fields = generator.generate(
                "/api/v1/fulfillment/scan",
                ConflictActionType.INBOUND_RECEIVE,
                Map.of("mode", "receive", "barcode", "123", "quantity", 2));

        assertThat(fields).anySatisfy(f -> {
            assertThat(f.key()).isEqualTo("quantity");
            assertThat(f.mutable()).isTrue();
            assertThat(f.type()).isEqualTo("number");
            assertThat(f.constraints()).containsEntry("min", 1);
        });
        assertThat(fields).anySatisfy(f -> {
            assertThat(f.key()).isEqualTo("barcode");
            assertThat(f.mutable()).isFalse();
            assertThat(f.label()).containsIgnoringCase("GTIN");
        });
    }

    @Test
    void outboundPickInfersFromModeWhenActionNull() {
        List<Map<String, Object>> maps = generator.generateAsMaps(
                "/api/v1/fulfillment/scan",
                null,
                Map.of("mode", "pick", "barcode", "999"));

        assertThat(maps).extracting(m -> m.get("key")).contains("quantity", "allocationId", "barcode");
        assertThat(maps).filteredOn(m -> "quantity".equals(m.get("key")))
                .first()
                .satisfies(m -> assertThat(m.get("mutable")).isEqualTo(true));
    }

    @Test
    void cycleCountSchemaAllowsZeroMin() {
        List<ConflictFieldDescriptor> fields = generator.generate(
                "/api/v1/cycle-counts",
                ConflictActionType.CYCLE_COUNT,
                Map.of());

        ConflictFieldDescriptor qty = fields.stream()
                .filter(f -> "quantity".equals(f.key()))
                .findFirst()
                .orElseThrow();
        assertThat(qty.constraints()).containsEntry("min", 0);
    }
}
