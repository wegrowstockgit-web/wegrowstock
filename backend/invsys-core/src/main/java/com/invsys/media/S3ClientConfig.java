package com.invsys.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Locale;

@Configuration
public class S3ClientConfig {

    private static final Logger log = LoggerFactory.getLogger(S3ClientConfig.class);

    @Bean(destroyMethod = "close")
    public S3Client s3Client(MediaStorageProperties properties) {
        ClientParts parts = buildParts(properties);
        var builder = S3Client.builder()
                .region(parts.region())
                .credentialsProvider(parts.credentials())
                .serviceConfiguration(parts.serviceConfiguration());
        parts.endpoint().ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));
        log.info("S3 media client provider={} region={} endpoint={} pathStyle={} bucket={}",
                parts.provider(),
                parts.region().id(),
                parts.endpoint().orElse("(aws-default)"),
                parts.pathStyle(),
                properties.getBucket());
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(MediaStorageProperties properties) {
        // Presigner uses the browser-facing endpoint so PUT URLs resolve outside the Docker network.
        ClientParts parts = buildParts(properties, true);
        var builder = S3Presigner.builder()
                .region(parts.region())
                .credentialsProvider(parts.credentials())
                .serviceConfiguration(parts.serviceConfiguration());
        parts.endpoint().ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));
        return builder.build();
    }

    private static ClientParts buildParts(MediaStorageProperties properties) {
        return buildParts(properties, false);
    }

    private static ClientParts buildParts(MediaStorageProperties properties, boolean forPresign) {
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "invsys.media.access-key and invsys.media.secret-key are required for S3-compatible storage");
        }

        MediaStorageProperties effective = properties;
        if (forPresign) {
            String publicEp = properties.getPublicEndpoint();
            if (publicEp != null && !publicEp.isBlank()) {
                effective = copyWithEndpoint(properties, publicEp.trim());
            }
        }

        S3CompatibleEndpointResolver.ResolvedEndpoint resolved = S3CompatibleEndpointResolver.resolve(effective);
        String region = properties.getRegion() == null || properties.getRegion().isBlank()
                ? "us-east-1"
                : properties.getRegion();
        String provider = properties.getProvider() == null ? "CUSTOM" : properties.getProvider().toUpperCase(Locale.ROOT);
        if ("AZURE".equals(provider) && resolved.endpoint().isEmpty()) {
            throw new IllegalStateException(
                    "invsys.media.provider=AZURE requires invsys.media.endpoint pointing at an S3-compatible gateway");
        }

        return new ClientParts(
                provider,
                Region.of(region),
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)),
                S3Configuration.builder().pathStyleAccessEnabled(resolved.pathStyleAccess()).build(),
                resolved.endpoint(),
                resolved.pathStyleAccess());
    }

    private static MediaStorageProperties copyWithEndpoint(MediaStorageProperties source, String endpoint) {
        MediaStorageProperties copy = new MediaStorageProperties();
        copy.setProvider(source.getProvider());
        copy.setBucket(source.getBucket());
        copy.setRegion(source.getRegion());
        copy.setEndpoint(endpoint);
        copy.setPublicEndpoint(source.getPublicEndpoint());
        copy.setAccessKey(source.getAccessKey());
        copy.setSecretKey(source.getSecretKey());
        copy.setPathStyleAccess(source.isPathStyleAccess());
        copy.setCreateBucketIfMissing(source.isCreateBucketIfMissing());
        copy.setMaxBytes(source.getMaxBytes());
        copy.setAvatarMaxBytes(source.getAvatarMaxBytes());
        copy.setEvidenceMaxBytes(source.getEvidenceMaxBytes());
        return copy;
    }

    private record ClientParts(
            String provider,
            Region region,
            StaticCredentialsProvider credentials,
            S3Configuration serviceConfiguration,
            java.util.Optional<String> endpoint,
            boolean pathStyle
    ) {
    }
}
