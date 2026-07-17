package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_saved_views")
public class UserSavedView extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "grid_identifier", nullable = false, length = 50)
    private String gridIdentifier;

    @Column(nullable = false, length = 100)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> stateJson = new LinkedHashMap<>();

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getGridIdentifier() {
        return gridIdentifier;
    }

    public void setGridIdentifier(String gridIdentifier) {
        this.gridIdentifier = gridIdentifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getStateJson() {
        return stateJson;
    }

    public void setStateJson(Map<String, Object> stateJson) {
        this.stateJson = stateJson != null ? stateJson : new LinkedHashMap<>();
    }
}
