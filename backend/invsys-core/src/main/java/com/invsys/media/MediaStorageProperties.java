package com.invsys.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "invsys.media")
public class MediaStorageProperties {

    /**
     * S3-compatible provider preset: AWS, GCP, AZURE, DIGITALOCEAN, MINIO, CUSTOM.
     * Presets fill default endpoints; any provider can override {@link #endpoint}.
     */
    private String provider = "MINIO";

    private String bucket = "invsys-media";

    private String region = "us-east-1";

    /** Optional custom endpoint (required for MinIO / DO / Azure gateway / CUSTOM). */
    private String endpoint = "";

    /**
     * Browser-reachable endpoint for pre-signed PUT URLs. When empty, {@link #endpoint} is used.
     * In Docker, set this to http://localhost:9000 while {@link #endpoint} stays http://minio:9000.
     */
    private String publicEndpoint = "";

    private String accessKey = "";

    private String secretKey = "";

    /**
     * Path-style addressing (required for MinIO; usually false for AWS/GCP/DO virtual-host).
     */
    private boolean pathStyleAccess = true;

    /** Create the bucket on startup when missing (dev / MinIO). */
    private boolean createBucketIfMissing = true;

    private long maxBytes = 15 * 1024 * 1024L;

    private long avatarMaxBytes = 2 * 1024 * 1024L;

    private long evidenceMaxBytes = 10 * 1024 * 1024L;

    /**
     * When true, custom {@code endpoint} / {@code public-endpoint} may resolve to loopback
     * or RFC 1918 (MinIO / local tests). Production object storage should leave this false.
     */
    private boolean allowPrivateEndpoints = false;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public boolean isCreateBucketIfMissing() {
        return createBucketIfMissing;
    }

    public void setCreateBucketIfMissing(boolean createBucketIfMissing) {
        this.createBucketIfMissing = createBucketIfMissing;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public long getAvatarMaxBytes() {
        return avatarMaxBytes;
    }

    public void setAvatarMaxBytes(long avatarMaxBytes) {
        this.avatarMaxBytes = avatarMaxBytes;
    }

    public long getEvidenceMaxBytes() {
        return evidenceMaxBytes;
    }

    public void setEvidenceMaxBytes(long evidenceMaxBytes) {
        this.evidenceMaxBytes = evidenceMaxBytes;
    }

    public boolean isAllowPrivateEndpoints() {
        return allowPrivateEndpoints;
    }

    public void setAllowPrivateEndpoints(boolean allowPrivateEndpoints) {
        this.allowPrivateEndpoints = allowPrivateEndpoints;
    }
}
