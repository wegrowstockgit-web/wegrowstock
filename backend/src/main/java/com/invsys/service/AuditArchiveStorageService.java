package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.media.MediaStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-isolated cold storage for gzipped audit JSONL archives.
 * Object key layout: {@code archives/{tenantId}/audit/{year}/{month}/{filename}}.
 */
@Service
public class AuditArchiveStorageService {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveStorageService.class);

    private final S3Client s3Client;
    private final MediaStorageProperties properties;

    public AuditArchiveStorageService(S3Client s3Client, MediaStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * Uploads a gzipped JSONL payload. Throws on any non-2xx S3 response so callers
     * never purge Postgres rows for a failed archive write.
     *
     * @return the full object key written
     */
    public String uploadArchive(UUID tenantId, String filename, byte[] gzippedPayload) {
        if (tenantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE", "tenantId is required");
        }
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/")
                || filename.contains("\\")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE", "Invalid archive filename");
        }
        if (gzippedPayload == null || gzippedPayload.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE", "Archive payload is empty");
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String year = String.format("%04d", today.getYear());
        String month = String.format("%02d", today.getMonthValue());
        String key = "archives/" + tenantId + "/audit/" + year + "/" + month + "/" + filename;

        try {
            PutObjectResponse response = s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType("application/gzip")
                            .contentLength((long) gzippedPayload.length)
                            .build(),
                    RequestBody.fromBytes(gzippedPayload));

            int status = response.sdkHttpResponse().statusCode();
            if (status < 200 || status >= 300) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_ARCHIVE_UPLOAD_FAILED",
                        "S3 archive upload returned HTTP " + status);
            }
            log.info("Audit archive uploaded tenant={} key={} bytes={}", tenantId, key, gzippedPayload.length);
            return key;
        } catch (ApiException ex) {
            throw ex;
        } catch (S3Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_ARCHIVE_UPLOAD_FAILED",
                    "Failed to upload audit archive to S3-compatible storage")
                    .withProperty("statusCode", ex.statusCode())
                    .withProperty("awsError", ex.awsErrorDetails() != null
                            ? ex.awsErrorDetails().errorCode() : null);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_ARCHIVE_UPLOAD_FAILED",
                    "Failed to upload audit archive to S3-compatible storage");
        }
    }

    /**
     * Lists {@code .jsonl.gz} object keys under {@code archives/{tenantId}/audit/}
     * for each month in {@code [startMonth, endMonth]} (inclusive).
     */
    public List<String> findArchiveKeys(UUID tenantId, YearMonth startMonth, YearMonth endMonth) {
        if (tenantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE", "tenantId is required");
        }
        if (startMonth == null || endMonth == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE_RANGE",
                    "startMonth and endMonth are required");
        }
        if (startMonth.isAfter(endMonth)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE_RANGE",
                    "startMonth must be on or before endMonth");
        }

        List<String> keys = new ArrayList<>();
        for (YearMonth cursor = startMonth; !cursor.isAfter(endMonth); cursor = cursor.plusMonths(1)) {
            String prefix = monthPrefix(tenantId, cursor);
            keys.addAll(listJsonlGzKeys(prefix));
        }
        return List.copyOf(keys);
    }

    /**
     * Opens a raw S3 object stream for a previously listed archive key.
     * Caller must close the stream.
     */
    public InputStream openArchiveStream(String key) {
        validateArchiveKey(key);
        try {
            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .build());
            return stream;
        } catch (NoSuchKeyException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AUDIT_ARCHIVE_NOT_FOUND",
                    "Audit archive object missing in storage");
        } catch (S3Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_ARCHIVE_READ_FAILED",
                    "Failed to read audit archive from S3-compatible storage")
                    .withProperty("statusCode", ex.statusCode());
        }
    }

    static String monthPrefix(UUID tenantId, YearMonth month) {
        return "archives/" + tenantId + "/audit/"
                + String.format("%04d", month.getYear()) + "/"
                + String.format("%02d", month.getMonthValue()) + "/";
    }

    private List<String> listJsonlGzKeys(String prefix) {
        List<String> keys = new ArrayList<>();
        String continuation = null;
        try {
            do {
                ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                        .bucket(properties.getBucket())
                        .prefix(prefix)
                        .maxKeys(1000);
                if (continuation != null) {
                    builder.continuationToken(continuation);
                }
                ListObjectsV2Response page = s3Client.listObjectsV2(builder.build());
                for (S3Object object : page.contents()) {
                    String key = object.key();
                    if (key != null && key.endsWith(".jsonl.gz") && !key.endsWith("/")) {
                        keys.add(key);
                    }
                }
                continuation = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
            } while (continuation != null);
            return keys;
        } catch (S3Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_ARCHIVE_LIST_FAILED",
                    "Failed to list audit archives in S3-compatible storage")
                    .withProperty("statusCode", ex.statusCode());
        }
    }

    private static void validateArchiveKey(String key) {
        if (key == null || key.isBlank() || key.contains("..") || !key.startsWith("archives/")
                || !key.endsWith(".jsonl.gz")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE_KEY", "Invalid archive storage key");
        }
    }
}
