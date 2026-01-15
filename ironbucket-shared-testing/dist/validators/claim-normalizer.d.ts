/**
 * Claim Normalization Implementation
 *
 * Transforms raw JWT claims into a standardized NormalizedIdentity format
 */
import { NormalizedIdentity } from '../types/identity';
export interface ClaimNormalizationConfig {
    defaultTenant?: string;
    multiTenantMode?: boolean;
}
/**
 * Normalize JWT claims into standardized NormalizedIdentity
 */
export declare function normalizeClaims(claims: Record<string, any>, enrichmentContext?: {
    ipAddress?: string;
    userAgent?: string;
    requestId?: string;
}, config?: ClaimNormalizationConfig): NormalizedIdentity;
/**
 * Resolve username from JWT claims with fallback chain
 */
export declare function resolveUsername(claims: Record<string, any>): string;
/**
 * Resolve tenant from claims or configuration
 */
export declare function resolveTenant(claims: Record<string, any>, config?: ClaimNormalizationConfig): string;
/**
 * Validate that normalized identity has all required fields
 */
export declare function validateNormalizedIdentity(identity: NormalizedIdentity): {
    valid: boolean;
    errors: string[];
};
//# sourceMappingURL=claim-normalizer.d.ts.map