package com.ironbucket.graphiteforge.model;

import java.time.Instant;
import java.util.Map;

public record S3Object(
    String key,
    String bucketName,
    long size,
    Instant lastModified,
    String contentType,
    Map<String, String> metadata
) {
    public String bucket() {
        return bucketName;
    }
}
