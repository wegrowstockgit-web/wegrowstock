package com.invsys.core.security;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves a client IP to a human-readable region for IAM audit events.
 * Documentation-range TEST-NET addresses map to stable mock cities so tests
 * and local demos stay deterministic. Private, loopback, and link-local
 * addresses are labeled as the corporate network. Swap {@link #resolvePublic(String)}
 * later for MaxMind GeoLite2 or an external Geo-IP API.
 */
@Service
public class GeoIpService {

    public static final String CORPORATE_NETWORK = "Corporate Network";
    public static final String UNKNOWN_REGION = "Unknown Region";

    private static final String[] MOCK_PUBLIC_REGIONS = {
            "Chicago, IL, US",
            "Austin, TX, US",
            "Seattle, WA, US",
            "Toronto, ON, CA"
    };

    private static final IpAddressMatcher TEST_NET_1 = new IpAddressMatcher("192.0.2.0/24");
    private static final IpAddressMatcher TEST_NET_2 = new IpAddressMatcher("198.51.100.0/24");
    private static final IpAddressMatcher TEST_NET_3 = new IpAddressMatcher("203.0.113.0/24");

    public String resolveLocation(String ipAddress) {
        String ip = ClientIpResolver.normalizeIp(ipAddress);
        if (ip == null) {
            return UNKNOWN_REGION;
        }
        InetAddress address = parse(ip);
        if (address == null) {
            return UNKNOWN_REGION;
        }
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return CORPORATE_NETWORK;
        }
        if (matches(TEST_NET_3, ip)) {
            return "Dallas, TX, US";
        }
        if (matches(TEST_NET_2, ip)) {
            return "London, England, GB";
        }
        if (matches(TEST_NET_1, ip)) {
            return "Sydney, NSW, AU";
        }
        return resolvePublic(ip);
    }

    /**
     * True when the current login's /24 (IPv4) or /64 (IPv6) or resolved location
     * differs from the previous successful login. First-ever login is not anomalous.
     */
    public boolean isNewNetworkOrLocation(Map<String, Object> previousDiff, String currentIp, String currentLocation) {
        if (previousDiff == null || previousDiff.isEmpty()) {
            return false;
        }
        String previousIp = stringValue(previousDiff.get("ip"));
        String previousLocation = stringValue(previousDiff.get("location"));
        if (previousIp.isBlank() && previousLocation.isBlank()) {
            return false;
        }
        boolean subnetChanged = !previousIp.isBlank() && !subnetKey(previousIp).equals(subnetKey(currentIp));
        boolean locationChanged = !previousLocation.isBlank()
                && !previousLocation.equalsIgnoreCase(currentLocation == null ? "" : currentLocation.trim());
        return subnetChanged || locationChanged;
    }

    String subnetKey(String ipAddress) {
        InetAddress address = parse(ClientIpResolver.normalizeIp(ipAddress));
        if (address == null) {
            return "unknown";
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + ".0/24";
        }
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            prefix.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
            if (i % 2 == 1 && i < 7) {
                prefix.append(':');
            }
        }
        return prefix.append("::/64").toString();
    }

    /**
     * Lightweight public-IP fallback. Replace with MaxMind GeoLite2 (or similar)
     * without changing {@link #resolveLocation(String)} callers.
     */
    protected String resolvePublic(String ip) {
        int idx = Math.floorMod(ip.hashCode(), MOCK_PUBLIC_REGIONS.length);
        return MOCK_PUBLIC_REGIONS[idx];
    }

    private static boolean matches(IpAddressMatcher matcher, String ip) {
        try {
            return matcher.matches(ip);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static InetAddress parse(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(ip);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
