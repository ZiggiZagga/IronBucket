package com.ironbucket.graphiteforge.model;

import java.time.Instant;

public record S3Bucket(
    String name,
    Instant creationDate,
    String ownerTenant
) {
    public S3Bucket {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bucket name cannot be null or blank");
        }
        if (!isValidBucketName(name)) {
            throw new IllegalArgumentException("Invalid bucket name: " + name);
        }
        if (ownerTenant == null || ownerTenant.isBlank()) {
            throw new IllegalArgumentException("Owner tenant cannot be null or blank");
        }
    }

    private static boolean isValidBucketName(String name) {
        if (name.length() < 3 || name.length() > 63) {
            return false;
        }
        return name.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$");
    }
}
