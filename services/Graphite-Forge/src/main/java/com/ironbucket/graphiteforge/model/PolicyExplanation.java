package com.ironbucket.graphiteforge.model;

import java.util.List;

public record PolicyExplanation(
    PolicyDecision decision,
    List<String> evaluatedPolicies,
    List<String> matchedRules,
    List<String> failedConditions,
    String summary
) {
    public PolicyExplanation {
        if (decision == null) {
            throw new IllegalArgumentException("Decision cannot be null");
        }
    }

    public List<String> evaluationSteps() {
        return evaluatedPolicies;
    }
}
