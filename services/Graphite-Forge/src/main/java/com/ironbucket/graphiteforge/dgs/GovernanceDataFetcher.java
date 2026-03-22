package com.ironbucket.graphiteforge.dgs;

import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GovernanceDataFetcher {

    private final ConcurrentMap<String, Map<String, Object>> tenants = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> identities = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> policies = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> auditEvents = new ConcurrentHashMap<>();

    @DgsQuery(field = "getPolicy")
    public Map<String, Object> getPolicy(@InputArgument String id) {
        return getPolicyById(id);
    }

    @DgsQuery(field = "getPolicyById")
    public Map<String, Object> getPolicyById(@InputArgument String id) {
        return policies.get(id);
    }

    @DgsQuery(field = "listPolicies")
    public List<Map<String, Object>> listPolicies() {
        return policies.values().stream().map(Map::copyOf).toList();
    }

    @DgsQuery(field = "searchPolicies")
    public List<Map<String, Object>> searchPolicies(@InputArgument String query) {
        if (query == null || query.isBlank()) {
            return listPolicies();
        }
        String term = query.toLowerCase();
        return policies.values().stream()
            .filter(policy -> String.valueOf(policy.getOrDefault("principal", "")).toLowerCase().contains(term)
                || String.valueOf(policy.getOrDefault("resource", "")).toLowerCase().contains(term)
                || String.valueOf(policy.getOrDefault("action", "")).toLowerCase().contains(term))
            .map(Map::copyOf)
            .toList();
    }

    @DgsQuery(field = "evaluatePolicy")
    public Map<String, Object> evaluatePolicy(@InputArgument Map<String, Object> input) {
        String principal = asString(input, "principal");
        String resource = asString(input, "resource");
        String action = asString(input, "action");
        String tenantId = asString(input, "tenantId");

        boolean allowed = policies.values().stream().anyMatch(policy ->
            Objects.equals(tenantId, asString(policy, "tenantId"))
                && Objects.equals(principal, asString(policy, "principal"))
                && Objects.equals(resource, asString(policy, "resource"))
                && Objects.equals(action, asString(policy, "action"))
                && "ALLOW".equalsIgnoreCase(asString(policy, "effect"))
                && Boolean.TRUE.equals(policy.get("enabled"))
        );

        return Map.of(
            "allow", allowed,
            "reason", allowed ? "Allowed by in-memory governance policy" : "No matching allow policy found"
        );
    }

    @DgsMutation(field = "createPolicy")
    public Map<String, Object> createPolicy(@InputArgument Map<String, Object> input) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("id", id);
        policy.put("tenantId", asString(input, "tenantId"));
        policy.put("principal", asString(input, "principal"));
        policy.put("resource", asString(input, "resource"));
        policy.put("action", asString(input, "action"));
        policy.put("effect", defaulted(asString(input, "effect"), "ALLOW"));
        policy.put("version", 1);
        policy.put("enabled", true);
        policies.put(id, policy);
        appendAuditEvent(asString(policy, "tenantId"), asString(policy, "principal"), "createPolicy", asString(policy, "resource"), "ALLOW");
        return Map.copyOf(policy);
    }

    @DgsMutation(field = "addPolicy")
    public Map<String, Object> addPolicy(@InputArgument Map<String, Object> input) {
        return createPolicy(input);
    }

    @DgsMutation(field = "updatePolicy")
    public Map<String, Object> updatePolicy(@InputArgument String id, @InputArgument Map<String, Object> input) {
        Map<String, Object> existing = policies.get(id);
        if (existing == null) {
            return null;
        }
        Map<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("tenantId", defaulted(asString(input, "tenantId"), asString(existing, "tenantId")));
        updated.put("principal", defaulted(asString(input, "principal"), asString(existing, "principal")));
        updated.put("resource", defaulted(asString(input, "resource"), asString(existing, "resource")));
        updated.put("action", defaulted(asString(input, "action"), asString(existing, "action")));
        updated.put("effect", defaulted(asString(input, "effect"), asString(existing, "effect")));
        updated.put("version", ((Number) existing.getOrDefault("version", 1)).intValue() + 1);
        policies.put(id, updated);
        appendAuditEvent(asString(updated, "tenantId"), asString(updated, "principal"), "updatePolicy", asString(updated, "resource"), "ALLOW");
        return Map.copyOf(updated);
    }

    @DgsMutation(field = "deletePolicy")
    public boolean deletePolicy(@InputArgument String id) {
        Map<String, Object> removed = policies.remove(id);
        if (removed != null) {
            appendAuditEvent(asString(removed, "tenantId"), asString(removed, "principal"), "deletePolicy", asString(removed, "resource"), "ALLOW");
            return true;
        }
        return false;
    }

    @DgsMutation(field = "validatePolicy")
    public Map<String, Object> validatePolicy(@InputArgument Map<String, Object> input) {
        List<String> errors = new ArrayList<>();
        if (asString(input, "tenantId") == null || asString(input, "tenantId").isBlank()) {
            errors.add("tenantId is required");
        }
        if (asString(input, "principal") == null || asString(input, "principal").isBlank()) {
            errors.add("principal is required");
        }
        if (asString(input, "resource") == null || asString(input, "resource").isBlank()) {
            errors.add("resource is required");
        }
        if (asString(input, "action") == null || asString(input, "action").isBlank()) {
            errors.add("action is required");
        }
        return Map.of(
            "valid", errors.isEmpty(),
            "errors", errors
        );
    }

    @DgsQuery(field = "getIdentity")
    public Map<String, Object> getIdentity(@InputArgument String sub) {
        if (sub == null || sub.isBlank()) {
            return null;
        }
        return identities.values().stream()
            .filter(identity -> sub.equals(asString(identity, "sub")))
            .findFirst()
            .map(Map::copyOf)
            .orElse(null);
    }

    @DgsQuery(field = "identity")
    public Map<String, Object> identity(@InputArgument String id) {
        return getIdentityById(id);
    }

    @DgsQuery(field = "getIdentityById")
    public Map<String, Object> getIdentityById(@InputArgument String id) {
        Map<String, Object> identity = identities.get(id);
        return identity == null ? null : Map.copyOf(identity);
    }

    @DgsQuery(field = "listIdentities")
    public List<Map<String, Object>> listIdentities(@InputArgument String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return identities.values().stream()
            .filter(identity -> tenantId.equals(asString(identity, "tenantId")))
            .map(Map::copyOf)
            .toList();
    }

    @DgsQuery(field = "getUserPermissions")
    public List<String> getUserPermissions(@InputArgument String identityId) {
        Map<String, Object> identity = identities.get(identityId);
        if (identity == null) {
            return List.of();
        }
        Object permissions = identity.get("permissions");
        if (permissions instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @DgsMutation(field = "createIdentity")
    public Map<String, Object> createIdentity(@InputArgument Map<String, Object> input) {
        String id = UUID.randomUUID().toString();
        String tenantId = defaulted(asString(input, "tenantId"), "default");
        String username = defaulted(asString(input, "username"), "unknown");
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("id", id);
        identity.put("sub", defaulted(asString(input, "sub"), id));
        identity.put("tenantId", tenantId);
        identity.put("username", username);
        identity.put("email", asString(input, "email"));
        identity.put("permissions", List.of("s3:read", "s3:write"));
        identities.put(id, identity);
        appendAuditEvent(tenantId, username, "createIdentity", id, "ALLOW");
        return Map.copyOf(identity);
    }

    @DgsMutation(field = "addUser")
    public Map<String, Object> addUser(@InputArgument Map<String, Object> input) {
        return createIdentity(input);
    }

    @DgsMutation(field = "updateIdentity")
    public Map<String, Object> updateIdentity(@InputArgument String id, @InputArgument Map<String, Object> input) {
        Map<String, Object> existing = identities.get(id);
        if (existing == null) {
            return null;
        }
        Map<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("tenantId", defaulted(asString(input, "tenantId"), asString(existing, "tenantId")));
        updated.put("username", defaulted(asString(input, "username"), asString(existing, "username")));
        updated.put("email", defaulted(asString(input, "email"), asString(existing, "email")));
        identities.put(id, updated);
        appendAuditEvent(asString(updated, "tenantId"), asString(updated, "username"), "updateIdentity", id, "ALLOW");
        return Map.copyOf(updated);
    }

    @DgsMutation(field = "updateUser")
    public Map<String, Object> updateUser(@InputArgument String id, @InputArgument Map<String, Object> input) {
        return updateIdentity(id, input);
    }

    @DgsMutation(field = "deleteIdentity")
    public boolean deleteIdentity(@InputArgument String id) {
        Map<String, Object> removed = identities.remove(id);
        if (removed != null) {
            appendAuditEvent(asString(removed, "tenantId"), asString(removed, "username"), "deleteIdentity", id, "ALLOW");
            return true;
        }
        return false;
    }

    @DgsMutation(field = "removeUser")
    public boolean removeUser(@InputArgument String id) {
        return deleteIdentity(id);
    }

    @DgsQuery(field = "getTenant")
    public Map<String, Object> getTenant(@InputArgument String id) {
        return getTenantById(id);
    }

    @DgsQuery(field = "tenant")
    public Map<String, Object> tenant(@InputArgument String id) {
        return getTenantById(id);
    }

    @DgsQuery(field = "getTenantById")
    public Map<String, Object> getTenantById(@InputArgument String id) {
        Map<String, Object> tenant = tenants.get(id);
        return tenant == null ? null : Map.copyOf(tenant);
    }

    @DgsQuery(field = "listTenants")
    public List<Map<String, Object>> listTenants() {
        return tenants.values().stream().map(Map::copyOf).toList();
    }

    @DgsMutation(field = "createTenant")
    public Map<String, Object> createTenant(@InputArgument Map<String, Object> input) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> tenant = new LinkedHashMap<>();
        tenant.put("id", id);
        tenant.put("name", defaulted(asString(input, "name"), "unnamed"));
        tenant.put("status", defaulted(asString(input, "status"), "ACTIVE"));
        tenants.put(id, tenant);
        appendAuditEvent(id, "system", "createTenant", id, "ALLOW");
        return Map.copyOf(tenant);
    }

    @DgsMutation(field = "addTenant")
    public Map<String, Object> addTenant(@InputArgument Map<String, Object> input) {
        return createTenant(input);
    }

    @DgsMutation(field = "updateTenant")
    public Map<String, Object> updateTenant(@InputArgument String id, @InputArgument Map<String, Object> input) {
        Map<String, Object> existing = tenants.get(id);
        if (existing == null) {
            return null;
        }
        Map<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("name", defaulted(asString(input, "name"), asString(existing, "name")));
        updated.put("status", defaulted(asString(input, "status"), asString(existing, "status")));
        tenants.put(id, updated);
        appendAuditEvent(id, "system", "updateTenant", id, "ALLOW");
        return Map.copyOf(updated);
    }

    @DgsMutation(field = "deleteTenant")
    public boolean deleteTenant(@InputArgument String id) {
        Map<String, Object> removed = tenants.remove(id);
        if (removed != null) {
            appendAuditEvent(id, "system", "deleteTenant", id, "ALLOW");
            return true;
        }
        return false;
    }

    @DgsQuery(field = "getAuditTrail")
    public List<Map<String, Object>> getAuditTrail(@InputArgument String tenantId) {
        return auditEvents.values().stream()
            .filter(event -> tenantId == null || tenantId.isBlank() || tenantId.equals(asString(event, "tenantId")))
            .map(Map::copyOf)
            .toList();
    }

    @DgsQuery(field = "getAuditLogs")
    public List<Map<String, Object>> getAuditLogs(@InputArgument String tenantId) {
        return getAuditTrail(tenantId);
    }

    @DgsQuery(field = "auditLogs")
    public List<Map<String, Object>> auditLogs(@InputArgument String tenantId) {
        return getAuditTrail(tenantId);
    }

    @DgsQuery(field = "filterAuditLogs")
    public List<Map<String, Object>> filterAuditLogs(@InputArgument Map<String, Object> filter) {
        String tenantId = asString(filter, "tenantId");
        String actor = asString(filter, "actor");
        String action = asString(filter, "action");
        String resource = asString(filter, "resource");
        return auditEvents.values().stream()
            .filter(event -> tenantId == null || tenantId.isBlank() || tenantId.equals(asString(event, "tenantId")))
            .filter(event -> actor == null || actor.isBlank() || actor.equals(asString(event, "actor")))
            .filter(event -> action == null || action.isBlank() || action.equals(asString(event, "action")))
            .filter(event -> resource == null || resource.isBlank() || resource.equals(asString(event, "resource")))
            .map(Map::copyOf)
            .toList();
    }

    @DgsQuery(field = "getAuditLogById")
    public Map<String, Object> getAuditLogById(@InputArgument String id) {
        Map<String, Object> event = auditEvents.get(id);
        if (event != null) {
            return Map.copyOf(event);
        }
        return Map.of(
            "id", id,
            "tenantId", "unknown",
            "actor", "unknown",
            "action", "unknown",
            "resource", "unknown",
            "timestamp", Instant.now().toString(),
            "decision", "ALLOW"
        );
    }

    @DgsQuery(field = "getPolicyStatistics")
    public Map<String, Object> getPolicyStatistics(@InputArgument String tenantId) {
        long totalPolicies = policies.values().stream()
            .filter(policy -> tenantId == null || tenantId.isBlank() || tenantId.equals(asString(policy, "tenantId")))
            .count();
        long evaluationCount = auditEvents.values().stream()
            .filter(event -> "evaluatePolicy".equals(asString(event, "action")))
            .filter(event -> tenantId == null || tenantId.isBlank() || tenantId.equals(asString(event, "tenantId")))
            .count();

        return Map.of(
            "tenantId", defaulted(tenantId, "default"),
            "totalPolicies", Math.toIntExact(totalPolicies),
            "evaluationCount", Math.toIntExact(evaluationCount)
        );
    }

    @DgsQuery(field = "policyStats")
    public Map<String, Object> policyStats(@InputArgument String tenantId) {
        return getPolicyStatistics(tenantId);
    }

    @DgsQuery(field = "getUserActivitySummary")
    public List<Map<String, Object>> getUserActivitySummary(@InputArgument String tenantId) {
        Map<String, Integer> byActor = new LinkedHashMap<>();
        Map<String, String> lastSeen = new LinkedHashMap<>();

        for (Map<String, Object> event : auditEvents.values()) {
            if (tenantId != null && !tenantId.isBlank() && !tenantId.equals(asString(event, "tenantId"))) {
                continue;
            }
            String actor = defaulted(asString(event, "actor"), "system");
            byActor.put(actor, byActor.getOrDefault(actor, 0) + 1);
            lastSeen.put(actor, defaulted(asString(event, "timestamp"), Instant.now().toString()));
        }

        return byActor.entrySet().stream()
            .map(entry -> {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("identityId", entry.getKey());
                summary.put("operations", entry.getValue());
                summary.put("lastSeen", defaulted(lastSeen.get(entry.getKey()), Instant.now().toString()));
                return summary;
            })
            .toList();
    }

    @DgsQuery(field = "userActivity")
    public List<Map<String, Object>> userActivity(@InputArgument String tenantId) {
        return getUserActivitySummary(tenantId);
    }

    @DgsQuery(field = "getResourceAccessPatterns")
    public List<Map<String, Object>> getResourceAccessPatterns(@InputArgument String tenantId) {
        Map<String, Integer> byResource = new LinkedHashMap<>();
        for (Map<String, Object> event : auditEvents.values()) {
            if (tenantId != null && !tenantId.isBlank() && !tenantId.equals(asString(event, "tenantId"))) {
                continue;
            }
            String resource = defaulted(asString(event, "resource"), "unknown");
            byResource.put(resource, byResource.getOrDefault(resource, 0) + 1);
        }

        return byResource.entrySet().stream()
            .map(entry -> {
                Map<String, Object> pattern = new LinkedHashMap<>();
                pattern.put("resource", entry.getKey());
                pattern.put("accesses", entry.getValue());
                return pattern;
            })
            .toList();
    }

    @DgsQuery(field = "resourceAccess")
    public List<Map<String, Object>> resourceAccess(@InputArgument String tenantId) {
        return getResourceAccessPatterns(tenantId);
    }

    private void appendAuditEvent(String tenantId, String actor, String action, String resource, String decision) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", id);
        event.put("tenantId", defaulted(tenantId, "default"));
        event.put("actor", defaulted(actor, "system"));
        event.put("action", defaulted(action, "unknown"));
        event.put("resource", defaulted(resource, "unknown"));
        event.put("timestamp", Instant.now().toString());
        event.put("decision", defaulted(decision, "ALLOW"));
        auditEvents.put(id, event);
    }

    private String asString(Map<String, Object> values, String key) {
        if (values == null) {
            return null;
        }
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String defaulted(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}