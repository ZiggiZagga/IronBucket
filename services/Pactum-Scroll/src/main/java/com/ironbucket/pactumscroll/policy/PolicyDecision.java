package com.ironbucket.pactumscroll.policy;

/**
 * Canonical policy decision vocabulary shared across all IronBucket services.
 *
 * <ul>
 *   <li>{@link #ALLOW} – the action is permitted</li>
 *   <li>{@link #DENY} – the action is explicitly denied</li>
 *   <li>{@link #NOT_APPLICABLE} – no policy matched; behaviour is determined by the caller</li>
 * </ul>
 */
public enum PolicyDecision {
    ALLOW,
    DENY,
    NOT_APPLICABLE
}
