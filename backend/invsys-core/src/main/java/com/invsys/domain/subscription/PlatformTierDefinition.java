package com.invsys.domain.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "platform_tier_definitions")
public class PlatformTierDefinition {

    @Id
    @Column(name = "tier_code", length = 50)
    private String tierCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_modules", columnDefinition = "jsonb", nullable = false)
    private List<String> defaultModules = new ArrayList<>(List.of(AppModule.CORE.name()));

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() {
        if (defaultModules == null || defaultModules.isEmpty()) {
            defaultModules = new ArrayList<>(List.of(AppModule.CORE.name()));
        }
        updatedAt = Instant.now();
    }

    public String getTierCode() {
        return tierCode;
    }

    public void setTierCode(String tierCode) {
        this.tierCode = tierCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getDefaultModules() {
        return defaultModules;
    }

    public void setDefaultModules(List<String> defaultModules) {
        this.defaultModules = defaultModules != null ? new ArrayList<>(defaultModules) : new ArrayList<>();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
