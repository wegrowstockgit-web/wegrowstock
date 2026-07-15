package com.invsys.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * GS1-128 / GS1 DataMatrix Application Identifier parser.
 * Supports AI 01 (GTIN), AI 10 (lot/batch), AI 17 (expiry YYMMDD),
 * AI 21 (serial), AI 30 (variable count of items).
 * FNC1 separators accepted as ASCII 29 or literal {@code ]C1}/{@code {GS}}.
 */
public final class Gs1BarcodeParser {

    private static final char FNC1 = '\u001d';
    private static final Set<String> FIXED_DATE_AIS = Set.of("17", "11", "13", "15");
    private static final Set<String> VARIABLE_AIS = Set.of("10", "21", "30");

    private Gs1BarcodeParser() {
    }

    public record Gs1Elements(
            String raw,
            String gtin,
            String lot,
            LocalDate expiry,
            String serial,
            BigDecimal variableQuantity,
            Map<String, String> all
    ) {
        public boolean hasCompositeData() {
            return lot != null || expiry != null || serial != null || variableQuantity != null;
        }
    }

    public static Optional<Gs1Elements> parse(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        String normalized = barcode.trim()
                .replace("]C1", "")
                .replace("{GS}", String.valueOf(FNC1))
                .replace("(01)", "01")
                .replace("(10)", "10")
                .replace("(17)", "17")
                .replace("(21)", "21")
                .replace("(30)", "30");

        // Strip human-readable parentheses form: (01)123...(17)250101(10)LOT
        if (normalized.contains("(") && normalized.contains(")")) {
            normalized = normalized.replace("(", "").replace(")", "");
        }

        if (!looksLikeGs1(normalized)) {
            return Optional.empty();
        }

        Map<String, String> elements = new LinkedHashMap<>();
        int i = 0;
        while (i < normalized.length()) {
            if (normalized.charAt(i) == FNC1) {
                i++;
                continue;
            }
            if (i + 2 > normalized.length()) {
                break;
            }
            String ai = normalized.substring(i, i + 2);
            i += 2;
            switch (ai) {
                case "01" -> {
                    if (i + 14 > normalized.length()) {
                        return Optional.empty();
                    }
                    elements.put("01", normalized.substring(i, i + 14));
                    i += 14;
                }
                case "17", "11", "13", "15" -> {
                    if (i + 6 > normalized.length()) {
                        return Optional.empty();
                    }
                    elements.put(ai, normalized.substring(i, i + 6));
                    i += 6;
                }
                case "10", "21", "30" -> {
                    int end = indexOfFnc1OrEnd(normalized, i);
                    end = Math.min(end, findNextFixedAiBoundary(normalized, i));
                    String value = normalized.substring(i, end);
                    if (value.isEmpty()) {
                        return Optional.empty();
                    }
                    if ("30".equals(ai)) {
                        if (value.length() > 8 || !value.chars().allMatch(Character::isDigit)) {
                            return Optional.empty();
                        }
                    } else if (value.length() > 20) {
                        return Optional.empty();
                    }
                    elements.put(ai, value);
                    i = end;
                    if (i < normalized.length() && normalized.charAt(i) == FNC1) {
                        i++;
                    }
                }
                default -> {
                    // Unknown AI — abort composite parse
                    return Optional.empty();
                }
            }
        }

        if (!elements.containsKey("01") && !elements.containsKey("10") && !elements.containsKey("17")) {
            return Optional.empty();
        }

        LocalDate expiry = null;
        if (elements.containsKey("17")) {
            expiry = parseYyMmDd(elements.get("17"));
        }

        BigDecimal variableQuantity = null;
        if (elements.containsKey("30")) {
            variableQuantity = new BigDecimal(elements.get("30"));
        }

        return Optional.of(new Gs1Elements(
                barcode.trim(),
                elements.get("01"),
                elements.get("10"),
                expiry,
                elements.get("21"),
                variableQuantity,
                Map.copyOf(elements)));
    }

    /** Lookup key: prefer GTIN (AI 01), else original string. */
    public static String lookupKey(String barcode) {
        return parse(barcode).map(e -> e.gtin() != null ? e.gtin() : barcode.trim()).orElse(barcode.trim());
    }

    private static boolean looksLikeGs1(String value) {
        if (value.startsWith("01") && value.length() >= 16) {
            return true;
        }
        return value.contains(String.valueOf(FNC1))
                || value.matches("^01\\d{14}.*")
                || value.matches(".*17\\d{6}.*");
    }

    private static int indexOfFnc1OrEnd(String s, int from) {
        int idx = s.indexOf(FNC1, from);
        return idx < 0 ? s.length() : idx;
    }

    private static int findNextFixedAiBoundary(String s, int from) {
        for (int j = from + 1; j + 2 <= s.length(); j++) {
            String maybe = s.substring(j, j + 2);
            if ("01".equals(maybe) || FIXED_DATE_AIS.contains(maybe) || VARIABLE_AIS.contains(maybe)) {
                return j;
            }
        }
        return s.length();
    }

    private static LocalDate parseYyMmDd(String yyMmDd) {
        int yy = Integer.parseInt(yyMmDd.substring(0, 2));
        int mm = Integer.parseInt(yyMmDd.substring(2, 4));
        int dd = Integer.parseInt(yyMmDd.substring(4, 6));
        int year = yy >= 70 ? 1900 + yy : 2000 + yy;
        if (dd == 0) {
            dd = 1;
        }
        return LocalDate.of(year, mm, dd);
    }
}
