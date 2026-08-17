package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.CurrencyRate;
import com.invsys.repository.CurrencyRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock CurrencyRateRepository currencyRateRepository;
    @InjectMocks CurrencyService service;

    @Test
    void convertUsesDirectAndInverseRates() {
        CurrencyRate usdToMxn = new CurrencyRate();
        usdToMxn.setRate(new BigDecimal("18.5"));
        when(currencyRateRepository.findByFromCurrencyAndToCurrency("USD", "MXN"))
                .thenReturn(Optional.of(usdToMxn));
        assertThat(service.convert(new BigDecimal("2"), "USD", "MXN"))
                .isEqualByComparingTo("37.0000");
        assertThat(service.convert(new BigDecimal("10"), "USD", "USD"))
                .isEqualByComparingTo("10");

        when(currencyRateRepository.findByFromCurrencyAndToCurrency("MXN", "USD"))
                .thenReturn(Optional.empty());
        when(currencyRateRepository.findByFromCurrencyAndToCurrency("USD", "MXN"))
                .thenReturn(Optional.of(usdToMxn));
        assertThat(service.convert(new BigDecimal("18.5"), "MXN", "USD"))
                .isEqualByComparingTo("1.0000");
    }

    @Test
    void convertThrowsWhenNoRateExists() {
        when(currencyRateRepository.findByFromCurrencyAndToCurrency("EUR", "JPY")).thenReturn(Optional.empty());
        when(currencyRateRepository.findByFromCurrencyAndToCurrency("JPY", "EUR")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.convert(BigDecimal.ONE, "EUR", "JPY"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void quoteOrOneNeverThrows() {
        assertThat(service.quoteOrOne("USD", "USD")).isEqualByComparingTo("1");
        assertThat(service.quoteOrOne(null, "MXN")).isEqualByComparingTo("1");
        when(currencyRateRepository.findByFromCurrencyAndToCurrency("USD", "MXN")).thenReturn(Optional.empty());
        when(currencyRateRepository.findByFromCurrencyAndToCurrency("MXN", "USD")).thenReturn(Optional.empty());
        assertThat(service.quoteOrOne("USD", "MXN")).isEqualByComparingTo("1");
    }
}
