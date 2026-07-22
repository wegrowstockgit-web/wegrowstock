package com.invsys.modules.fintech.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "capital_credit_lines")
public class CapitalCreditLine extends TenantScopedEntity {

    @Column(name = "credit_limit", nullable = false)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "outstanding_balance", nullable = false)
    private BigDecimal outstandingBalance = BigDecimal.ZERO;

    @Column(name = "interest_rate_apr", nullable = false)
    private BigDecimal interestRateApr = new BigDecimal("12.00");

    @Column(name = "utilization_status", nullable = false)
    private String utilizationStatus = "AVAILABLE";

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public BigDecimal getOutstandingBalance() {
        return outstandingBalance;
    }

    public void setOutstandingBalance(BigDecimal outstandingBalance) {
        this.outstandingBalance = outstandingBalance;
    }

    public BigDecimal getInterestRateApr() {
        return interestRateApr;
    }

    public void setInterestRateApr(BigDecimal interestRateApr) {
        this.interestRateApr = interestRateApr;
    }

    public String getUtilizationStatus() {
        return utilizationStatus;
    }

    public void setUtilizationStatus(String utilizationStatus) {
        this.utilizationStatus = utilizationStatus;
    }
}
