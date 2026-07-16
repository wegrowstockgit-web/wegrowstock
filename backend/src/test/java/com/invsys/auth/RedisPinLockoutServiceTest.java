package com.invsys.auth;

import com.invsys.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisPinLockoutServiceTest {

    RedisPinLockoutService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        service = new RedisPinLockoutService(provider);
        service.reset();
    }

    @Test
    void locksAfterThreeFailuresAndExposesUnlockAt() {
        service.recordFailure("cred:a");
        service.recordFailure("cred:a");
        assertThatThrownBy(() -> service.recordFailure("cred:a"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("PIN_LOCKED");
                    assertThat(api.getProperties()).containsKeys("unlockAt", "unlockAtEpochMs");
                });

        assertThatThrownBy(() -> service.assertAllowed("cred:a"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("PIN_LOCKED");
    }

    @Test
    void successClearsFailures() {
        service.recordFailure("cred:b");
        service.recordFailure("cred:b");
        service.recordSuccess("cred:b");
        service.recordFailure("cred:b");
        service.recordFailure("cred:b");
        // third would lock — still under threshold after clear
        service.assertAllowed("cred:b");
    }
}
