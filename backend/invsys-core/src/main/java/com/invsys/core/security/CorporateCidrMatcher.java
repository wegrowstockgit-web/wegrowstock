package com.invsys.core.security;

import com.invsys.core.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.List;

public final class CorporateCidrMatcher {

    private CorporateCidrMatcher() {
    }

    public static boolean matches(String ip, List<String> cidrs) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip) || cidrs == null) {
            return false;
        }
        for (String cidr : cidrs) {
            String block = cidrPart(cidr);
            if (block.isBlank()) {
                continue;
            }
            try {
                if (new IpAddressMatcher(block).matches(ip)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // skip malformed CIDR stored by an older row
            }
        }
        return false;
    }

    public static List<String> normalizeOrReject(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String cidr = cidrPart(item);
            String label = labelPart(item);
            try {
                new IpAddressMatcher(cidr);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CIDR",
                        "Invalid corporate CIDR: " + cidr);
            }
            if (seen.contains(cidr)) {
                continue;
            }
            seen.add(cidr);
            cleaned.add(format(cidr, label));
        }
        return List.copyOf(cleaned);
    }

    public static String cidrPart(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        int hash = trimmed.indexOf('#');
        return (hash >= 0 ? trimmed.substring(0, hash) : trimmed).trim();
    }

    public static String labelPart(String raw) {
        if (raw == null) {
            return "";
        }
        int hash = raw.indexOf('#');
        return hash >= 0 ? raw.substring(hash + 1).trim() : "";
    }

    public static String format(String cidr, String label) {
        if (cidr == null || cidr.isBlank()) {
            return "";
        }
        if (label == null || label.isBlank()) {
            return cidr.trim();
        }
        return cidr.trim() + "#" + label.trim().replace('#', ' ');
    }
}
