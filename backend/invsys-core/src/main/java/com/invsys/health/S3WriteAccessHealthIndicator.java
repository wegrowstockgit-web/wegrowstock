package com.invsys.health;

import com.invsys.media.MediaStorageProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Verifies the configured media bucket accepts a short-lived write (put + delete).
 */
@Component("s3Write")
public class S3WriteAccessHealthIndicator implements HealthIndicator {

    private static final String KEY_PREFIX = "healthchecks/write-probe-";

    private final S3Client s3Client;
    private final MediaStorageProperties properties;

    public S3WriteAccessHealthIndicator(S3Client s3Client, MediaStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public Health health() {
        String bucket = properties.getBucket();
        String key = KEY_PREFIX + UUID.randomUUID() + ".txt";
        long started = System.nanoTime();
        try {
            byte[] payload = "ok".getBytes(StandardCharsets.UTF_8);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("text/plain")
                            .build(),
                    RequestBody.fromBytes(payload));
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            return Health.up()
                    .withDetail("bucket", bucket)
                    .withDetail("provider", properties.getProvider())
                    .withDetail("writeLatencyMs", latencyMs)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("bucket", bucket)
                    .withDetail("provider", properties.getProvider())
                    .build();
        }
    }
}
