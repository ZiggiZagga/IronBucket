package com.ironbucket.graphiteforge.service;

import com.ironbucket.graphiteforge.exception.PolicyNotFoundException;
import com.ironbucket.graphiteforge.model.PolicyEvaluationContext;
import com.ironbucket.pactumscroll.policy.PolicyDecision;
import com.ironbucket.graphiteforge.model.PolicyEvaluationResult;
import com.ironbucket.graphiteforge.model.PolicyInput;
import com.ironbucket.graphiteforge.model.PolicyRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class PolicyManagementService {

    private final List<PolicyRule> policyStore = new CopyOnWriteArrayList<>();

    public List<PolicyRule> listPolicies(String jwtToken) {
        return policyStore.stream()
            .filter(policy -> !policy.deleted())
            .sorted(Comparator.comparing(PolicyRule::tenant).thenComparing(PolicyRule::id))
            .toList();
    }

    public PolicyRule getPolicyById(String jwtToken, String policyId) {
        return policyStore.stream()
            .filter(policy -> policy.id().equals(policyId) && !policy.deleted())
            .findFirst()
            .orElseThrow(() -> new PolicyNotFoundException(policyId));
    }

    public List<PolicyRule> getPoliciesByTenant(String jwtToken, String tenant) {
        if (tenant == null || tenant.isBlank()) {
            return List.of();
        }
        return listPolicies(jwtToken).stream()
            .filter(policy -> tenant.equals(policy.tenant()))
            .toList();
    }

    public PolicyRule createPolicy(String jwtToken, PolicyInput input) {
        PolicyInput normalized = normalizeInput(input);
        PolicyRule created = new PolicyRule(
            UUID.randomUUID().toString(),
            normalized.tenant(),
            normalized.roles(),
            normalized.buckets(),
            normalized.tags(),
            normalized.operations(),
            1,
            false
        );
        policyStore.add(created);
        return created;
    }

    public PolicyRule updatePolicy(String jwtToken, String policyId, PolicyInput input) {
        PolicyRule previous = getPolicyById(jwtToken, policyId);
        PolicyInput normalized = normalizeInput(input);
        PolicyRule updated = new PolicyRule(
            previous.id(),
            normalized.tenant(),
            normalized.roles(),
            normalized.buckets(),
            normalized.tags(),
            normalized.operations(),
            previous.version() + 1,
            false
        );
        policyStore.removeIf(policy -> policy.id().equals(policyId));
        policyStore.add(updated);
        return updated;
    }

    public boolean deletePolicy(String jwtToken, String policyId) {
        PolicyRule existing = getPolicyById(jwtToken, policyId);
        policyStore.removeIf(policy -> policy.id().equals(policyId));
        policyStore.add(new PolicyRule(
            existing.id(),
            existing.tenant(),
            existing.roles(),
            existing.buckets(),
            existing.tags(),
            existing.operations(),
            existing.version() + 1,
            true
        ));
        return true;
    }

    public PolicyEvaluationResult evaluatePolicy(String jwtToken, String policyId, PolicyEvaluationContext context) {
        if (context == null) {
            return PolicyEvaluationResult.deny("Missing policy evaluation context");
        }

        PolicyRule policy = getPolicyById(jwtToken, policyId);
        boolean tenantAllowed = Objects.equals(policy.tenant(), context.tenant());
        boolean operationAllowed = matchesOperation(policy.operations(), context.operation());
        boolean roleAllowed = intersects(normalizeList(policy.roles()), normalizeList(context.roles()));
        boolean resourceAllowed = matchesResource(policy.buckets(), context.resource());

        boolean allowed = tenantAllowed && operationAllowed && roleAllowed && resourceAllowed;
        return allowed
            ? PolicyEvaluationResult.allow(List.of(policy.id()), "Allowed by Claimspindel-compatible policy evaluation")
            : PolicyEvaluationResult.deny("Denied by tenant/role/operation/resource policy constraints");
    }

    public PolicyEvaluationResult dryRunPolicy(String jwtToken, PolicyInput policy, String operation, String resource) {
        PolicyInput normalized = normalizeInput(policy);
        PolicyEvaluationContext context = new PolicyEvaluationContext(
            normalized.tenant(),
            normalized.roles(),
            operation,
            resource
        );

        boolean allowed = matchesOperation(normalized.operations(), operation)
            && matchesResource(normalized.buckets(), resource)
            && intersects(normalized.roles(), context.roles());

        PolicyDecision decision = allowed
            ? PolicyDecision.ALLOW
            : PolicyDecision.DENY;
        return new PolicyEvaluationResult(decision, List.of("dry-run"), decision == PolicyDecision.ALLOW ? "Allowed" : "Denied");
    }

    private PolicyInput normalizeInput(PolicyInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Policy input is required");
        }

        List<String> roles = normalizeList(input.roles());
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }

        List<String> operations = normalizeList(input.operations());
        if (operations.isEmpty()) {
            operations = List.of("s3:GetObject");
        }

        List<String> buckets = normalizeList(input.buckets());
        if (buckets.isEmpty()) {
            buckets = List.of("*");
        }

        return new PolicyInput(
            input.tenant(),
            roles,
            buckets,
            normalizeList(input.tags()),
            operations
        );
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private boolean matchesOperation(List<String> allowedOperations, String requestedOperation) {
        List<String> normalizedOps = normalizeList(allowedOperations).stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();

        String requested = requestedOperation == null ? "" : requestedOperation.trim().toLowerCase(Locale.ROOT);
        return normalizedOps.contains("s3:*") || normalizedOps.contains(requested);
    }

    private boolean matchesResource(List<String> allowedBuckets, String resource) {
        List<String> buckets = normalizeList(allowedBuckets);
        if (buckets.contains("*")) {
            return true;
        }

        String requestedBucket = extractBucket(resource);
        return buckets.stream().anyMatch(bucket -> bucket.equalsIgnoreCase(requestedBucket));
    }

    private String extractBucket(String resource) {
        if (resource == null || resource.isBlank()) {
            return "";
        }

        int slash = resource.indexOf('/');
        String candidate = slash > 0 ? resource.substring(0, slash) : resource;
        String arnPrefix = "arn:aws:s3:::";
        if (candidate.startsWith(arnPrefix)) {
            candidate = candidate.substring(arnPrefix.length());
        }
        return candidate;
    }

    private boolean intersects(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return left.stream().map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(value -> right.stream().anyMatch(other -> value.equalsIgnoreCase(other)));
    }
}
