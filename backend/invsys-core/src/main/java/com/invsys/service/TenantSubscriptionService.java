package com.invsys.service;

import com.invsys.config.CacheConfig;
import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.domain.subscription.AppModule;
import com.invsys.domain.subscription.CommercialTier;
import com.invsys.domain.subscription.TenantSubscription;
import com.invsys.repository.TenantSubscriptionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantSubscriptionService {

    private final BootstrapJdbc bootstrapJdbc;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantSettingsCacheService tenantSettingsCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantSubscriptionService self;

    public TenantSubscriptionService(BootstrapJdbc bootstrapJdbc,
                                     TenantSubscriptionRepository tenantSubscriptionRepository,
                                     TenantSettingsCacheService tenantSettingsCacheService,
                                     ApplicationEventPublisher eventPublisher,
                                     @Lazy TenantSubscriptionService self) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.tenantSettingsCacheService = tenantSettingsCacheService;
        this.eventPublisher = eventPublisher;
        this.self = self;
    }

    /**
     * Commercial tier bundle presets.
     * BASIC → CORE only; INTERMEDIATE → CORE + Tier-2 add-ons; ENTERPRISE → all modules.
     */
    public Set<AppModule> getDefaultModulesForTier(CommercialTier tier) {
        CommercialTier resolved = tier != null ? tier : CommercialTier.BASIC;
        return switch (resolved) {
            case BASIC -> EnumSet.of(AppModule.CORE);
            case INTERMEDIATE -> EnumSet.of(
                    AppModule.CORE,
                    AppModule.SHOPIFY,
                    AppModule.ACCOUNTING,
                    AppModule.ADVANCED_FULFILLMENT,
                    AppModule.MANUFACTURING,
                    AppModule.DOCUMENTS,
                    AppModule.MRP);
            case ENTERPRISE -> EnumSet.allOf(AppModule.class);
        };
    }

    @Transactional(readOnly = true)
    public List<ControlPlaneTenantView> listTenantsWithModules() {
        return bootstrapJdbc.listTenantsWithSubscriptions().stream()
                .map(row -> new ControlPlaneTenantView(
                        row.tenantId(),
                        row.name(),
                        row.slug(),
                        row.status(),
                        CommercialTier.fromString(row.tier()),
                        parseModules(row.enabledModulesJson())))
                .toList();
    }

    /**
     * Cached enabled-module snapshot used by {@code RequireModuleAspect}.
     * Cache name {@link CacheConfig#TENANT_SETTINGS_CACHE} matches the control-plane eviction contract.
     */
    @Cacheable(cacheNames = CacheConfig.TENANT_SETTINGS_CACHE, key = "#tenantId")
    @Transactional(readOnly = true)
    public List<AppModule> getEnabledModules(UUID tenantId) {
        return bootstrapJdbc.findTenantSubscription(tenantId)
                .map(row -> parseModules(row.enabledModulesJson()))
                .orElseGet(() -> List.of(AppModule.CORE));
    }

    public boolean isModuleEnabled(UUID tenantId, AppModule module) {
        if (module == null) {
            return false;
        }
        return self.getEnabledModules(tenantId).contains(module);
    }

    @CacheEvict(cacheNames = CacheConfig.TENANT_SETTINGS_CACHE, key = "#tenantId")
    @Transactional
    public ControlPlaneTenantView replaceEnabledModules(UUID tenantId, List<AppModule> modules) {
        if (tenantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TENANT", "tenantId required");
        }
        bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tenant not found"));

        List<AppModule> normalized = normalizeModules(modules);
        String json = toJsonArray(normalized);
        BootstrapJdbc.TenantSubscriptionRow updated = bootstrapJdbc.upsertTenantEnabledModules(tenantId, json)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SUBSCRIPTION_UPDATE_FAILED",
                        "Failed to update tenant subscription"));

        return afterSubscriptionWrite(updated);
    }

    /**
     * Applies a commercial tier: sets {@code tier} and rebuilds {@code enabled_modules}
     * from the tier bundle while preserving custom add-ons that were outside the previous
     * tier's default set.
     */
    @CacheEvict(cacheNames = CacheConfig.TENANT_SETTINGS_CACHE, key = "#tenantId")
    @Transactional
    public ControlPlaneTenantView replaceTier(UUID tenantId, CommercialTier newTier) {
        if (tenantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TENANT", "tenantId required");
        }
        if (newTier == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIER", "tier required");
        }
        BootstrapJdbc.TenantSubscriptionRow current = bootstrapJdbc.findTenantSubscription(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tenant not found"));

        CommercialTier previousTier = CommercialTier.fromString(current.tier());
        Set<AppModule> previousDefaults = getDefaultModulesForTier(previousTier);
        Set<AppModule> currentEnabled = new LinkedHashSet<>(parseModules(current.enabledModulesJson()));

        Set<AppModule> customOverrides = EnumSet.noneOf(AppModule.class);
        for (AppModule module : currentEnabled) {
            if (!previousDefaults.contains(module)) {
                customOverrides.add(module);
            }
        }

        Set<AppModule> next = new LinkedHashSet<>(getDefaultModulesForTier(newTier));
        next.addAll(customOverrides);
        List<AppModule> normalized = normalizeModules(List.copyOf(next));

        BootstrapJdbc.TenantSubscriptionRow updated = bootstrapJdbc
                .upsertTenantTierAndModules(tenantId, newTier.name(), toJsonArray(normalized))
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SUBSCRIPTION_UPDATE_FAILED",
                        "Failed to update tenant tier"));

        return afterSubscriptionWrite(updated);
    }

    private ControlPlaneTenantView afterSubscriptionWrite(BootstrapJdbc.TenantSubscriptionRow updated) {
        tenantSettingsCacheService.invalidate(updated.tenantId());
        List<AppModule> enabled = parseModules(updated.enabledModulesJson());
        eventPublisher.publishEvent(new TenantSubscriptionUpdatedEvent(updated.tenantId(), enabled));
        return new ControlPlaneTenantView(
                updated.tenantId(),
                updated.name(),
                updated.slug(),
                updated.status(),
                CommercialTier.fromString(updated.tier()),
                enabled);
    }

    /**
     * Seed defaults in the same JPA transaction as tenant provisioning
     * (BootstrapJdbc cannot see uncommitted tenants on a separate connection).
     * New tenants start ENTERPRISE (full catalog) so modular monolith tests/dev stay unlocked;
     * control plane can downgrade to BASIC/INTERMEDIATE bundles.
     */
    @Transactional
    public void insertDefaults(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        if (tenantSubscriptionRepository.existsById(tenantId)) {
            return;
        }
        tenantSubscriptionRepository.save(TenantSubscription.defaults(tenantId));
    }

    private static List<AppModule> normalizeModules(List<AppModule> modules) {
        Set<AppModule> ordered = new LinkedHashSet<>();
        ordered.add(AppModule.CORE);
        if (modules != null) {
            for (AppModule m : modules) {
                if (m != null) {
                    ordered.add(m);
                }
            }
        }
        List<AppModule> result = new ArrayList<>();
        for (AppModule m : AppModule.values()) {
            if (ordered.contains(m)) {
                result.add(m);
            }
        }
        return result;
    }

    static List<AppModule> parseModules(String jsonOrCsv) {
        if (jsonOrCsv == null || jsonOrCsv.isBlank()) {
            return List.of(AppModule.CORE);
        }
        String trimmed = jsonOrCsv.trim();
        EnumSet<AppModule> found = EnumSet.noneOf(AppModule.class);
        if (trimmed.startsWith("[")) {
            String inner = trimmed.substring(1, trimmed.endsWith("]") ? trimmed.length() - 1 : trimmed.length());
            for (String part : inner.split(",")) {
                String token = part.trim().replace("\"", "").replace("'", "");
                if (token.isEmpty()) {
                    continue;
                }
                try {
                    found.add(AppModule.fromString(token));
                } catch (IllegalArgumentException ignored) {
                    // skip unknown module tokens
                }
            }
        } else {
            for (String part : trimmed.split(",")) {
                try {
                    found.add(AppModule.fromString(part.trim()));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
        if (found.isEmpty()) {
            found.add(AppModule.CORE);
        }
        List<AppModule> result = new ArrayList<>();
        for (AppModule m : AppModule.values()) {
            if (found.contains(m)) {
                result.add(m);
            }
        }
        return result;
    }

    private static String toJsonArray(List<AppModule> modules) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < modules.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(modules.get(i).name()).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    public record ControlPlaneTenantView(
            UUID tenantId,
            String name,
            String slug,
            String status,
            CommercialTier tier,
            List<AppModule> enabledModules
    ) {
    }
}
