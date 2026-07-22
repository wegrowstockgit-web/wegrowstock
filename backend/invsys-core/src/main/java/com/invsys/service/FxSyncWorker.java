package com.invsys.service;

import com.invsys.core.tenancy.BootstrapJdbc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class FxSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(FxSyncWorker.class);
    private static final List<String> BASE_CURRENCIES = List.of("USD", "EUR", "GBP", "CAD", "AUD");

    private final BootstrapJdbc bootstrapJdbc;
    private final RestClient restClient;
    private final String apiKey;

    public FxSyncWorker(BootstrapJdbc bootstrapJdbc,
                        @Value("${invsys.fx.openexchangerates-app-id:}") String apiKey) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.apiKey = apiKey;
        this.restClient = RestClient.create();
    }

    @Scheduled(cron = "0 30 1 * * *")
    public void syncRates() {
        Instant asOf = Instant.now();
        if (apiKey == null || apiKey.isBlank()) {
            upsertFallbackRates(asOf);
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri("https://openexchangerates.org/api/latest.json?app_id=" + apiKey)
                    .retrieve()
                    .body(Map.class);
            if (body == null || !body.containsKey("rates")) {
                upsertFallbackRates(asOf);
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Number> rates = (Map<String, Number>) body.get("rates");
            Number usdRate = rates.get("USD");
            if (usdRate == null) {
                upsertFallbackRates(asOf);
                return;
            }
            for (String from : BASE_CURRENCIES) {
                Number fromRate = rates.get(from);
                if (fromRate == null) {
                    continue;
                }
                for (String to : BASE_CURRENCIES) {
                    if (from.equals(to)) {
                        continue;
                    }
                    Number toRate = rates.get(to);
                    if (toRate == null) {
                        continue;
                    }
                    BigDecimal cross = BigDecimal.valueOf(toRate.doubleValue())
                            .divide(BigDecimal.valueOf(fromRate.doubleValue()), 8, java.math.RoundingMode.HALF_UP);
                    upsertRate(from, to, cross, asOf);
                }
            }
            log.info("FX sync completed from OpenExchangeRates");
        } catch (Exception e) {
            log.warn("FX sync failed, using fallback rates: {}", e.getMessage());
            upsertFallbackRates(asOf);
        }
    }

    private void upsertFallbackRates(Instant asOf) {
        Map<String, BigDecimal> usdRates = Map.of(
                "EUR", new BigDecimal("0.92000000"),
                "GBP", new BigDecimal("0.79000000"),
                "CAD", new BigDecimal("1.36000000"),
                "AUD", new BigDecimal("1.52000000")
        );
        for (String from : BASE_CURRENCIES) {
            for (String to : BASE_CURRENCIES) {
                if (from.equals(to)) {
                    continue;
                }
                BigDecimal rate;
                if ("USD".equals(from)) {
                    rate = usdRates.getOrDefault(to, BigDecimal.ONE);
                } else if ("USD".equals(to)) {
                    rate = BigDecimal.ONE.divide(usdRates.getOrDefault(from, BigDecimal.ONE), 8, java.math.RoundingMode.HALF_UP);
                } else {
                    BigDecimal fromUsd = usdRates.getOrDefault(from, BigDecimal.ONE);
                    BigDecimal toUsd = usdRates.getOrDefault(to, BigDecimal.ONE);
                    rate = toUsd.divide(fromUsd, 8, java.math.RoundingMode.HALF_UP);
                }
                upsertRate(from, to, rate, asOf);
            }
        }
    }

    private void upsertRate(String from, String to, BigDecimal rate, Instant asOf) {
        bootstrapJdbc.upsertCurrencyRate(from, to, rate, asOf);
    }
}
