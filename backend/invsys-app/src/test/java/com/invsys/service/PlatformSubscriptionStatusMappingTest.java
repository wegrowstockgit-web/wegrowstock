package com.invsys.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSubscriptionStatusMappingTest {

    @Test
    void mapsStripeLifecycleStatuses() {
        assertThat(PlatformSubscriptionWebhookService.mapSubscriptionStatus(
                "customer.subscription.updated", "active")).isEqualTo("ACTIVE");
        assertThat(PlatformSubscriptionWebhookService.mapSubscriptionStatus(
                "customer.subscription.updated", "trialing")).isEqualTo("ACTIVE");
        assertThat(PlatformSubscriptionWebhookService.mapSubscriptionStatus(
                "customer.subscription.updated", "past_due")).isEqualTo("PAST_DUE");
        assertThat(PlatformSubscriptionWebhookService.mapSubscriptionStatus(
                "customer.subscription.updated", "unpaid")).isEqualTo("SUSPENDED");
        assertThat(PlatformSubscriptionWebhookService.mapSubscriptionStatus(
                "customer.subscription.deleted", "active")).isEqualTo("SUSPENDED");
    }
}
