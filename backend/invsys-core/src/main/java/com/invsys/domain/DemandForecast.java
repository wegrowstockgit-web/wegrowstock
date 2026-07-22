package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "demand_forecasts")
public class DemandForecast extends TenantScopedEntity {

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "recommended_po_qty", nullable = false)
    private BigDecimal recommendedPoQty = BigDecimal.ZERO;

    @Column(name = "velocity_30d", nullable = false)
    private BigDecimal velocity30d = BigDecimal.ZERO;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt = Instant.now();

    @Column(name = "seasonality_index", nullable = false)
    private BigDecimal seasonalityIndex = BigDecimal.ONE;

    @Column(name = "confidence_score", nullable = false)
    private BigDecimal confidenceScore = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_signals", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> externalSignals = new LinkedHashMap<>();

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public BigDecimal getRecommendedPoQty() {
        return recommendedPoQty;
    }

    public void setRecommendedPoQty(BigDecimal recommendedPoQty) {
        this.recommendedPoQty = recommendedPoQty;
    }

    public BigDecimal getVelocity30d() {
        return velocity30d;
    }

    public void setVelocity30d(BigDecimal velocity30d) {
        this.velocity30d = velocity30d;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public BigDecimal getSeasonalityIndex() {
        return seasonalityIndex;
    }

    public void setSeasonalityIndex(BigDecimal seasonalityIndex) {
        this.seasonalityIndex = seasonalityIndex;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Map<String, Object> getExternalSignals() {
        return externalSignals;
    }

    public void setExternalSignals(Map<String, Object> externalSignals) {
        this.externalSignals = externalSignals;
    }
}
