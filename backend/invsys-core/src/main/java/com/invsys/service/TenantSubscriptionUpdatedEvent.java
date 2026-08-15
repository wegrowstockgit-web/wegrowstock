package com.invsys.service;

import com.invsys.domain.subscription.AppModule;

import java.util.List;
import java.util.UUID;

/** Fired after control-plane updates a tenant's enabled commercial modules. */
public record TenantSubscriptionUpdatedEvent(
        UUID tenantId,
        List<AppModule> enabledModules
) {
}
