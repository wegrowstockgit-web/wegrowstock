package com.invsys.repository;

import com.invsys.domain.CurrencyRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, UUID> {
    Optional<CurrencyRate> findByFromCurrencyAndToCurrency(String fromCurrency, String toCurrency);
}
