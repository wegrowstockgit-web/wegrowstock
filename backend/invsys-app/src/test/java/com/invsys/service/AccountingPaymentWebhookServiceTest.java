package com.invsys.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountingPaymentWebhookServiceTest {

    @Test
    void hmacOnly_rejectsRawSecretAndAcceptsDigest() {
        String body = "{\"invoiceNumber\":\"INV-1\"}";
        String secret = "accounting_mock_secret";
        String digest = AccountingPaymentWebhookService.hmacHex(body, secret);

        assertThat(AccountingPaymentWebhookService.isValidSignature(body, secret, secret)).isFalse();
        assertThat(AccountingPaymentWebhookService.isValidSignature(body, digest, secret)).isTrue();
        assertThat(AccountingPaymentWebhookService.isValidSignature(body, digest.toUpperCase(), secret)).isTrue();
        assertThat(AccountingPaymentWebhookService.isValidSignature(null, digest, secret)).isFalse();
    }
}
