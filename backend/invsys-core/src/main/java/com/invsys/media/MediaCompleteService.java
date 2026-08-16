package com.invsys.media;

import com.invsys.core.common.ApiException;
import com.invsys.domain.MediaObject;
import com.invsys.repository.MediaObjectRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.UUID;

/**
 * Registers a pre-signed MinIO/S3 PUT as a tenant-owned {@link MediaObject}
 * after the client finishes uploading bytes to the bucket.
 */
@Service
public class MediaCompleteService {

    private final S3Client s3Client;
    private final MediaStorageProperties properties;
    private final MediaPreSignService preSignService;
    private final MediaObjectRepository mediaObjectRepository;
    private final MediaUploadService mediaUploadService;
    private final ImageContentValidator imageContentValidator;
    private final ObjectStorage objectStorage;

    public MediaCompleteService(S3Client s3Client,
                                MediaStorageProperties properties,
                                MediaPreSignService preSignService,
                                MediaObjectRepository mediaObjectRepository,
                                MediaUploadService mediaUploadService,
                                ImageContentValidator imageContentValidator,
                                ObjectStorage objectStorage) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.preSignService = preSignService;
        this.mediaObjectRepository = mediaObjectRepository;
        this.mediaUploadService = mediaUploadService;
        this.imageContentValidator = imageContentValidator;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public MediaObject complete(String objectKey, String declaredContentType) {
        preSignService.assertOwnedKey(objectKey);
        UUID tenantId = TenantContext.requireTenantId();

        HeadObjectResponse head;
        try {
            head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
        } catch (NoSuchKeyException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_MISSING",
                    "Object not found in storage — complete the pre-signed PUT first");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_MISSING",
                        "Object not found in storage — complete the pre-signed PUT first");
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_READ_FAILED",
                    "Failed to verify uploaded object");
        }

        long size = head.contentLength() != null ? head.contentLength() : 0L;
        if (size <= 0 || size > properties.getMaxBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Uploaded object size is invalid");
        }

        byte[] bytes;
        try (var in = objectStorage.open(objectKey)) {
            bytes = in.readAllBytes();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_UNREADABLE",
                    "Uploaded object could not be read for validation");
        }
        if (bytes.length != size && bytes.length > properties.getMaxBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Uploaded object size is invalid");
        }
        String contentType = imageContentValidator.detectAndValidate(bytes, declaredContentType);

        MediaObject media = new MediaObject();
        media.setTenantId(tenantId);
        media.setStorageKey(objectKey);
        media.setContentType(contentType);
        media.setByteSize(size);
        media.setChecksumSha256(head.eTag() != null ? head.eTag().replace("\"", "") : "presigned");
        media.setOriginalFilename(objectKey.substring(objectKey.lastIndexOf('/') + 1));
        media.setCreatedBy(TenantContext.getUserId().orElse(null));
        return mediaObjectRepository.save(media);
    }

    public String contentPath(UUID mediaId) {
        return mediaUploadService.contentPath(mediaId);
    }
}
