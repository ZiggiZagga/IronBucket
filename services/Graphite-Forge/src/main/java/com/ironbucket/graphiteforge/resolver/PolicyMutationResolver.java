package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.model.PolicyInput;
import com.ironbucket.graphiteforge.model.PolicyRule;
import com.ironbucket.graphiteforge.model.PolicyValidationResult;
import com.ironbucket.graphiteforge.service.PolicyManagementService;

import java.util.List;

public class PolicyMutationResolver {

    private final PolicyManagementService policyService;

    public PolicyMutationResolver() {
        this(new PolicyManagementService());
    }

    public PolicyMutationResolver(PolicyManagementService policyService) {
        this.policyService = policyService;
    }

    public PolicyRule createPolicy(PolicyInput input) {
        return policyService.createPolicy("internal", input);
    }

    public PolicyRule addPolicy(PolicyInput input) {
        return createPolicy(input);
    }

    public PolicyRule updatePolicy(String id, PolicyInput input) {
        return policyService.updatePolicy("internal", id, input);
    }

    public boolean deletePolicy(String id) {
        return policyService.deletePolicy("internal", id);
    }

    public PolicyValidationResult validatePolicy(PolicyInput input) {
        if (input == null) {
            return PolicyValidationResult.failure(List.of("Policy input is required"));
        }
        return PolicyValidationResult.success();
    }
}
