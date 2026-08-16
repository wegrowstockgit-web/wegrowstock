package com.invsys.media;

import com.invsys.core.common.ApiException;
import org.springframework.http.HttpStatus;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves default S3-compatible endpoints / path-style flags per cloud preset.
 * Explicit {@code invsys.media.endpoint} always wins, but loopback / link-local /
 * RFC 1918 hosts are rejected unless the provider is MinIO or
 * {@code invsys.media.allow-private-endpoints=true}.
 */
public final class S3CompatibleEndpointResolver {

    private S3CompatibleEndpointResolver() {
    }

    public record ResolvedEndpoint(Optional<String> endpoint, boolean pathStyleAccess) {
    }

    public static ResolvedEndpoint resolve(MediaStorageProperties props) {
        String provider = props.getProvider() == null ? "CUSTOM" : props.getProvider().trim().toUpperCase(Locale.ROOT);
        String configured = props.getEndpoint() == null ? "" : props.getEndpoint().trim();

        if (!configured.isEmpty()) {
            assertEndpointAllowed(configured, props, provider);
            return new ResolvedEndpoint(Optional.of(configured), props.isPathStyleAccess());
        }

        ResolvedEndpoint resolved = switch (provider) {
            case "AWS" -> new ResolvedEndpoint(Optional.empty(), false);
            case "GCP" -> new ResolvedEndpoint(Optional.of("https://storage.googleapis.com"), false);
            case "DIGITALOCEAN", "OCEANBLUE", "DO" -> {
                String region = props.getRegion() == null || props.getRegion().isBlank() ? "nyc3" : props.getRegion().trim().toLowerCase(Locale.ROOT);
                if (!region.matches("^[a-z0-9][a-z0-9-]{0,31}$")) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_REGION",
                            "DigitalOcean region must be a short alphanumeric token");
                }
                yield new ResolvedEndpoint(Optional.of("https://" + region + ".digitaloceanspaces.com"), false);
            }
            case "AZURE" ->
                // Azure Blob has no native S3 API; require an S3-compatible gateway endpoint
                // (e.g. MinIO gateway, Ceph, or third-party adapter) via invsys.media.endpoint.
                    new ResolvedEndpoint(Optional.empty(), props.isPathStyleAccess());
            case "MINIO" -> new ResolvedEndpoint(Optional.of("http://localhost:9000"), true);
            default -> new ResolvedEndpoint(Optional.empty(), props.isPathStyleAccess());
        };
        resolved.endpoint().ifPresent(url -> assertEndpointAllowed(url, props, provider));
        return resolved;
    }

    static void assertEndpointAllowed(String rawUrl, MediaStorageProperties props, String provider) {
        if (allowsPrivate(props, provider)) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_ENDPOINT",
                    "Media storage endpoint is not a valid URL");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_ENDPOINT",
                    "Media storage endpoint host is required");
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized)
                || "metadata".equals(normalized)
                || "metadata.google.internal".equals(normalized)
                || normalized.endsWith(".localhost")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_ENDPOINT",
                    "Media storage endpoint must not use loopback or metadata hosts");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (MediaUrlValidator.isBlockedAddress(address)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_ENDPOINT",
                            "Media storage endpoint must not resolve to a private or loopback address");
                }
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ignored) {
            // Unresolvable host at config time is not treated as a live SSRF target.
        }
    }

    private static boolean allowsPrivate(MediaStorageProperties props, String provider) {
        return props.isAllowPrivateEndpoints() || "MINIO".equalsIgnoreCase(provider);
    }
}
