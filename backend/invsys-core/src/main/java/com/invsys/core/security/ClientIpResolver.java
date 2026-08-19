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

    public String resolveClientIp(HttpServletRequest request) {
        return resolve(request);
    }

    public String resolve(HttpServletRequest request) {
        return resolveDetailed(request).ip();
    }

    /**
     * Normalizes a raw address for audit storage. Blank, {@code unknown}, and
     * unparseable values become {@code "unknown"} so Geo-IP and CIDR checks stay defined.
     */
    public String normalizeOrUnknown(String ip) {
        String normalized = normalizeIp(ip);
        return normalized != null ? normalized : "unknown";
    }

    /**
     * When every hop is a configured trusted proxy (typical Docker SNAT:
     * browser → WMS nginx → API gateway → app), there is no public client IP
     * to fence against. Callers should treat {@link ResolvedClientIp#onPremMesh()}
     * as internal rather than denying STRICT_INTERNAL roles.
     */
    public ResolvedClientIp resolveDetailed(HttpServletRequest request) {
        if (request == null) {
            return ResolvedClientIp.UNKNOWN;
        }
        String remote = normalizeIp(request.getRemoteAddr());
        if (remote == null) {
            return ResolvedClientIp.UNKNOWN;
        }
        if (!isTrustedProxy(remote)) {
            return new ResolvedClientIp(remote, false);
        }
        String realIp = normalizeIp(request.getHeader("X-Real-IP"));
        if (realIp != null && !isTrustedProxy(realIp)) {
            return new ResolvedClientIp(realIp, false);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String candidate = normalizeIp(hops[i].trim());
                if (candidate != null && !isTrustedProxy(candidate)) {
                    return new ResolvedClientIp(candidate, false);
                }
            }
        }
        if (realIp != null) {
            return new ResolvedClientIp(realIp, true);
        }
        return new ResolvedClientIp(remote, true);
    }

    public record ResolvedClientIp(String ip, boolean onlyTrustedProxyHops) {
        static final ResolvedClientIp UNKNOWN = new ResolvedClientIp("unknown", false);

        public boolean onPremMesh() {
            return onlyTrustedProxyHops || isLoopback(ip);
        }
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

    public static boolean isLoopback(String ip) {
        InetAddress address = parseAddress(ip);
        return address != null && address.isLoopbackAddress();
    }

    public static boolean isPrivateNetwork(String ip) {
        InetAddress address = parseAddress(ip);
        return address != null && address.isSiteLocalAddress();
    }

    public static String suggestedCidr(String ip) {
        InetAddress address = parseAddress(ip);
        if (address == null) {
            return "";
        }
        return address.getHostAddress().toLowerCase(Locale.ROOT)
                + (address.getAddress().length == 16 ? "/128" : "/32");
    }

    public static String networkHint(String ip) {
        InetAddress address = parseAddress(ip);
        if (address == null) {
            return "Unknown network";
        }
        if (address.isLoopbackAddress()) {
            return "Local / loopback";
        }
        if (address.isSiteLocalAddress()) {
            return "Internal VPN / LAN";
        }
        return "Public Corporate Gateway";
    }

    private static InetAddress parseAddress(String ip) {
        String normalized = normalizeIp(ip);
        if (normalized == null) {
            return null;
        }
        try {
            return InetAddress.getByName(normalized);
        } catch (Exception ex) {
            return null;
        }
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
