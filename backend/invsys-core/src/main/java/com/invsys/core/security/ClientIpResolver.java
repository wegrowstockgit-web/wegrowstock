package com.invsys.core.security;

import com.invsys.config.ActuatorProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the client IP without blindly trusting {@code X-Forwarded-For}.
 * Forwarded headers are honored only when the immediate peer is a configured
 * trusted proxy (actuator scrape CIDRs plus {@code invsys.security.trusted-proxy-cidrs}).
 */
@Component
public class ClientIpResolver {

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(
            ActuatorProperties actuatorProperties,
            @Value("${invsys.security.trusted-proxy-cidrs:}") String extraTrustedCidrs) {
        List<IpAddressMatcher> built = new ArrayList<>();
        for (String cidr : actuatorProperties.resolvedScrapeAllowedCidrs()) {
            addCidr(built, cidr);
        }
        if (extraTrustedCidrs != null && !extraTrustedCidrs.isBlank()) {
            for (String cidr : extraTrustedCidrs.split(",")) {
                addCidr(built, cidr.trim());
            }
        }
        this.trustedProxies = List.copyOf(built);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remote = normalizeIp(request.getRemoteAddr());
        if (remote == null) {
            return "unknown";
        }
        if (!isTrustedProxy(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        for (String hop : forwarded.split(",")) {
            String candidate = normalizeIp(hop.trim());
            if (candidate != null) {
                return candidate;
            }
        }
        return remote;
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    private static void addCidr(List<IpAddressMatcher> into, String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return;
        }
        into.add(new IpAddressMatcher(cidr.trim()));
    }

    static String normalizeIp(String raw) {
        if (raw == null || raw.isBlank() || "unknown".equalsIgnoreCase(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        int zone = value.indexOf('%');
        if (zone > 0) {
            value = value.substring(0, zone);
        }
        try {
            return InetAddress.getByName(value).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return null;
        }
    }
}
