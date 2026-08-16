package com.invsys.pos;

import java.util.List;
import java.util.Locale;

/**
 * Resolves POS language and currency from WMS organization settings, the cashier
 * profile, and the place the register is opened (browser locale / timezone).
 *
 * <p>When Retail POS is entitled, WMS organization language and base currency win.
 * Place detection still drives tax-region hints and is the fallback when WMS
 * has not configured a value.
 */
public final class PosLocaleResolver {

    public static final String DEFAULT_LANGUAGE = "en";
    public static final String DEFAULT_CURRENCY = "USD";

    private PosLocaleResolver() {
    }

    public static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int comma = token.indexOf(',');
        if (comma > 0) {
            token = token.substring(0, comma).trim();
        }
        if (token.startsWith("es")) {
            return "es";
        }
        if (token.startsWith("fr")) {
            return "fr";
        }
        if (token.startsWith("en")) {
            return "en";
        }
        return null;
    }

    public static String resolveLanguage(String organization, String user, String place) {
        for (String candidate : List.of(
                nullToEmpty(organization), nullToEmpty(user), nullToEmpty(place))) {
            String normalized = normalizeLanguage(candidate);
            if (normalized != null) {
                return normalized;
            }
        }
        return DEFAULT_LANGUAGE;
    }

    public static String languageSource(String organization, String user, String place) {
        if (normalizeLanguage(organization) != null) {
            return "ORGANIZATION";
        }
        if (normalizeLanguage(user) != null) {
            return "USER";
        }
        if (normalizeLanguage(place) != null) {
            return "PLACE";
        }
        return "DEFAULT";
    }

    public static String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        return code.matches("[A-Z]{3}") ? code : null;
    }

    /**
     * WMS company currency is authoritative. Place currency is only used when
     * the workspace has not configured one.
     */
    public static String resolveCurrency(String wms, String place) {
        String configured = normalizeCurrency(wms);
        if (configured != null) {
            return configured;
        }
        String detected = normalizeCurrency(place);
        if (detected != null) {
            return detected;
        }
        return DEFAULT_CURRENCY;
    }

    public static String currencySource(String wms, String place) {
        if (normalizeCurrency(wms) != null) {
            return "WMS";
        }
        if (normalizeCurrency(place) != null) {
            return "PLACE";
        }
        return "DEFAULT";
    }

    public static String inferPlaceCurrency(String acceptLanguage, String timezone) {
        String zone = timezone == null ? "" : timezone;
        if (zone.startsWith("America/Mexico") || zone.equals("America/Tijuana") || zone.equals("America/Cancun")) {
            return "MXN";
        }
        if (zone.startsWith("Europe/London") || zone.equals("Europe/Belfast") || zone.equals("Europe/Guernsey")) {
            return "GBP";
        }
        if (zone.startsWith("Europe/")) {
            return "EUR";
        }
        if (zone.startsWith("America/Toronto")
                || zone.startsWith("America/Vancouver")
                || zone.equals("America/Edmonton")
                || zone.equals("America/Winnipeg")) {
            return "CAD";
        }
        if (zone.startsWith("Australia/")) {
            return "AUD";
        }

        String lang = acceptLanguage == null ? "" : acceptLanguage.toLowerCase(Locale.ROOT);
        if (lang.contains("mx")) {
            return "MXN";
        }
        if (lang.contains("en-gb") || lang.contains("en-uk")) {
            return "GBP";
        }
        if (lang.contains("en-ca") || lang.contains("fr-ca")) {
            return "CAD";
        }
        if (lang.contains("en-au")) {
            return "AUD";
        }
        if (lang.startsWith("fr") || lang.contains("es-es") || lang.contains("de-") || lang.contains("it-")) {
            return "EUR";
        }
        return DEFAULT_CURRENCY;
    }

    public static String taxRegionHint(String acceptLanguage, String timezone, String placeCurrency) {
        String zone = timezone == null ? "" : timezone;
        if (zone.contains("Mexico") || zone.equals("America/Tijuana") || zone.equals("America/Cancun")) {
            return "MX";
        }
        if ("MXN".equalsIgnoreCase(placeCurrency)) {
            return "MX";
        }
        String lang = acceptLanguage == null ? "" : acceptLanguage.toLowerCase(Locale.ROOT);
        if (lang.contains("mx")) {
            return "MX";
        }
        return "US";
    }

    public static String localeTag(String language, String currency, String taxRegion) {
        String lang = language == null ? DEFAULT_LANGUAGE : language;
        if ("MX".equals(taxRegion) || "MXN".equalsIgnoreCase(currency)) {
            return "es".equals(lang) ? "es-MX" : lang + "-MX";
        }
        if ("EUR".equalsIgnoreCase(currency) && "fr".equals(lang)) {
            return "fr-FR";
        }
        if ("GBP".equalsIgnoreCase(currency)) {
            return "en".equals(lang) ? "en-GB" : lang + "-GB";
        }
        if ("CAD".equalsIgnoreCase(currency)) {
            return "fr".equals(lang) ? "fr-CA" : "en-CA";
        }
        return switch (lang) {
            case "es" -> "es-ES";
            case "fr" -> "fr-FR";
            default -> "en-US";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
