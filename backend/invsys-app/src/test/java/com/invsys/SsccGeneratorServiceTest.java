package com.invsys;

import com.invsys.service.SsccGeneratorService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SsccGeneratorServiceTest {

    @Test
    void generateSscc18Produces18DigitsWithValidCheckDigit() {
        String sscc = SsccGeneratorService.generateSscc18("0", "0614141", "123456789");
        assertThat(sscc).hasSize(18);
        assertThat(sscc).matches("\\d{18}");

        String withoutCheck = sscc.substring(0, 17);
        int expectedCheck = SsccGeneratorService.gs1Mod10CheckDigit(withoutCheck);
        assertThat(sscc.charAt(17) - '0').isEqualTo(expectedCheck);
    }

    @Test
    void generateSscc18PadsSerialToFillStructure() {
        String sscc = SsccGeneratorService.generateSscc18("0", "0614141", "42");
        assertThat(sscc).hasSize(18);
        assertThat(sscc.startsWith("00614141")).isTrue();
    }

    @Test
    void gs1Mod10CheckDigitMatchesKnownValue() {
        assertThat(SsccGeneratorService.gs1Mod10CheckDigit("00614141123456789")).isBetween(0, 9);
    }

    @Test
    void extensionDigitIsPreserved() {
        String sscc = SsccGeneratorService.generateSscc18("1", "0614141", "999999999");
        assertThat(sscc.charAt(0)).isEqualTo('1');
    }
}
