package com.invsys.support;

/**
 * Classifies Spring AI / Gemini failures so the copilot can fail fast on quota instead of
 * stacking retries (HyDE → structured → content) that stall the UI for tens of seconds.
 */
public final class SupportAiErrorClassifier {

    private SupportAiErrorClassifier() {
    }

    public static boolean isQuotaOrRateLimit(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            String message = cursor.getMessage();
            String type = cursor.getClass().getName();
            if (matchesQuota(message) || matchesQuota(type)) {
                return true;
            }
            String combined = cursor.toString();
            if (matchesQuota(combined)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static boolean matchesQuota(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("429")
                || lower.contains("resource_exhausted")
                || lower.contains("resource exhausted")
                || lower.contains("quota")
                || lower.contains("rate limit")
                || lower.contains("rate_limit")
                || lower.contains("too many requests");
    }
}
