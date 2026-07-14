package com.invsys.media;

import com.invsys.common.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

@Component
public class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client s3Client;
    private final MediaStorageProperties properties;

    public S3ObjectStorage(S3Client s3Client, MediaStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @PostConstruct
    void ensureBucket() {
        String bucket = properties.getBucket();
        if (bucketExists(bucket)) {
            return;
        }
        if (!properties.isCreateBucketIfMissing()) {
            throw new IllegalStateException("S3 bucket missing and create-bucket-if-missing=false: " + bucket);
        }
        log.info("Creating S3 media bucket {}", bucket);
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception createEx) {
            // Race / already exists
            if (createEx.statusCode() != 409 && !bucketExists(bucket)) {
                throw new IllegalStateException("Failed to create S3 media bucket " + bucket, createEx);
            }
        }
    }

    private boolean bucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404 || ex.statusCode() == 403) {
                return false;
            }
            throw ex;
        }
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        validateKey(key);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORE_FAILED",
                    "Failed to store media object in S3-compatible storage");
        }
    }

    @Override
    public InputStream open(String key) {
        validateKey(key);
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
        } catch (NoSuchKeyException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Media object missing in storage");
        } catch (S3Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_READ_FAILED",
                    "Failed to read media object from S3-compatible storage");
        }
    }

    @Override
    public boolean exists(String key) {
        validateKey(key);
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_READ_FAILED",
                    "Failed to probe media object in S3-compatible storage");
        }
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.startsWith("/") || key.startsWith("\\")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY", "Invalid storage key");
        }
    }
}
