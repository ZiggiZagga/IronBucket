package com.ironbucket.graphiteforge.dgs;

import com.ironbucket.graphiteforge.exception.PolicyNotFoundException;
import com.ironbucket.graphiteforge.model.PolicyInput;
import com.ironbucket.graphiteforge.model.PolicyRule;
import com.ironbucket.graphiteforge.service.PolicyManagementService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * DGS data fetcher for all Policy queries and mutations defined in schema.graphqls.
 *
 * Schema Policy type: {id, tenantId, principal, resource, action, effect, version, enabled}
 * Java PolicyRule type: {id, tenant, roles, buckets, tags, operations, version, deleted}
 *
 * Mapping: principal→roles[0], resource→buckets[0], action→operations[0], effect stored in tags
 */
@DgsComponent
public class PolicyDataFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyDataFetcher.class);

    private final PolicyManagementService policyService;

    @Autowired
    public PolicyDataFetcher(PolicyManagementService policyService) {
        this.policyService = policyService;
    }

    // ───────────────────── Queries ─────────────────────

    @DgsQuery(field = "getPolicy")
    public Mono<Map<String, Object>> getPolicy(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql getPolicy id={}", id);
            PolicyRule rule = policyService.getPolicyById("internal", id);
            return toSchemaMap(rule);
        });
    }

    @DgsQuery(field = "getPolicyById")
    public Mono<Map<String, Object>> getPolicyById(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql getPolicyById id={}", id);
            PolicyRule rule = policyService.getPolicyById("internal", id);
            return toSchemaMap(rule);
        });
    }

    @DgsQuery(field = "listPolicies")
    public Mono<List<Map<String, Object>>> listPolicies() {
        return fromBlocking(() -> {
            LOG.info("graphql listPolicies");
            return policyService.listPolicies("internal").stream()
                .map(this::toSchemaMap)
                .collect(Collectors.toList());
        });
    }

    @DgsQuery(field = "searchPolicies")
    public Mono<List<Map<String, Object>>> searchPolicies(@InputArgument String query) {
        return fromBlocking(() -> {
            LOG.info("graphql searchPolicies query={}", query);
            String q = (query == null) ? "" : query.toLowerCase();
            return policyService.listPolicies("internal").stream()
                .filter(r -> q.isBlank()
                    || r.id().toLowerCase().contains(q)
                    || r.tenant().toLowerCase().contains(q)
                    || anyContains(r.roles(), q)
                    || anyContains(r.buckets(), q)
                    || anyContains(r.operations(), q))
                .map(this::toSchemaMap)
                .collect(Collectors.toList());
        });
    }

    @DgsQuery(field = "evaluatePolicy")
    public Mono<Map<String, Object>> evaluatePolicy(@InputArgument Map<String, Object> input) {
        return fromBlocking(() -> {
            String tenantId = str(input, "tenantId");
            String principal = str(input, "principal");
            String resource  = str(input, "resource");
            String action    = str(input, "action");
            LOG.info("graphql evaluatePolicy tenantId={} principal={} resource={} action={}",
                tenantId, principal, resource, action);

            boolean allow = policyService.listPolicies("internal").stream()
                .filter(r -> !r.deleted())
                .filter(r -> tenantId.equals(r.tenant()))
                .filter(r -> r.roles().contains(principal) || r.roles().isEmpty())
                .filter(r -> r.buckets().stream().anyMatch(b -> resource.startsWith(b) || b.equals("*")) || r.buckets().isEmpty())
                .filter(r -> r.operations().contains(action) || r.operations().isEmpty())
                .anyMatch(r -> !"DENY".equalsIgnoreCase(effectOf(r)));

            String reason = allow
                ? "Allowed by matching policy in Graphite-Forge policy store"
                : "No matching ALLOW policy found for principal=" + principal + " action=" + action;

            return Map.of("allow", allow, "reason", reason);
        });
    }

    // ───────────────────── Mutations ─────────────────────

    @DgsMutation(field = "createPolicy")
    public Mono<Map<String, Object>> createPolicy(@InputArgument Map<String, Object> input) {
        return fromBlocking(() -> {
            LOG.info("graphql createPolicy input={}", input);
            PolicyInput pi = fromSchemaInput(input);
            PolicyRule created = policyService.createPolicy("internal", pi);
            return toSchemaMap(created, str(input, "effect"));
        });
    }

    @DgsMutation(field = "addPolicy")
    public Mono<Map<String, Object>> addPolicy(@InputArgument Map<String, Object> input) {
        return createPolicy(input).block() == null
            ? Mono.error(new IllegalStateException("addPolicy failed"))
            : fromBlocking(() -> {
                PolicyInput pi = fromSchemaInput(input);
                PolicyRule created = policyService.createPolicy("internal", pi);
                return toSchemaMap(created, str(input, "effect"));
            });
    }

    @DgsMutation(field = "updatePolicy")
    public Mono<Map<String, Object>> updatePolicy(
        @InputArgument String id,
        @InputArgument Map<String, Object> input
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql updatePolicy id={}", id);
            PolicyInput pi = fromSchemaInput(input);
            PolicyRule updated = policyService.updatePolicy("internal", id, pi);
            return toSchemaMap(updated, str(input, "effect"));
        });
    }

    @DgsMutation(field = "deletePolicy")
    public Mono<Boolean> deletePolicy(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql deletePolicy id={}", id);
            return policyService.deletePolicy("internal", id);
        });
    }

    @DgsMutation(field = "validatePolicy")
    public Mono<Map<String, Object>> validatePolicy(@InputArgument Map<String, Object> input) {
        return fromBlocking(() -> {
            LOG.info("graphql validatePolicy input={}", input);
            if (input == null || str(input, "tenantId").isBlank()) {
                return Map.of("valid", false, "errors", List.of("tenantId is required"));
            }
            if (str(input, "principal").isBlank()) {
                return Map.of("valid", false, "errors", List.of("principal is required"));
            }
            if (str(input, "action").isBlank()) {
                return Map.of("valid", false, "errors", List.of("action is required"));
            }
            String effect = str(input, "effect").toUpperCase();
            if (!effect.equals("ALLOW") && !effect.equals("DENY")) {
                return Map.of("valid", false, "errors", List.of("effect must be ALLOW or DENY"));
            }
            return Map.of("valid", true, "errors", List.of());
        });
    }

    // ───────────────────── Helpers ─────────────────────

    private PolicyInput fromSchemaInput(Map<String, Object> input) {
        String tenantId  = str(input, "tenantId");
        String principal = str(input, "principal");
        String resource  = str(input, "resource");
        String action    = str(input, "action");
        String effect    = str(input, "effect");

        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (principal.isBlank()) throw new IllegalArgumentException("principal is required");

        // Store effect as a tag (e.g. "effect:ALLOW") so we can retrieve it
        List<String> tags = effect.isBlank() ? List.of("effect:ALLOW") : List.of("effect:" + effect.toUpperCase());

        return new PolicyInput(
            tenantId,
            List.of(principal),
            resource.isBlank() ? List.of("*") : List.of(resource),
            tags,
            action.isBlank() ? List.of("*") : List.of(action)
        );
    }

    private Map<String, Object> toSchemaMap(PolicyRule rule) {
        return toSchemaMap(rule, null);
    }

    private Map<String, Object> toSchemaMap(PolicyRule rule, String overrideEffect) {
        String effect = overrideEffect != null ? overrideEffect.toUpperCase() : effectOf(rule);
        return Map.of(
            "id",        rule.id(),
            "tenantId",  rule.tenant(),
            "principal", rule.roles().isEmpty() ? "" : rule.roles().get(0),
            "resource",  rule.buckets().isEmpty() ? "*" : rule.buckets().get(0),
            "action",    rule.operations().isEmpty() ? "*" : rule.operations().get(0),
            "effect",    effect,
            "version",   rule.version(),
            "enabled",   !rule.deleted()
        );
    }

    /** Extract the ALLOW/DENY effect stored in tags as "effect:ALLOW" or "effect:DENY". */
    private String effectOf(PolicyRule rule) {
        return rule.tags().stream()
            .filter(t -> t.startsWith("effect:"))
            .map(t -> t.substring("effect:".length()))
            .findFirst()
            .orElse("ALLOW");
    }

    private boolean anyContains(List<String> list, String q) {
        return list.stream().anyMatch(s -> s.toLowerCase().contains(q));
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private <T> Mono<T> fromBlocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
