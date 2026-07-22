package com.invsys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invsys.integration")
public class IntegrationProperties {

    /**
     * Envelope key provider: {@code LOCAL} (dev/test), {@code AWS_KMS}, or {@code HASHICORP_VAULT}.
     */
    private String vaultProvider = "LOCAL";
    private String masterKey = "";
    private String awsKmsKeyId = "";
    private String awsRegion = "us-east-1";
    private String vaultAddress = "";
    private String vaultToken = "";
    private String vaultTransitKey = "invsys-credentials";
    private Outbox outbox = new Outbox();

    public String getVaultProvider() {
        return vaultProvider;
    }

    public void setVaultProvider(String vaultProvider) {
        this.vaultProvider = vaultProvider;
    }

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public String getAwsKmsKeyId() {
        return awsKmsKeyId;
    }

    public void setAwsKmsKeyId(String awsKmsKeyId) {
        this.awsKmsKeyId = awsKmsKeyId;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public String getVaultAddress() {
        return vaultAddress;
    }

    public void setVaultAddress(String vaultAddress) {
        this.vaultAddress = vaultAddress;
    }

    public String getVaultToken() {
        return vaultToken;
    }

    public void setVaultToken(String vaultToken) {
        this.vaultToken = vaultToken;
    }

    public String getVaultTransitKey() {
        return vaultTransitKey;
    }

    public void setVaultTransitKey(String vaultTransitKey) {
        this.vaultTransitKey = vaultTransitKey;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    public static class Outbox {
        private int batchSize = 20;
        private int maxRetries = 5;
        private long pollIntervalMs = 5000;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }
    }
}
