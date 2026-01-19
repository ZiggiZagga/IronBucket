package com.ironbucket.vaultsmith.config;

/**
 * Configuration for S3 backend.
 */
public class S3BackendConfig {
    private String provider; // "aws-s3", "s3", "openstack-swift", etc.
    private String region;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccess;
    private long socketTimeoutMs;

    public S3BackendConfig(String provider, String region, String endpoint, String accessKey, String secretKey) {
        this.provider = provider;
        this.region = region;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.pathStyleAccess = true;
        this.socketTimeoutMs = 30000;
    }

    public String getProvider() { return provider; }
    public String getRegion() { return region; }
    public String getEndpoint() { return endpoint; }
    public String getAccessKey() { return accessKey; }
    public String getSecretKey() { return secretKey; }
    public boolean isPathStyleAccess() { return pathStyleAccess; }
    public long getSocketTimeoutMs() { return socketTimeoutMs; }

    public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }
    public void setSocketTimeoutMs(long socketTimeoutMs) { this.socketTimeoutMs = socketTimeoutMs; }

    @Override
    public String toString() {
        return "S3BackendConfig{" +
                "provider='" + provider + '\'' +
                ", region='" + region + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", accessKey='" + accessKey + '\'' +
                ", pathStyleAccess=" + pathStyleAccess +
                ", socketTimeoutMs=" + socketTimeoutMs +
                '}';
    }
}
