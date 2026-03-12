package com.ironbucket.graphiteforge.model;

public record AuditStatistics(
    long totalOperations,
    long successfulOperations,
    long failedOperations,
    long uniqueUsers
) {
    public AuditStatistics {
        if (totalOperations < 0 || successfulOperations < 0 || failedOperations < 0 || uniqueUsers < 0) {
            throw new IllegalArgumentException("Statistics values cannot be negative");
        }
    }
}
