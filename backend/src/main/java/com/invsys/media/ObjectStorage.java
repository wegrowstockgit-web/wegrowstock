package com.invsys.media;

import java.io.InputStream;

/**
 * Provider-agnostic object storage.
 * <p><strong>S3-only invariant:</strong> the sole production implementation is
 * {@link S3ObjectStorage}. Local filesystem / disk backends are forbidden — all
 * media bytes must live in an S3-compatible bucket (AWS, MinIO, GCS HMAC, DO Spaces).
 */
public interface ObjectStorage {

    void put(String key, byte[] bytes, String contentType);

    InputStream open(String key);

    boolean exists(String key);

    /** Best-effort delete; missing keys are ignored. */
    void delete(String key);
}
