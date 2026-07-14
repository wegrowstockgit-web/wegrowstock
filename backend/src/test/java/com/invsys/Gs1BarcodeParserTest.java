package com.invsys;

import com.invsys.service.Gs1BarcodeParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class Gs1BarcodeParserTest {

    @Test
    void parsesCompositeGs1WithGtinLotAndExpiry() {
        // AI 01 (14) + AI 17 (6) + AI 10 (variable)
        String raw = "01012345678901281725010110LOT42";
        var parsed = Gs1BarcodeParser.parse(raw).orElseThrow();
        assertThat(parsed.gtin()).isEqualTo("01234567890128");
        assertThat(parsed.expiry()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(parsed.lot()).isEqualTo("LOT42");
        assertThat(parsed.hasCompositeData()).isTrue();
    }

    @Test
    void parsesParentheticalHumanReadableForm() {
        var parsed = Gs1BarcodeParser.parse("(01)01234567890128(17)251231(10)BATCH9").orElseThrow();
        assertThat(parsed.gtin()).isEqualTo("01234567890128");
        assertThat(parsed.lot()).isEqualTo("BATCH9");
        assertThat(parsed.expiry()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void lookupKeyPrefersGtin() {
        assertThat(Gs1BarcodeParser.lookupKey("01012345678901281725010110ABC"))
                .isEqualTo("01234567890128");
        assertThat(Gs1BarcodeParser.lookupKey("PLAIN-SKU")).isEqualTo("PLAIN-SKU");
    }

    @Test
    void rejectsNonGs1Payloads() {
        assertThat(Gs1BarcodeParser.parse("SKU-ONLY")).isEmpty();
        assertThat(Gs1BarcodeParser.parse("")).isEmpty();
        assertThat(Gs1BarcodeParser.parse(null)).isEmpty();
    }
}
