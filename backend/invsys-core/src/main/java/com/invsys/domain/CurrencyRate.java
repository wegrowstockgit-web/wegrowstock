package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import com.invsys.core.common.BaseEntity;

@Entity
@Table(name = "currency_rates")
public class CurrencyRate extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    @Column(nullable = false)
    private BigDecimal rate;

    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    public String getFromCurrency() {
        return fromCurrency;
    }

    public void setFromCurrency(String fromCurrency) {
        this.fromCurrency = fromCurrency;
    }

    public String getToCurrency() {
        return toCurrency;
    }

    public void setToCurrency(String toCurrency) {
        this.toCurrency = toCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public void setAsOf(Instant asOf) {
        this.asOf = asOf;
    }
}
