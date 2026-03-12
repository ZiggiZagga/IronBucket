package com.ironbucket.graphiteforge.model;

public record UserActivitySummary(
    String user,
    long totalOperations,
    long successfulOperations,
    long failedOperations
) {
}
