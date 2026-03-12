package com.ironbucket.graphiteforge.model;

import java.time.Instant;
import java.util.Map;

public record S3Object(
    String key,
    String bucketName,
    long size,
    Instant lastModified,
    String contentType,
    Map<String, String> metadata,
    String selectedProvider,
    String routingReason
) {
    public S3Object(
        String key,
        String bucketName,
        long size,
        Instant lastModified,
        String contentType,
        Map<String, String> metadata
    ) {
        this(key, bucketName, size, lastModified, contentType, metadata, null, null);
    }

    public String bucket() {
        return bucketName;
    }
}
