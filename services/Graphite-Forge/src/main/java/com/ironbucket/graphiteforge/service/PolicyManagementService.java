package com.ironbucket.graphiteforge.service;

import com.ironbucket.graphiteforge.exception.PolicyNotFoundException;
import com.ironbucket.graphiteforge.model.PolicyDecision;
import com.ironbucket.graphiteforge.model.PolicyEvaluationContext;
import com.ironbucket.graphiteforge.model.PolicyEvaluationResult;
import com.ironbucket.graphiteforge.model.PolicyInput;
import com.ironbucket.graphiteforge.model.PolicyRule;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class PolicyManagementService {

    private final List<PolicyRule> policyStore = new CopyOnWriteArrayList<>();

    public List<PolicyRule> listPolicies(String jwtToken) {
        return List.copyOf(policyStore);
    }

    public PolicyRule getPolicyById(String jwtToken, String policyId) {
        return policyStore.stream()
            .filter(policy -> policy.id().equals(policyId))
            .findFirst()
            .orElseThrow(() -> new PolicyNotFoundException(policyId));
    }

    public List<PolicyRule> getPoliciesByTenant(String jwtToken, String tenant) {
        return policyStore.stream().filter(policy -> policy.tenant().equals(tenant)).toList();
    }

    public PolicyRule createPolicy(String jwtToken, PolicyInput input) {
        PolicyRule created = new PolicyRule(
            UUID.randomUUID().toString(),
            input.tenant(),
            input.roles(),
            input.buckets(),
            input.tags(),
            input.operations(),
            1,
            false
        );
        policyStore.add(created);
        return created;
    }

    public PolicyRule updatePolicy(String jwtToken, String policyId, PolicyInput input) {
        PolicyRule previous = getPolicyById(jwtToken, policyId);
        PolicyRule updated = new PolicyRule(
            previous.id(),
            input.tenant(),
            input.roles(),
            input.buckets(),
            input.tags(),
            input.operations(),
            previous.version() + 1,
            false
        );
        policyStore.removeIf(policy -> policy.id().equals(policyId));
        policyStore.add(updated);
        return updated;
    }

    public boolean deletePolicy(String jwtToken, String policyId) {
        return policyStore.removeIf(policy -> policy.id().equals(policyId));
    }

    public PolicyEvaluationResult evaluatePolicy(String jwtToken, String policyId, PolicyEvaluationContext context) {
        PolicyRule policy = getPolicyById(jwtToken, policyId);
        boolean allowed = policy.operations().contains("s3:*") || policy.operations().contains(context.operation());
        return allowed
            ? PolicyEvaluationResult.allow(List.of(policy.id()), "Allowed by policy")
            : PolicyEvaluationResult.deny("Operation not allowed");
    }

    public PolicyEvaluationResult dryRunPolicy(String jwtToken, PolicyInput policy, String operation, String resource) {
        List<String> operations = new ArrayList<>(policy.operations());
        PolicyDecision decision = operations.contains("s3:*") || operations.contains(operation)
            ? PolicyDecision.ALLOW
            : PolicyDecision.DENY;
        return new PolicyEvaluationResult(decision, List.of("dry-run"), decision == PolicyDecision.ALLOW ? "Allowed" : "Denied");
    }
}
