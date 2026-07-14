package com.invsys.media;

import java.io.InputStream;

/**
 * Provider-agnostic object storage. Implementations target S3-compatible APIs
 * (AWS S3, GCP Cloud Storage HMAC, DigitalOcean Spaces, Azure S3 gateway, MinIO).
 */
public interface ObjectStorage {

    void put(String key, byte[] bytes, String contentType);

    InputStream open(String key);

    boolean exists(String key);
}
