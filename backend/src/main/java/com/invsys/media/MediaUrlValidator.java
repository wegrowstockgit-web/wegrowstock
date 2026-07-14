package com.invsys.media;

import com.invsys.common.ApiException;
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
 * and public HTTPS hosts that resolve to public addresses only.
 */
@Component
public class MediaUrlValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "metadata.google.internal", "metadata");

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
        if (isPrivateOrLocalAddress(host)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL",
                    "URL must not resolve to a private or loopback address");
        }
        return uri.normalize().toString();
    }

    private boolean isPrivateOrLocalAddress(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return true;
                }
                byte[] b = address.getAddress();
                if (b.length == 4) {
                    int a0 = b[0] & 0xFF;
                    int a1 = b[1] & 0xFF;
                    // 100.64.0.0/10 CGNAT, 169.254.0.0/16 link-local already covered
                    if (a0 == 100 && a1 >= 64 && a1 <= 127) {
                        return true;
                    }
                }
            }
            return false;
        } catch (UnknownHostException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL", "Unable to resolve host");
        }
    }
}
