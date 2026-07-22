package com.invsys.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportAiErrorClassifierTest {

    @Test
    void detectsHttp429AndQuotaMessages() {
        assertThat(SupportAiErrorClassifier.isQuotaOrRateLimit(
                new RuntimeException("ClientException: 429 . You exceeded your current quota")))
                .isTrue();
        assertThat(SupportAiErrorClassifier.isQuotaOrRateLimit(
                new RuntimeException(new IllegalStateException("RESOURCE_EXHAUSTED: rate limit"))))
                .isTrue();
        assertThat(SupportAiErrorClassifier.isQuotaOrRateLimit(
                new RuntimeException("Too Many Requests")))
                .isTrue();
    }

    @Test
    void ignoresOrdinaryModelFailures() {
        assertThat(SupportAiErrorClassifier.isQuotaOrRateLimit(
                new RuntimeException("Failed to convert to SupportChatResponse")))
                .isFalse();
        assertThat(SupportAiErrorClassifier.isQuotaOrRateLimit(null)).isFalse();
    }
}
