package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.model.PolicyEvaluationResult;
import com.ironbucket.graphiteforge.model.PolicyInput;
import com.ironbucket.graphiteforge.model.PolicyRule;
import com.ironbucket.graphiteforge.service.PolicyManagementService;

import java.util.List;

public class PolicyQueryResolver {

    private final PolicyManagementService policyService;

    public PolicyQueryResolver() {
        this(new PolicyManagementService());
    }

    public PolicyQueryResolver(PolicyManagementService policyService) {
        this.policyService = policyService;
    }

    public List<PolicyRule> listPolicies() {
        return policyService.listPolicies("internal");
    }

    public List<PolicyRule> getAllPolicies() {
        return listPolicies();
    }

    public PolicyRule getPolicyById(String id) {
        return policyService.getPolicyById("internal", id);
    }

    public PolicyRule getPolicy(String id) {
        return getPolicyById(id);
    }

    public List<PolicyRule> searchPolicies(String query) {
        return listPolicies().stream().filter(policy -> policy.id().contains(query)).toList();
    }

    public PolicyEvaluationResult evaluatePolicy(PolicyInput input) {
        String operation = input.operations() != null && !input.operations().isEmpty() ? input.operations().get(0) : "s3:GetObject";
        return policyService.dryRunPolicy("internal", input, operation, "arn:aws:s3:::example/object");
    }

    public PolicyEvaluationResult dryRun(PolicyInput input) {
        return evaluatePolicy(input);
    }
}
