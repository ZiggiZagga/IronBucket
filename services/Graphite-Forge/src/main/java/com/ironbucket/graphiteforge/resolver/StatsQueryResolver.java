package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.service.AuditLogService;
import com.ironbucket.graphiteforge.service.PolicyManagementService;

import java.util.HashMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class StatsQueryResolver {

    private final PolicyManagementService policyManagementService;
    private final AuditLogService auditLogService;

    public StatsQueryResolver() {
        this(new PolicyManagementService(), new AuditLogService());
    }

    public StatsQueryResolver(PolicyManagementService policyManagementService, AuditLogService auditLogService) {
        this.policyManagementService = policyManagementService;
        this.auditLogService = auditLogService;
    }

    public Map<String, Object> getPolicyStatistics(String tenantId) {
        int totalPolicies = policyManagementService.getPoliciesByTenant("internal", tenantId).size();
        long evaluations = auditLogService.getAuditLogs("internal", 1000, 0).stream()
            .filter(entry -> entry.bucket().startsWith(tenantId + "-"))
            .count();
        return Map.of("tenantId", tenantId, "totalPolicies", totalPolicies, "evaluationCount", evaluations);
    }

    public Map<String, Object> policyStats(String tenantId) {
        return getPolicyStatistics(tenantId);
    }

    public List<Map<String, Object>> getUserActivitySummary(String tenantId) {
        return auditLogService.getAuditLogs("internal", 1000, 0).stream()
            .filter(entry -> entry.bucket().startsWith(tenantId + "-"))
            .collect(java.util.stream.Collectors.groupingBy(entry -> entry.user()))
            .entrySet().stream()
            .map(group -> {
                Map<String, Object> item = new HashMap<>();
                item.put("identityId", group.getKey());
                item.put("operations", (long) group.getValue().size());
                item.put("lastSeen", group.getValue().stream().map(entry -> entry.timestamp()).max(Instant::compareTo).orElse(Instant.EPOCH).toString());
                return item;
            })
            .toList();
    }

    public List<Map<String, Object>> userActivity(String tenantId) {
        return getUserActivitySummary(tenantId);
    }

    public List<Map<String, Object>> getResourceAccessPatterns(String tenantId) {
        return auditLogService.getAuditLogs("internal", 1000, 0).stream()
            .filter(entry -> entry.bucket().startsWith(tenantId + "-"))
            .collect(java.util.stream.Collectors.groupingBy(entry -> entry.bucket()))
            .entrySet().stream()
            .map(group -> {
                Map<String, Object> item = new HashMap<>();
                item.put("resource", group.getKey());
                item.put("accesses", (long) group.getValue().size());
                return item;
            })
            .toList();
    }

    public List<Map<String, Object>> resourceAccess(String tenantId) {
        return getResourceAccessPatterns(tenantId);
    }
}
