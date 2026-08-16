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
            if (cidr == null || cidr.isBlank()) {
                continue;
            }
            try {
                if (new IpAddressMatcher(cidr.trim()).matches(ip)) {
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
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String cidr = item.trim();
            try {
                new IpAddressMatcher(cidr);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CIDR",
                        "Invalid corporate CIDR: " + cidr);
            }
            if (!cleaned.contains(cidr)) {
                cleaned.add(cidr);
            }
        }
        return List.copyOf(cleaned);
    }
}
