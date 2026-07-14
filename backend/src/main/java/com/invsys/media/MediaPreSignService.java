package com.invsys.media;

import com.invsys.common.ApiException;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaPreSignService {

    public enum PresignType {
        PRODUCT, TRANSACTION, USER_AVATAR
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final S3Presigner s3Presigner;
    private final MediaStorageProperties properties;
    private final ImageContentValidator contentValidator;

    public MediaPreSignService(S3Presigner s3Presigner,
                               MediaStorageProperties properties,
                               ImageContentValidator contentValidator) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
        this.contentValidator = contentValidator;
    }

    public PresignUploadResult presignUpload(String typeRaw, String filename, String contentType) {
        PresignType type = parseType(typeRaw);
        String normalizedType = normalizeContentType(contentType);
        if (!ALLOWED_TYPES.contains(normalizedType) && !contentValidator.isAllowedContentType(normalizedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE",
                    "Only JPEG, PNG, WebP, and GIF images are allowed");
        }

        UUID tenantId = TenantContext.requireTenantId();
        String ext = contentValidator.extensionFor(normalizedType);
        String safeName = sanitizeFilename(filename);
        String objectKey = tenantId + "/" + type.name().toLowerCase(Locale.ROOT) + "/"
                + UUID.randomUUID() + (safeName != null ? "-" + safeName : "") + "." + ext;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(normalizedType)
                .build();

        Duration ttl = Duration.ofMinutes(10);
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(objectRequest)
                .build());

        return new PresignUploadResult(
                objectKey,
                presigned.url().toString(),
                normalizedType,
                type.name(),
                ttl.getSeconds());
    }

    public void assertOwnedKey(String objectKey) {
        UUID tenantId = TenantContext.requireTenantId();
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")
                || !objectKey.startsWith(tenantId + "/")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_OBJECT_KEY",
                    "Object key is not owned by the current tenant");
        }
    }

    private static PresignType parseType(String typeRaw) {
        if (typeRaw == null || typeRaw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TYPE",
                    "type must be PRODUCT, TRANSACTION, or USER_AVATAR");
        }
        try {
            return PresignType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TYPE",
                    "type must be PRODUCT, TRANSACTION, or USER_AVATAR");
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "contentType is required");
        }
        return contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        return base.isBlank() ? null : base.toLowerCase(Locale.ROOT);
    }

    public record PresignUploadResult(
            String objectKey,
            String uploadUrl,
            String contentType,
            String type,
            long expiresInSeconds
    ) {
    }
}
