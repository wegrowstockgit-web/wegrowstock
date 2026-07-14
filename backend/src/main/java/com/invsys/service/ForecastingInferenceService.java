package com.invsys.service;

import com.invsys.domain.DemandForecast;
import com.invsys.domain.ProductVariant;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demand sensing with pluggable ML inference and localized regression fallback.
 */
@Service
public class ForecastingInferenceService {

    private final MlInferenceClient mlClient;

    public ForecastingInferenceService(MlInferenceClient mlClient) {
        this.mlClient = mlClient;
    }

    public InferenceResult infer(ProductVariant variant, BigDecimal velocity30d, BigDecimal onHand, BigDecimal incomingPo) {
        InferenceRequest request = new InferenceRequest(
                variant.getId(),
                variant.getSku(),
                velocity30d,
                onHand,
                incomingPo,
                variant.getSupplierLeadTimeDays(),
                variant.getReorderPoint()
        );

        InferenceResult mlResult = mlClient.predict(request);
        if (mlResult != null && mlResult.confidenceScore().compareTo(new BigDecimal("0.50")) >= 0) {
            return mlResult;
        }
        return regressionFallback(request);
    }

    public InferenceResult regressionFallback(InferenceRequest request) {
        int leadDays = request.leadTimeDays() > 0 ? request.leadTimeDays() : 14;
        BigDecimal seasonal = computeSeasonality(request.velocity30d());
        BigDecimal adjustedVelocity = request.velocity30d().multiply(seasonal);
        BigDecimal required = adjustedVelocity.multiply(BigDecimal.valueOf(leadDays));
        BigDecimal available = request.onHand().add(request.incomingPo());
        BigDecimal recommended = required.subtract(available).max(BigDecimal.ZERO);

        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("model", "linear_regression_fallback");
        signals.put("leadDays", leadDays);
        signals.put("spikeDetected", adjustedVelocity.compareTo(request.velocity30d().multiply(new BigDecimal("1.25"))) > 0);

        BigDecimal confidence = request.velocity30d().signum() > 0
                ? new BigDecimal("0.72")
                : new BigDecimal("0.35");

        return new InferenceResult(recommended, adjustedVelocity, seasonal, confidence, signals);
    }

    public void applyToForecast(DemandForecast forecast, InferenceResult result) {
        forecast.setRecommendedPoQty(result.recommendedPoQty());
        forecast.setVelocity30d(result.velocity30d());
        forecast.setSeasonalityIndex(result.seasonalityIndex());
        forecast.setConfidenceScore(result.confidenceScore());
        forecast.setExternalSignals(result.externalSignals());
    }

    public List<DemandChartPoint> chartSeries(List<DemandForecast> forecasts) {
        return forecasts.stream()
                .map(f -> new DemandChartPoint(
                        f.getVariantId(),
                        f.getVelocity30d(),
                        f.getRecommendedPoQty(),
                        f.getSeasonalityIndex(),
                        f.getConfidenceScore(),
                        f.getCalculatedAt()))
                .toList();
    }

    private BigDecimal computeSeasonality(BigDecimal velocity) {
        int month = java.time.LocalDate.now().getMonthValue();
        double factor = 1.0 + 0.15 * Math.sin((month - 1) * Math.PI / 6.0);
        if (velocity.compareTo(new BigDecimal("5")) > 0) {
            factor += 0.05;
        }
        return BigDecimal.valueOf(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public record InferenceRequest(
            java.util.UUID variantId,
            String sku,
            BigDecimal velocity30d,
            BigDecimal onHand,
            BigDecimal incomingPo,
            int leadTimeDays,
            BigDecimal reorderPoint
    ) {
    }

    public record InferenceResult(
            BigDecimal recommendedPoQty,
            BigDecimal velocity30d,
            BigDecimal seasonalityIndex,
            BigDecimal confidenceScore,
            Map<String, Object> externalSignals
    ) {
    }

    public record DemandChartPoint(
            java.util.UUID variantId,
            BigDecimal historicalVelocity,
            BigDecimal forecastQty,
            BigDecimal seasonalityIndex,
            BigDecimal confidenceScore,
            java.time.Instant calculatedAt
    ) {
    }
}
