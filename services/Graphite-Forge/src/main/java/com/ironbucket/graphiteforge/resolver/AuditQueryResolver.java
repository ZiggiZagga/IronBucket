package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.model.AuditLogEntry;
import com.ironbucket.graphiteforge.service.AuditLogService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuditQueryResolver {

    private final AuditLogService auditLogService;

    public AuditQueryResolver() {
        this(new AuditLogService());
    }

    public AuditQueryResolver(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public List<Map<String, Object>> getAuditTrail(String tenantId) {
        return auditLogService.getAuditLogs("internal", 100, 0).stream()
            .filter(entry -> tenantId == null || tenantId.isBlank() || entry.bucket().startsWith(tenantId + "-"))
            .map(this::toAuditEvent)
            .toList();
    }

    public List<Map<String, Object>> getAuditLogs(String tenantId) {
        return getAuditTrail(tenantId);
    }

    public List<Map<String, Object>> auditLogs(String tenantId) {
        return getAuditLogs(tenantId);
    }

    public List<Map<String, Object>> filterAuditLogs(Map<String, Object> filter) {
        String tenantId = filter == null ? null : String.valueOf(filter.getOrDefault("tenantId", ""));
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return getAuditTrail(tenantId);
    }

    public List<Map<String, Object>> searchAuditLogs(Map<String, Object> filter) {
        return filterAuditLogs(filter);
    }

    public Map<String, Object> getAuditLogById(String id) {
        return auditLogService.getAuditLogs("internal", 1000, 0).stream()
            .filter(entry -> id.equals(entry.id()))
            .findFirst()
            .map(this::toAuditEvent)
            .orElse(Map.of("id", id));
    }

    private Map<String, Object> toAuditEvent(AuditLogEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.id());
        result.put("tenantId", inferTenant(entry.bucket()));
        result.put("actor", entry.user());
        result.put("action", entry.action());
        result.put("resource", entry.bucket());
        result.put("timestamp", entry.timestamp().toString());
        result.put("decision", "SUCCESS".equals(entry.result()) ? "ALLOW" : "DENY");
        return result;
    }

    private String inferTenant(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return "unknown";
        }
        int separator = bucket.indexOf('-');
        if (separator <= 0) {
            return bucket;
        }
        return bucket.substring(0, separator);
    }
}
