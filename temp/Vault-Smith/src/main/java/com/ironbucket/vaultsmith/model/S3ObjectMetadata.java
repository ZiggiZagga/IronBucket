package com.ironbucket.vaultsmith.model;

import java.time.Instant;

/**
 * S3 object metadata representation.
 */
public class S3ObjectMetadata {
    private String key;
    private long contentLength;
    private String contentType;
    private String etag;
    private Instant lastModified;
    private String storageClass;

    public S3ObjectMetadata(String key, long contentLength, String contentType, String etag, Instant lastModified, String storageClass) {
        this.key = key;
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.etag = etag;
        this.lastModified = lastModified;
        this.storageClass = storageClass;
    }

    public String getKey() { return key; }
    public long getContentLength() { return contentLength; }
    public String getContentType() { return contentType; }
    public String getEtag() { return etag; }
    public Instant getLastModified() { return lastModified; }
    public String getStorageClass() { return storageClass; }

    @Override
    public String toString() {
        return "S3ObjectMetadata{" +
                "key='" + key + '\'' +
                ", contentLength=" + contentLength +
                ", contentType='" + contentType + '\'' +
                ", etag='" + etag + '\'' +
                ", lastModified=" + lastModified +
                ", storageClass='" + storageClass + '\'' +
                '}';
    }
}
