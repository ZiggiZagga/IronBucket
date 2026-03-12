package com.ironbucket.graphiteforge.model;

import java.time.Instant;
import java.util.List;

public record PolicySyncResult(
    boolean success,
    Instant syncTime,
    int policiesAdded,
    int policiesUpdated,
    int policiesDeleted,
    List<String> errors
) {
    public PolicySyncResult {
        if (syncTime == null) {
            throw new IllegalArgumentException("Sync time cannot be null");
        }
        if (errors == null) {
            errors = List.of();
        }
    }

    public int addedPolicies() {
        return policiesAdded;
    }

    public int updatedPolicies() {
        return policiesUpdated;
    }

    public int deletedPolicies() {
        return policiesDeleted;
    }
}
