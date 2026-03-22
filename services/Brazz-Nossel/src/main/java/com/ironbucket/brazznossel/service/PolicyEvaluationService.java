package com.ironbucket.brazznossel.service;

import com.ironbucket.brazznossel.model.PolicyEvaluationResult;
import com.ironbucket.pactumscroll.identity.NormalizedIdentity;
import reactor.core.publisher.Mono;

/**
 * PolicyEvaluationService - Interface for policy evaluation.
 * 
 * This service delegates to Claimspindel (claims-based routing) and policy engine
 * to evaluate whether a requested action should be allowed or denied.
 * 
 * Implementation coordinates with:
 * - Claimspindel: Claims-based routing and filtering
 * - Policy Engine: Policy evaluation (ABAC/RBAC)
 * - Audit Service: Logging of policy decisions
 */
public interface PolicyEvaluationService {
    
    /**
     * Evaluate a policy decision for an action by an identity.
     * 
     * @param identity The normalized identity performing the action
     * @param action The S3 action (e.g., "s3:GetObject", "s3:PutObject")
     * @param resource The ARN or resource identifier (e.g., "arn:aws:s3:::bucket/key")
     * @return A Mono<PolicyEvaluationResult> with ALLOW or DENY
     */
    Mono<PolicyEvaluationResult> evaluate(NormalizedIdentity identity, String action, String resource);
}
