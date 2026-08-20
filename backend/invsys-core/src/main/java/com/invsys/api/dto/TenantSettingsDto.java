package com.invsys.api.dto;

import com.invsys.core.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tenant settings persisted in {@code tenant_settings} (JSONB plus typed columns).
 * Exposed on GET/PATCH/PUT {@code /api/v1/settings}.
 */
public record TenantSettingsDto(
        String posReceiptHeader,
        String posReceiptFooter,
        String posDefaultCurrency,
        Boolean posRequireBlindCloseout,
        Boolean posEnableCfdiInvoicing,
        Integer desktopIdleTimeoutMinutes
) {
    public static final String KEY_RECEIPT_HEADER = "pos_receipt_header";
    public static final String KEY_RECEIPT_FOOTER = "pos_receipt_footer";
    public static final String KEY_DEFAULT_CURRENCY = "pos_default_currency";
    public static final String KEY_REQUIRE_BLIND_CLOSEOUT = "pos_require_blind_closeout";
    public static final String KEY_ENABLE_CFDI = "pos_enable_cfdi_invoicing";
    public static final String KEY_DESKTOP_IDLE_TIMEOUT_MINUTES = "desktop_idle_timeout_minutes";

    public static final Set<String> ALLOWED_CURRENCIES = Set.of("USD", "MXN");
    public static final Set<Integer> ALLOWED_DESKTOP_IDLE_MINUTES = Set.of(15, 30, 60, 240);
    public static final int DEFAULT_DESKTOP_IDLE_MINUTES = 30;
    public static final int MAX_RECEIPT_TEXT = 2000;

    public static TenantSettingsDto defaults() {
        return new TenantSettingsDto("", "", "USD", false, false, DEFAULT_DESKTOP_IDLE_MINUTES);
    }

    public static TenantSettingsDto fromSettingsMap(Map<String, Object> map) {
        TenantSettingsDto fallback = defaults();
        if (map == null || map.isEmpty()) {
            return fallback;
        }
        return new TenantSettingsDto(
                stringOr(map.get(KEY_RECEIPT_HEADER), fallback.posReceiptHeader()),
                stringOr(map.get(KEY_RECEIPT_FOOTER), fallback.posReceiptFooter()),
                normalizeCurrency(stringOr(map.get(KEY_DEFAULT_CURRENCY), fallback.posDefaultCurrency()), false),
                boolOr(map.get(KEY_REQUIRE_BLIND_CLOSEOUT), fallback.posRequireBlindCloseout()),
                boolOr(map.get(KEY_ENABLE_CFDI), fallback.posEnableCfdiInvoicing()),
                normalizeTimeout(map.get(KEY_DESKTOP_IDLE_TIMEOUT_MINUTES), false));
    }

    public void writeTo(Map<String, Object> map) {
        if (map == null) {
            return;
        }
        map.put(KEY_RECEIPT_HEADER, posReceiptHeader() != null ? posReceiptHeader() : "");
        map.put(KEY_RECEIPT_FOOTER, posReceiptFooter() != null ? posReceiptFooter() : "");
        map.put(KEY_DEFAULT_CURRENCY, posDefaultCurrency() != null ? posDefaultCurrency() : "USD");
        map.put(KEY_REQUIRE_BLIND_CLOSEOUT, Boolean.TRUE.equals(posRequireBlindCloseout()));
        map.put(KEY_ENABLE_CFDI, Boolean.TRUE.equals(posEnableCfdiInvoicing()));
        map.put(KEY_DESKTOP_IDLE_TIMEOUT_MINUTES,
                desktopIdleTimeoutMinutes() != null ? desktopIdleTimeoutMinutes() : DEFAULT_DESKTOP_IDLE_MINUTES);
    }

    /**
     * Validates and normalizes POS / desktop-idle keys present in {@code patch} onto {@code settings}.
     */
    public static void applyPatch(Map<String, Object> settings, Map<String, Object> patch) {
        if (settings == null || patch == null) {
            return;
        }
        if (patch.containsKey(KEY_RECEIPT_HEADER)) {
            settings.put(KEY_RECEIPT_HEADER, clipReceiptText(patch.get(KEY_RECEIPT_HEADER)));
        }
        if (patch.containsKey(KEY_RECEIPT_FOOTER)) {
            settings.put(KEY_RECEIPT_FOOTER, clipReceiptText(patch.get(KEY_RECEIPT_FOOTER)));
        }
        if (patch.containsKey(KEY_DEFAULT_CURRENCY)) {
            settings.put(KEY_DEFAULT_CURRENCY, normalizeCurrency(stringOr(patch.get(KEY_DEFAULT_CURRENCY), "USD"), true));
        }
        if (patch.containsKey(KEY_REQUIRE_BLIND_CLOSEOUT)) {
            settings.put(KEY_REQUIRE_BLIND_CLOSEOUT, boolOr(patch.get(KEY_REQUIRE_BLIND_CLOSEOUT), false));
        }
        if (patch.containsKey(KEY_ENABLE_CFDI)) {
            settings.put(KEY_ENABLE_CFDI, boolOr(patch.get(KEY_ENABLE_CFDI), false));
        }
        if (patch.containsKey(KEY_DESKTOP_IDLE_TIMEOUT_MINUTES)) {
            settings.put(KEY_DESKTOP_IDLE_TIMEOUT_MINUTES,
                    normalizeTimeout(patch.get(KEY_DESKTOP_IDLE_TIMEOUT_MINUTES), true));
        }
    }

    public static void putDefaults(Map<String, Object> settings) {
        defaults().writeTo(settings);
    }

    static String clipReceiptText(Object raw) {
        String value = raw == null ? "" : String.valueOf(raw);
        if (value.length() > MAX_RECEIPT_TEXT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "POS_RECEIPT_TOO_LONG",
                    "Receipt header/footer cannot exceed " + MAX_RECEIPT_TEXT + " characters");
        }
        return value;
    }

    static String normalizeCurrency(String raw, boolean rejectUnknown) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (ALLOWED_CURRENCIES.contains(code)) {
            return code;
        }
        if (rejectUnknown) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "POS_CURRENCY_UNSUPPORTED",
                    "POS default currency must be USD or MXN");
        }
        return "USD";
    }

    public static int normalizeTimeout(Object raw, boolean rejectUnknown) {
        Integer parsed = parsePositiveInt(raw);
        if (parsed != null && ALLOWED_DESKTOP_IDLE_MINUTES.contains(parsed)) {
            return parsed;
        }
        if (rejectUnknown) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DESKTOP_IDLE_TIMEOUT_UNSUPPORTED",
                    "Desktop idle timeout must be 15, 30, 60, or 240 minutes");
        }
        return DEFAULT_DESKTOP_IDLE_MINUTES;
    }

    private static Integer parsePositiveInt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringOr(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = String.valueOf(raw);
        return value.isBlank() ? fallback : value;
    }

    private static boolean boolOr(Object raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }
}
