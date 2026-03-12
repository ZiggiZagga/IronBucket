package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.model.AuditLogEntry;
import com.ironbucket.graphiteforge.service.AuditLogService;

import java.time.Instant;
import java.util.Map;

public class AuditSubscriptionResolver {

    private final AuditLogService auditLogService;

    public AuditSubscriptionResolver() {
        this(new AuditLogService());
    }

    public AuditSubscriptionResolver(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public Map<String, Object> auditLogSubscription(String tenantId) {
        AuditLogEntry synthetic = new AuditLogEntry(
            "sub-" + tenantId,
            Instant.now(),
            "graphite-subscription",
            "SUBSCRIBE",
            tenantId + "-audit-stream",
            "subscription",
            "SUCCESS",
            "127.0.0.1"
        );
        auditLogService.append(synthetic);
        return Map.of(
            "id", synthetic.id(),
            "tenantId", tenantId,
            "actor", synthetic.user(),
            "action", synthetic.action(),
            "resource", synthetic.bucket(),
            "timestamp", synthetic.timestamp().toString(),
            "decision", "ALLOW"
        );
    }

    public Map<String, Object> onAuditLog(String tenantId) {
        return auditLogSubscription(tenantId);
    }
}
