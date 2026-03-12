package com.ironbucket.graphiteforge.model;

import java.util.List;

public record PolicyInput(
    String tenant,
    List<String> roles,
    List<String> buckets,
    List<String> tags,
    List<String> operations
) {
    public PolicyInput {
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("Tenant cannot be null or blank");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Roles cannot be null or empty");
        }
    }
}
