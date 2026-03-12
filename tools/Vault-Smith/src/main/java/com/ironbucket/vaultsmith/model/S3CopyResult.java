package com.ironbucket.vaultsmith.model;

/**
 * Result of an S3 copy operation.
 */
public class S3CopyResult {
    private String sourceBucket;
    private String sourceKey;
    private String destBucket;
    private String destKey;
    private String etag;
    private long contentLength;

    public S3CopyResult(String sourceBucket, String sourceKey, String destBucket, String destKey, String etag, long contentLength) {
        this.sourceBucket = sourceBucket;
        this.sourceKey = sourceKey;
        this.destBucket = destBucket;
        this.destKey = destKey;
        this.etag = etag;
        this.contentLength = contentLength;
    }

    public String getSourceBucket() { return sourceBucket; }
    public String getSourceKey() { return sourceKey; }
    public String getDestBucket() { return destBucket; }
    public String getDestKey() { return destKey; }
    public String getEtag() { return etag; }
    public long getContentLength() { return contentLength; }

    @Override
    public String toString() {
        return "S3CopyResult{" +
                "sourceBucket='" + sourceBucket + '\'' +
                ", sourceKey='" + sourceKey + '\'' +
                ", destBucket='" + destBucket + '\'' +
                ", destKey='" + destKey + '\'' +
                ", etag='" + etag + '\'' +
                ", contentLength=" + contentLength +
                '}';
    }
}
