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
    void parsesVariableQuantityAi30() {
        // AI 01 + AI 30 (count) + AI 10
        String raw = "01012345678901283012" + "\u001d" + "10LOT99";
        var parsed = Gs1BarcodeParser.parse(raw).orElseThrow();
        assertThat(parsed.gtin()).isEqualTo("01234567890128");
        assertThat(parsed.variableQuantity()).isEqualByComparingTo("12");
        assertThat(parsed.lot()).isEqualTo("LOT99");
        assertThat(parsed.hasCompositeData()).isTrue();
    }

    @Test
    void rejectsNonGs1Payloads() {
        assertThat(Gs1BarcodeParser.parse("SKU-ONLY")).isEmpty();
        assertThat(Gs1BarcodeParser.parse("")).isEmpty();
        assertThat(Gs1BarcodeParser.parse(null)).isEmpty();
    }

    @Test
    void parsesGsWithBraceDelimiterAndSerialAi21() {
        var parsed = Gs1BarcodeParser.parse("0101234567890128{GS}21SERIAL99{GS}10LOT7").orElseThrow();
        assertThat(parsed.gtin()).isEqualTo("01234567890128");
        assertThat(parsed.serial()).isEqualTo("SERIAL99");
        assertThat(parsed.lot()).isEqualTo("LOT7");
        assertThat(parsed.all()).containsKeys("01", "21", "10");
    }

    @Test
    void parentheticalWithQuantityDoesNotBleedLot() {
        var parsed = Gs1BarcodeParser.parse("(01)01234567890128(30)8(10)BATCHX(17)260101").orElseThrow();
        assertThat(parsed.variableQuantity()).isEqualByComparingTo("8");
        assertThat(parsed.lot()).isEqualTo("BATCHX");
        assertThat(parsed.expiry()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void stripsC1SymbologyIdentifierAndDayZeroExpiry() {
        var parsed = Gs1BarcodeParser.parse("]C101012345678901281700010110LOTZ").orElseThrow();
        assertThat(parsed.gtin()).isEqualTo("01234567890128");
        assertThat(parsed.expiry()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(parsed.lot()).isEqualTo("LOTZ");
    }

    @Test
    void rejectsTruncatedGtinPayload() {
        assertThat(Gs1BarcodeParser.parse("01012345678901")).isEmpty();
    }
}
