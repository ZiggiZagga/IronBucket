/**
 * Normalized Identity Model
 *
 * Represents a standardized user identity across all IronBucket components.
 * Created after JWT validation and claim normalization.
 */
/**
 * Normalized identity representation for all IronBucket components
 */
export interface NormalizedIdentity {
    userId: string;
    username: string;
    issuer: string;
    issuedAt: number;
    expiresAt: number;
    roles: string[];
    realmRoles: string[];
    resourceRoles: Map<string, string[]>;
    tenant: string;
    region?: string;
    groups: string[];
    email?: string;
    firstName?: string;
    lastName?: string;
    fullName?: string;
    ipAddress?: string;
    userAgent?: string;
    requestId?: string;
    isServiceAccount: boolean;
    rawClaims: Record<string, any>;
    createdAt: number;
}
/**
 * JWT Validation Result
 */
export interface JWTValidationResult {
    valid: boolean;
    error?: string;
    claims?: Record<string, any>;
}
/**
 * Claim Normalization Result
 */
export interface ClaimNormalizationResult {
    success: boolean;
    error?: string;
    identity?: NormalizedIdentity;
}
//# sourceMappingURL=identity.d.ts.map