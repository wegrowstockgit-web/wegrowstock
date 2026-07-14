package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.CurrencyRate;
import com.invsys.repository.CurrencyRateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CurrencyService {

    private final CurrencyRateRepository currencyRateRepository;

    public CurrencyService(CurrencyRateRepository currencyRateRepository) {
        this.currencyRateRepository = currencyRateRepository;
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (from.equals(to)) {
            return amount;
        }
        CurrencyRate direct = currencyRateRepository.findByFromCurrencyAndToCurrency(from, to)
                .orElse(null);
        if (direct != null) {
            return amount.multiply(direct.getRate()).setScale(4, RoundingMode.HALF_UP);
        }
        CurrencyRate inverse = currencyRateRepository.findByFromCurrencyAndToCurrency(to, from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_RATE",
                        "No exchange rate for " + from + " to " + to));
        return amount.divide(inverse.getRate(), 4, RoundingMode.HALF_UP);
    }
}
