package com.ironbucket.graphiteforge.model;

import java.util.List;

public record PolicyDiff(
    String policyId,
    int fromVersion,
    int toVersion,
    List<String> addedRoles,
    List<String> removedRoles,
    List<String> addedBuckets,
    List<String> removedBuckets,
    List<String> addedOperations,
    List<String> removedOperations
) {
    public PolicyDiff {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("Policy ID cannot be null or blank");
        }
    }

    public List<String> changedBuckets() {
        return (addedBuckets == null || addedBuckets.isEmpty()) ? removedBuckets : addedBuckets;
    }
}
