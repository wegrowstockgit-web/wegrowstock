package com.invsys.core.security;

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
 * Forwarded headers are honored only when the immediate peer is in
 * {@code invsys.security.trusted-proxy-cidrs}. Hops are peeled from the
 * <em>right</em> so a client-supplied leftmost XFF value cannot spoof the IP.
 */
@Component
public class ClientIpResolver {

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(
            @Value("${invsys.security.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        List<IpAddressMatcher> built = new ArrayList<>();
        if (trustedProxyCidrs != null && !trustedProxyCidrs.isBlank()) {
            for (String cidr : trustedProxyCidrs.split(",")) {
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
        String realIp = normalizeIp(request.getHeader("X-Real-IP"));
        if (realIp != null && !isTrustedProxy(realIp)) {
            return realIp;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return realIp != null ? realIp : remote;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String candidate = normalizeIp(hops[i].trim());
            if (candidate != null && !isTrustedProxy(candidate)) {
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
