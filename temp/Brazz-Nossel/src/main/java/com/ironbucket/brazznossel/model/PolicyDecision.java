package com.ironbucket.brazznossel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PolicyDecision - Result of policy evaluation by Claimspindel/Policy Engine.
 * 
 * Represents the decision made by the policy evaluation engine when evaluating
 * whether an action should be allowed or denied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDecision {
    
    /**
     * ALLOW or DENY
     */
    private String decision;
    
    /**
     * Policy ID or reason for the decision
     */
    private String reason;
    
    /**
     * Detailed explanation (for audit and debugging)
     */
    private String explanation;
    
    /**
     * Optional: Matched policy details
     */
    private String matchedPolicy;
    
    /**
     * Create an ALLOW decision
     */
    public static PolicyDecision allow(String reason) {
        return PolicyDecision.builder()
                .decision("ALLOW")
                .reason(reason)
                .build();
    }
    
    /**
     * Create a DENY decision
     */
    public static PolicyDecision deny(String reason) {
        return PolicyDecision.builder()
                .decision("DENY")
                .reason(reason)
                .build();
    }
    
    /**
     * Check if decision is ALLOW
     */
    public boolean isAllow() {
        return "ALLOW".equalsIgnoreCase(decision);
    }
    
    /**
     * Check if decision is DENY
     */
    public boolean isDeny() {
        return "DENY".equalsIgnoreCase(decision);
    }
}
