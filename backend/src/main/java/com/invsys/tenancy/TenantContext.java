package com.invsys.tenancy;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CUSTOMER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BOOTSTRAP = ThreadLocal.withInitial(() -> false);

    private TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        TENANT.set(tenantId);
    }

    public static Optional<UUID> getTenantId() {
        return Optional.ofNullable(TENANT.get());
    }

    public static UUID requireTenantId() {
        return getTenantId().orElseThrow(() -> new IllegalStateException("Tenant context not set"));
    }

    public static void setUserId(UUID userId) {
        USER.set(userId);
    }

    public static Optional<UUID> getUserId() {
        return Optional.ofNullable(USER.get());
    }

    public static void setCustomerId(UUID customerId) {
        CUSTOMER.set(customerId);
    }

    public static Optional<UUID> getCustomerId() {
        return Optional.ofNullable(CUSTOMER.get());
    }

    public static UUID requireCustomerId() {
        return getCustomerId().orElseThrow(() -> new IllegalStateException("Customer context not set"));
    }

    public static void setBootstrap(boolean bootstrap) {
        BOOTSTRAP.set(bootstrap);
    }

    public static boolean isBootstrap() {
        return Boolean.TRUE.equals(BOOTSTRAP.get());
    }

    public static void clear() {
        TENANT.remove();
        USER.remove();
        CUSTOMER.remove();
        BOOTSTRAP.remove();
    }
}
