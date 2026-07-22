package com.invsys.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * External ML inference boundary. Returns null to delegate to regression fallback.
 */
public interface MlInferenceClient {
    ForecastingInferenceService.InferenceResult predict(ForecastingInferenceService.InferenceRequest request);
}

@Component
class LocalMlInferenceClient implements MlInferenceClient {

    @Override
    public ForecastingInferenceService.InferenceResult predict(ForecastingInferenceService.InferenceRequest request) {
        if (request.velocity30d().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal seasonal = BigDecimal.valueOf(1.10);
        BigDecimal adjusted = request.velocity30d().multiply(seasonal);
        int leadDays = request.leadTimeDays() > 0 ? request.leadTimeDays() : 14;
        BigDecimal required = adjusted.multiply(BigDecimal.valueOf(leadDays));
        BigDecimal available = request.onHand().add(request.incomingPo());
        BigDecimal recommended = required.subtract(available).max(BigDecimal.ZERO);
        return new ForecastingInferenceService.InferenceResult(
                recommended,
                adjusted,
                seasonal,
                new BigDecimal("0.81"),
                Map.of("model", "local_ml_stub", "promoBoost", false)
        );
    }
}
