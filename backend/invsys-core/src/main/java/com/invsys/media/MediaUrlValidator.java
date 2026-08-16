package com.invsys.media;

import com.invsys.core.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks SSRF-prone external media URLs. Allows relative authenticated media paths
 * and public HTTPS hosts that resolve only to public addresses.
 * <p>
 * Resolution checks every A/AAAA answer against RFC 1918, link-local (incl. cloud
 * metadata {@code 169.254.169.254}), loopback, CGNAT, and unique-local IPv6.
 */
@Component
public class MediaUrlValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "metadata.google.internal", "metadata");

    public void assertHttpsPublicUrl(String rawUrl, String errorCode, String message) {
        try {
            validateAndNormalize(rawUrl);
        } catch (ApiException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    public void assertAllowedHttpsHost(String rawUrl, Set<String> allowedHosts, String errorCode) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "URL is required");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "Malformed URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "Only https URLs with a host are allowed");
        }
        String host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "Host is not allowed");
        }
        if (resolvesToBlockedAddress(host)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode,
                    "URL must not resolve to a private or loopback address");
        }
    }

    public String validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL", "url is required");
        }
        String trimmed = rawUrl.trim();
        if (trimmed.length() > 1024) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL", "url must be <= 1024 chars");
        }

        // First-party authenticated content path
        if (trimmed.startsWith("/api/v1/media/") && trimmed.contains("/content")) {
            if (trimmed.contains("..") || trimmed.contains("\\")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL", "Invalid media path");
            }
            return trimmed;
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL", "Malformed URL");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL",
                    "Only https:// or /api/v1/media/{id}/content URLs are allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL", "URL host is required");
        }
        String host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(host) || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL", "Host is not allowed");
        }
        if (resolvesToBlockedAddress(host)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL",
                    "URL must not resolve to a private or loopback address");
        }
        return uri.normalize().toString();
    }

    private boolean resolvesToBlockedAddress(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL", "Unable to resolve host");
        }
    }

    /**
     * Strict blocklist: loopback, RFC 1918, link-local (169.254/16 incl. metadata),
     * CGNAT, multicast, and IPv6 ULA / link-local. Also unwraps IPv4-mapped IPv6.
     */
    static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] raw = address.getAddress();
        if (raw.length == 16 && isIpv4Mapped(raw)) {
            raw = new byte[]{raw[12], raw[13], raw[14], raw[15]};
        }

        if (raw.length == 4) {
            return isBlockedIpv4(raw);
        }
        if (raw.length == 16) {
            return isBlockedIpv6(raw);
        }
        return true;
    }

    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
    }

    private static boolean isBlockedIpv4(byte[] b) {
        int a0 = b[0] & 0xFF;
        int a1 = b[1] & 0xFF;
        // 0.0.0.0/8
        if (a0 == 0) {
            return true;
        }
        // 10.0.0.0/8
        if (a0 == 10) {
            return true;
        }
        // 127.0.0.0/8
        if (a0 == 127) {
            return true;
        }
        // 169.254.0.0/16 (link-local / cloud metadata 169.254.169.254)
        if (a0 == 169 && a1 == 254) {
            return true;
        }
        // 172.16.0.0/12
        if (a0 == 172 && a1 >= 16 && a1 <= 31) {
            return true;
        }
        // 192.168.0.0/16
        if (a0 == 192 && a1 == 168) {
            return true;
        }
        // 100.64.0.0/10 CGNAT
        if (a0 == 100 && a1 >= 64 && a1 <= 127) {
            return true;
        }
        return false;
    }

    private static boolean isBlockedIpv6(byte[] b) {
        // ::1 already covered by isLoopbackAddress; fe80::/10 link-local by isLinkLocalAddress.
        // fc00::/7 unique local
        int b0 = b[0] & 0xFF;
        return (b0 & 0xFE) == 0xFC;
    }
}
