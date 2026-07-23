package com.invsys.documents;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class DocumentAddressFormatter {

    private DocumentAddressFormatter() {
    }

    static String toHtml(Map<String, Object> address) {
        if (address == null || address.isEmpty()) {
            return "—";
        }
        String line1 = str(address, "line1", "address1", "street");
        String line2 = str(address, "line2", "address2");
        String city = str(address, "city");
        String region = str(address, "region", "state", "province");
        String postal = str(address, "postalCode", "postal_code", "zip");
        String country = str(address, "country");
        String cityLine = Stream.of(city, region, postal)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
        return Stream.of(line1, line2, cityLine, country)
                .filter(s -> !s.isBlank())
                .map(DocumentAddressFormatter::escape)
                .collect(Collectors.joining("<br/>"));
    }

    private static String str(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private static String escape(String raw) {
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
