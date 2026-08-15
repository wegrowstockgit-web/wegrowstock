package com.invsys.domain.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tenant_subscriptions")
public class TenantSubscription {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CommercialTier tier = CommercialTier.BASIC;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enabled_modules", columnDefinition = "jsonb", nullable = false)
    private List<String> enabledModules = new ArrayList<>(List.of(AppModule.CORE.name()));

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (enabledModules == null || enabledModules.isEmpty()) {
            enabledModules = new ArrayList<>(List.of(AppModule.CORE.name()));
        }
        if (tier == null) {
            tier = CommercialTier.BASIC;
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static TenantSubscription defaults(UUID tenantId) {
        TenantSubscription sub = new TenantSubscription();
        sub.setTenantId(tenantId);
        sub.setTier(CommercialTier.ENTERPRISE);
        List<String> all = new ArrayList<>();
        for (AppModule module : AppModule.values()) {
            all.add(module.name());
        }
        sub.setEnabledModules(all);
        return sub;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public CommercialTier getTier() {
        return tier;
    }

    public void setTier(CommercialTier tier) {
        this.tier = tier;
    }

    public List<String> getEnabledModules() {
        return enabledModules;
    }

    public void setEnabledModules(List<String> enabledModules) {
        this.enabledModules = enabledModules != null ? new ArrayList<>(enabledModules) : new ArrayList<>();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean hasModule(AppModule module) {
        if (module == null || enabledModules == null) {
            return false;
        }
        return enabledModules.stream().anyMatch(m -> module.name().equalsIgnoreCase(m));
    }
}
