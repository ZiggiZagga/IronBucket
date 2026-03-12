package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.model.PolicyEvaluationContext;
import com.ironbucket.graphiteforge.model.PolicyEvaluationResult;
import com.ironbucket.graphiteforge.model.PolicyInput;
import com.ironbucket.graphiteforge.model.PolicyRule;
import com.ironbucket.graphiteforge.service.PolicyManagementService;

import java.util.List;

public class PolicyResolver {

    private final PolicyManagementService policyService;

    public PolicyResolver() {
        this(new PolicyManagementService());
    }

    public PolicyResolver(PolicyManagementService policyService) {
        this.policyService = policyService;
    }

    public List<PolicyRule> listPolicies(String jwtToken) {
        return policyService.listPolicies(jwtToken);
    }

    public PolicyRule getPolicy(String jwtToken, String policyId) {
        return policyService.getPolicyById(jwtToken, policyId);
    }

    public PolicyRule createPolicy(String jwtToken, PolicyInput input) {
        return policyService.createPolicy(jwtToken, input);
    }

    public PolicyRule updatePolicy(String jwtToken, String policyId, PolicyInput input) {
        return policyService.updatePolicy(jwtToken, policyId, input);
    }

    public boolean deletePolicy(String jwtToken, String policyId) {
        return policyService.deletePolicy(jwtToken, policyId);
    }

    public List<PolicyRule> policies(String jwtToken) {
        return policyService.listPolicies(jwtToken);
    }

    public PolicyRule policyById(String jwtToken, String policyId) {
        return policyService.getPolicyById(jwtToken, policyId);
    }

    public List<PolicyRule> policiesByTenant(String jwtToken, String tenant) {
        return policyService.getPoliciesByTenant(jwtToken, tenant);
    }

    public PolicyEvaluationResult dryRunPolicy(String jwtToken, PolicyInput policy, String operation, String resource) {
        PolicyEvaluationContext context = new PolicyEvaluationContext(policy.tenant(), policy.roles(), operation, resource);
        return policyService.evaluatePolicy(jwtToken, createPolicy(jwtToken, policy).id(), context);
    }
}
