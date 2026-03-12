package com.ironbucket.graphiteforge.service;

import com.ironbucket.graphiteforge.model.AuditLogEntry;
import com.ironbucket.graphiteforge.model.AuditStatistics;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditLogService {

    private final List<AuditLogEntry> entries = new CopyOnWriteArrayList<>();

    public List<AuditLogEntry> getAuditLogs(String jwtToken, int limit, int offset) {
        if (offset >= entries.size()) {
            return List.of();
        }
        int toIndex = Math.min(entries.size(), offset + Math.max(0, limit));
        return entries.subList(offset, toIndex);
    }

    public List<AuditLogEntry> getAuditLogsByBucket(String jwtToken, String bucket) {
        return entries.stream().filter(entry -> bucket.equals(entry.bucket())).toList();
    }

    public AuditStatistics getAuditStatistics(String jwtToken, Instant startDate, Instant endDate) {
        List<AuditLogEntry> inRange = entries.stream()
            .filter(entry -> !entry.timestamp().isBefore(startDate) && !entry.timestamp().isAfter(endDate))
            .toList();
        long success = inRange.stream().filter(entry -> "SUCCESS".equals(entry.result())).count();
        long failures = inRange.size() - success;
        long users = inRange.stream().map(AuditLogEntry::user).distinct().count();
        return new AuditStatistics(inRange.size(), success, failures, users);
    }

    public void append(AuditLogEntry entry) {
        entries.add(entry);
    }
}
