package com.ironbucket.graphiteforge.dgs;

import com.ironbucket.graphiteforge.model.AuditLogEntry;
import com.ironbucket.graphiteforge.service.AuditLogService;
import com.ironbucket.graphiteforge.service.PolicyManagementService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.DgsSubscription;
import com.netflix.graphql.dgs.InputArgument;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * DGS data fetcher for Audit queries, Stats queries and Audit subscriptions
 * defined in schema.graphqls.
 */
@DgsComponent
public class AuditDataFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(AuditDataFetcher.class);

    private final AuditLogService auditLogService;
    private final PolicyManagementService policyManagementService;

    @Autowired
    public AuditDataFetcher(AuditLogService auditLogService, PolicyManagementService policyManagementService) {
        this.auditLogService = auditLogService;
        this.policyManagementService = policyManagementService;
    }

    // ───────────────────── Audit Queries ─────────────────────

    @DgsQuery(field = "getAuditTrail")
    public Mono<List<Map<String, Object>>> getAuditTrail(@InputArgument String tenantId) {
        return fromBlocking(() -> {
            LOG.info("graphql getAuditTrail tenantId={}", tenantId);
            return fetchAuditForTenant(tenantId, 100);
        });
    }

    @DgsQuery(field = "getAuditLogs")
    public Mono<List<Map<String, Object>>> getAuditLogs(@InputArgument String tenantId) {
        return fromBlocking(() -> {
            LOG.info("graphql getAuditLogs tenantId={}", tenantId);
            return fetchAuditForTenant(tenantId, 100);
        });
    }

    @DgsQuery(field = "auditLogs")
    public Mono<List<Map<String, Object>>> auditLogs(@InputArgument String tenantId) {
        return fromBlocking(() -> {
            LOG.info("graphql auditLogs tenantId={}", tenantId);
            return fetchAuditForTenant(tenantId, 100);
        });
    }

    @DgsQuery(field = "filterAuditLogs")
    public Mono<List<Map<String, Object>>> filterAuditLogs(@InputArgument Map<String, Object> filter) {
        return fromBlocking(() -> {
            if (filter == null) return List.of();
            String tenantId = str(filter, "tenantId");
            String actor    = str(filter, "actor");
            String action   = str(filter, "action");
            String resource = str(filter, "resource");
            LOG.info("graphql filterAuditLogs tenantId={} actor={} action={} resource={}", tenantId, actor, action, resource);
            return auditLogService.getAuditLogs("internal", 200, 0).stream()
                .filter(e -> tenantId.isBlank() || e.bucket().startsWith(tenantId + "-") || tenantId.equals(inferTenant(e.bucket())))
                .filter(e -> actor.isBlank() || actor.equalsIgnoreCase(e.user()))
                .filter(e -> action.isBlank() || action.equalsIgnoreCase(e.action()))
                .filter(e -> resource.isBlank() || e.bucket().contains(resource))
                .map(this::toAuditEvent)
                .collect(Collectors.toList());
        });
    }

    @DgsQuery(field = "getAuditLogById")
    public Mono<Map<String, Object>> getAuditLogById(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql getAuditLogById id={}", id);
            return auditLogService.getAuditLogs("internal", 1000, 0).stream()
                .filter(e -> id.equals(e.id()))
                .findFirst()
                .map(this::toAuditEvent)
                .orElse(Map.of("id", id, "tenantId", "unknown", "actor", "unknown",
                               "action", "unknown", "resource", "unknown",
                               "timestamp", Instant.now().toString(), "decision", "UNKNOWN"));
        });
    }

    // ───────────────────── Stats Queries ─────────────────────

    @DgsQuery(field = "getPolicyStatistics")
    public Mono<Map<String, Object>> getPolicyStatistics(@InputArgument String tenantId) {
        return fromBlocking(() -> {
            LOG.info("graphql getPolicyStatistics tenantId={}", tenantId);
            int total = policyManagementService.getPoliciesByTenant("internal", tenantId).size();
            long evaluationCount = auditLogService.getAuditLogs("internal", 1000, 0).stream()
                .filter(e -> tenantId.equals(inferTenant(e.bucket())))
                .count();
            return Map.of("tenantId", tenantId, "totalPolicies", total, "evaluationCount", evaluationCount);
        });
    }

    @DgsQuery(field = "policyStats")
    public Mono<Map<String, Object>> policyStats(@InputArgument String tenantId) {
        return getPolicyStatistics(tenantId);
    }

    @DgsQuery(field = "getUserActivitySummary")
    public Mono<List<Map<String, Object>>> getUserActivitySummary(@InputArgument String tenantId) {
        return fromBlocking(() -> {
            LOG.info("graphql getUserActivitySummary tenantId={}", tenantId);
            return auditLogService.getAuditLogs("internal", 1000, 0).stream()
                .filter(e -> tenantId.equals(inferTenant(e.bucket())))
                .collect(Collectors.groupingBy(AuditLogEntry::user))
                .entrySet().stream()
                .map(g -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("identityId", g.getKey());
                    item.put("operations", g.getValue().size());
                    item.put("lastSeen", g.getValue().stream()
                        .map(AuditLogEntry::timestamp)
                        .max(Instant::compareTo)
                        .orElse(Instant.EPOCH).toString());
                    return item;
                })
                .collect(Collectors.toList());
        });
    }

    @DgsQuery(field = "userActivity")
    public Mono<List<Map<String, Object>>> userActivity(@InputArgument String tenantId) {
        return getUserActivitySummary(tenantId);
    }

    @DgsQuery(field = "getResourceAccessPatterns")
    public Mono<List<Map<String, Object>>> getResourceAccessPatterns(@InputArgument String tenantId) {
        return fromBlocking(() -> {
            LOG.info("graphql getResourceAccessPatterns tenantId={}", tenantId);
            return auditLogService.getAuditLogs("internal", 1000, 0).stream()
                .filter(e -> tenantId.equals(inferTenant(e.bucket())))
                .collect(Collectors.groupingBy(AuditLogEntry::bucket))
                .entrySet().stream()
                .map(g -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("resource", g.getKey());
                    item.put("accesses", g.getValue().size());
                    return item;
                })
                .collect(Collectors.toList());
        });
    }

    @DgsQuery(field = "resourceAccess")
    public Mono<List<Map<String, Object>>> resourceAccess(@InputArgument String tenantId) {
        return getResourceAccessPatterns(tenantId);
    }

    // ───────────────────── Subscriptions ─────────────────────

    @DgsSubscription(field = "auditLogSubscription")
    public Publisher<Map<String, Object>> auditLogSubscription(@InputArgument String tenantId) {
        LOG.info("graphql auditLogSubscription tenantId={}", tenantId);
        return Flux.interval(Duration.ofSeconds(5))
            .map(tick -> {
                List<Map<String, Object>> latest = fetchAuditForTenant(tenantId, 1);
                if (!latest.isEmpty()) return latest.get(0);
                Map<String, Object> heartbeat = new LinkedHashMap<>();
                heartbeat.put("id", "heartbeat-" + tick);
                heartbeat.put("tenantId", tenantId);
                heartbeat.put("actor", "graphite-forge");
                heartbeat.put("action", "HEARTBEAT");
                heartbeat.put("resource", tenantId + "-audit-stream");
                heartbeat.put("timestamp", Instant.now().toString());
                heartbeat.put("decision", "ALLOW");
                return heartbeat;
            })
            .take(Duration.ofMinutes(5));
    }

    @DgsSubscription(field = "onAuditLog")
    public Publisher<Map<String, Object>> onAuditLog(@InputArgument String tenantId) {
        return auditLogSubscription(tenantId);
    }

    // ───────────────────── Helpers ─────────────────────

    private List<Map<String, Object>> fetchAuditForTenant(String tenantId, int limit) {
        return auditLogService.getAuditLogs("internal", limit, 0).stream()
            .filter(e -> tenantId == null || tenantId.isBlank()
                || e.bucket().startsWith(tenantId + "-")
                || tenantId.equals(inferTenant(e.bucket())))
            .map(this::toAuditEvent)
            .collect(Collectors.toList());
    }

    private Map<String, Object> toAuditEvent(AuditLogEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id",        entry.id());
        result.put("tenantId",  inferTenant(entry.bucket()));
        result.put("actor",     entry.user());
        result.put("action",    entry.action());
        result.put("resource",  entry.bucket());
        result.put("timestamp", entry.timestamp().toString());
        result.put("decision",  "SUCCESS".equals(entry.result()) ? "ALLOW" : "DENY");
        return result;
    }

    private String inferTenant(String bucket) {
        if (bucket == null || bucket.isBlank()) return "unknown";
        int sep = bucket.indexOf('-');
        return sep > 0 ? bucket.substring(0, sep) : bucket;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private <T> Mono<T> fromBlocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
