package com.ironbucket.graphiteforge.model;

import java.util.List;

public record PolicyRule(
    String id,
    String tenant,
    List<String> roles,
    List<String> buckets,
    List<String> tags,
    List<String> operations,
    int version,
    boolean deleted
) {
    public PolicyRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Policy ID cannot be null or blank");
        }
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("Tenant cannot be null or blank");
        }
    }

    public List<String> allowedBuckets() {
        return buckets;
    }

    public boolean isDefault() {
        return id != null && id.startsWith("default-");
    }
}
