package com.ironbucket.graphiteforge.model;

import java.time.Instant;

public record AuditLogEntry(
    String id,
    Instant timestamp,
    String user,
    String action,
    String bucket,
    String objectKey,
    String result,
    String ipAddress
) {
    public AuditLogEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Audit log ID cannot be null or blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }
}
