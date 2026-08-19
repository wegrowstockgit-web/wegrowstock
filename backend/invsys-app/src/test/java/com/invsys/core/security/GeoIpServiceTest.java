package com.invsys.core.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeoIpServiceTest {

    private final GeoIpService geoIpService = new GeoIpService();

    @Test
    void mapsPrivateAndLoopbackToCorporateNetwork() {
        assertThat(geoIpService.resolveLocation("10.1.2.3")).isEqualTo(GeoIpService.CORPORATE_NETWORK);
        assertThat(geoIpService.resolveLocation("192.168.0.20")).isEqualTo(GeoIpService.CORPORATE_NETWORK);
        assertThat(geoIpService.resolveLocation("127.0.0.1")).isEqualTo(GeoIpService.CORPORATE_NETWORK);
        assertThat(geoIpService.resolveLocation("::1")).isEqualTo(GeoIpService.CORPORATE_NETWORK);
    }

    @Test
    void mapsDocumentationRangesToStableMockCities() {
        assertThat(geoIpService.resolveLocation("203.0.113.40")).isEqualTo("Dallas, TX, US");
        assertThat(geoIpService.resolveLocation("198.51.100.45")).isEqualTo("London, England, GB");
        assertThat(geoIpService.resolveLocation("192.0.2.10")).isEqualTo("Sydney, NSW, AU");
    }

    @Test
    void unknownOrBlankBecomesUnknownRegion() {
        assertThat(geoIpService.resolveLocation(null)).isEqualTo(GeoIpService.UNKNOWN_REGION);
        assertThat(geoIpService.resolveLocation("")).isEqualTo(GeoIpService.UNKNOWN_REGION);
        assertThat(geoIpService.resolveLocation("unknown")).isEqualTo(GeoIpService.UNKNOWN_REGION);
        assertThat(geoIpService.resolveLocation("not-an-ip")).isEqualTo(GeoIpService.UNKNOWN_REGION);
    }

    @Test
    void publicIpGetsDeterministicMockCity() {
        String first = geoIpService.resolveLocation("8.8.8.8");
        String second = geoIpService.resolveLocation("8.8.8.8");
        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(GeoIpService.UNKNOWN_REGION);
        assertThat(first).isNotEqualTo(GeoIpService.CORPORATE_NETWORK);
    }

    @Test
    void firstLoginIsNotAnomalous() {
        assertThat(geoIpService.isNewNetworkOrLocation(null, "203.0.113.40", "Dallas, TX, US")).isFalse();
        assertThat(geoIpService.isNewNetworkOrLocation(Map.of(), "203.0.113.40", "Dallas, TX, US")).isFalse();
    }

    @Test
    void sameSubnetAndLocationIsNotAnomalous() {
        assertThat(geoIpService.isNewNetworkOrLocation(
                Map.of("ip", "203.0.113.40", "location", "Dallas, TX, US"),
                "203.0.113.50",
                "Dallas, TX, US")).isFalse();
    }

    @Test
    void differentSubnetOrLocationIsAnomalous() {
        assertThat(geoIpService.isNewNetworkOrLocation(
                Map.of("ip", "203.0.113.40", "location", "Dallas, TX, US"),
                "198.51.100.45",
                "London, England, GB")).isTrue();
        assertThat(geoIpService.isNewNetworkOrLocation(
                Map.of("ip", "10.1.2.3", "location", GeoIpService.CORPORATE_NETWORK),
                "10.9.1.4",
                GeoIpService.CORPORATE_NETWORK)).isTrue();
    }
}
