package com.ironbucket.vaultsmith.model;

/**
 * Result of an S3 upload operation.
 */
public class S3UploadResult {
    private String bucketName;
    private String objectKey;
    private String etag;
    private long contentLength;

    public S3UploadResult(String bucketName, String objectKey, String etag, long contentLength) {
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.etag = etag;
        this.contentLength = contentLength;
    }

    public String getBucketName() { return bucketName; }
    public String getObjectKey() { return objectKey; }
    public String getEtag() { return etag; }
    public long getContentLength() { return contentLength; }

    @Override
    public String toString() {
        return "S3UploadResult{" +
                "bucketName='" + bucketName + '\'' +
                ", objectKey='" + objectKey + '\'' +
                ", etag='" + etag + '\'' +
                ", contentLength=" + contentLength +
                '}';
    }
}
