package com.invsys.core.security;

import com.invsys.core.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorporateCidrMatcherTest {

    @Test
    void matchesIpv4CidrAndRejectsUnknown() {
        assertThat(CorporateCidrMatcher.matches("203.0.113.10", List.of("203.0.113.0/24"))).isTrue();
        assertThat(CorporateCidrMatcher.matches("198.51.100.1", List.of("203.0.113.0/24"))).isFalse();
        assertThat(CorporateCidrMatcher.matches("unknown", List.of("203.0.113.0/24"))).isFalse();
        assertThat(CorporateCidrMatcher.matches("203.0.113.10", List.of("not-a-cidr"))).isFalse();
    }

    @Test
    void normalizeOrReject_dedupesAndValidates() {
        assertThat(CorporateCidrMatcher.normalizeOrReject(List.of(" 10.0.0.0/8 ", "10.0.0.0/8", "")))
                .containsExactly("10.0.0.0/8");
        assertThatThrownBy(() -> CorporateCidrMatcher.normalizeOrReject(List.of("999.1.1.1/99")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_CIDR");
    }
}
