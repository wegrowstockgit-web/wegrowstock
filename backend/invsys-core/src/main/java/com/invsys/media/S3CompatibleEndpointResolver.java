package com.invsys.media;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves default S3-compatible endpoints / path-style flags per cloud preset.
 * Explicit {@code invsys.media.endpoint} always wins.
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
            return new ResolvedEndpoint(Optional.of(configured), props.isPathStyleAccess());
        }

        return switch (provider) {
            case "AWS" -> new ResolvedEndpoint(Optional.empty(), false);
            case "GCP" -> new ResolvedEndpoint(Optional.of("https://storage.googleapis.com"), false);
            case "DIGITALOCEAN", "OCEANBLUE", "DO" -> {
                String region = props.getRegion() == null || props.getRegion().isBlank() ? "nyc3" : props.getRegion();
                yield new ResolvedEndpoint(Optional.of("https://" + region + ".digitaloceanspaces.com"), false);
            }
            case "AZURE" ->
                // Azure Blob has no native S3 API; require an S3-compatible gateway endpoint
                // (e.g. MinIO gateway, Ceph, or third-party adapter) via invsys.media.endpoint.
                    new ResolvedEndpoint(Optional.empty(), props.isPathStyleAccess());
            case "MINIO" -> new ResolvedEndpoint(Optional.of("http://localhost:9000"), true);
            default -> new ResolvedEndpoint(Optional.empty(), props.isPathStyleAccess());
        };
    }
}
