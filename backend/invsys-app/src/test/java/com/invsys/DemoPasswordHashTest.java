package com.invsys;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPasswordHashTest {

    private static final String DEMO_HASH = "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Test
    void demoSeedHashMatchesPassword123() {
        var encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("password123", DEMO_HASH)).isTrue();
    }
}
