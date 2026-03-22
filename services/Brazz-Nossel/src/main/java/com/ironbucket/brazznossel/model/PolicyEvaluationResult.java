package com.ironbucket.brazznossel.model;

import com.ironbucket.pactumscroll.policy.PolicyDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PolicyEvaluationResult – result of a policy evaluation for an S3 action.
 *
 * Carries the type-safe {@link PolicyDecision} verdict together with
 * the reason, optional explanation, and the matched policy identifier.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEvaluationResult {

    /** Type-safe verdict from the policy engine. */
    private PolicyDecision decision;

    /** Policy ID or short reason for the decision. */
    private String reason;

    /** Detailed explanation (for audit and debugging). */
    private String explanation;

    /** Optional: matched policy identifier. */
    private String matchedPolicy;

    // ── Factory helpers ───────────────────────────────────────────────────────

    public static PolicyEvaluationResult allow(String reason) {
        return PolicyEvaluationResult.builder()
                .decision(PolicyDecision.ALLOW)
                .reason(reason)
                .build();
    }

    public static PolicyEvaluationResult deny(String reason) {
        return PolicyEvaluationResult.builder()
                .decision(PolicyDecision.DENY)
                .reason(reason)
                .build();
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    public boolean isAllow() {
        return PolicyDecision.ALLOW == decision;
    }

    public boolean isDeny() {
        return PolicyDecision.DENY == decision;
    }
}
