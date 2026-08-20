package com.invsys.media;

import com.invsys.core.integration.OutboxEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@code TENANT_S3_PURGE} outbox events and deletes tenant-prefixed media objects.
 */
@Component
public class TenantMediaPurgeHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "TENANT_S3_PURGE";

    private static final Logger log = LoggerFactory.getLogger(TenantMediaPurgeHandler.class);

    private final S3Client s3Client;
    private final MediaStorageProperties properties;

    public TenantMediaPurgeHandler(S3Client s3Client, MediaStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        if (tenantId == null) {
            return;
        }
        String prefix = payload != null && payload.get("prefix") != null
                ? String.valueOf(payload.get("prefix"))
                : tenantId + "/";
        if (!prefix.startsWith(tenantId.toString())) {
            log.warn("Refusing S3 purge prefix {} for tenant {}", prefix, tenantId);
            return;
        }
        int deleted = 0;
        String token = null;
        do {
            ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(properties.getBucket())
                    .prefix(prefix)
                    .continuationToken(token)
                    .build());
            for (S3Object object : page.contents()) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(object.key())
                        .build());
                deleted++;
            }
            token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (token != null);
        log.info("Purged {} S3 objects for tenant {} prefix={}", deleted, tenantId, prefix);
    }
}
