package com.ironbucket.graphiteforge.model;

import com.ironbucket.pactumscroll.policy.PolicyDecision;
import java.util.List;

public record PolicyEvaluationResult(
    PolicyDecision decision,
    List<String> matchedPolicies,
    String reason
) {
    public PolicyEvaluationResult {
        if (decision == null) {
            throw new IllegalArgumentException("Decision cannot be null");
        }
        if (matchedPolicies == null) {
            matchedPolicies = List.of();
        }
    }

    public static PolicyEvaluationResult allow(List<String> policies, String reason) {
        return new PolicyEvaluationResult(PolicyDecision.ALLOW, policies, reason);
    }

    public static PolicyEvaluationResult deny(String reason) {
        return new PolicyEvaluationResult(PolicyDecision.DENY, List.of(), reason);
    }

    public List<String> matchedRules() {
        return matchedPolicies;
    }
}
