package com.invsys.integration.domain;

import com.invsys.core.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "integration_rate_buckets")
public class IntegrationRateBucket extends TenantScopedEntity {

    @Column(nullable = false, length = 50)
    private String system;

    @Column(name = "tokens_remaining", nullable = false)
    private BigDecimal tokensRemaining = BigDecimal.ZERO;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart = Instant.now();

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public BigDecimal getTokensRemaining() {
        return tokensRemaining;
    }

    public void setTokensRemaining(BigDecimal tokensRemaining) {
        this.tokensRemaining = tokensRemaining;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }
}
