/**
 * JWT Validation Implementation
 *
 * Provides JWT signature verification, expiration checking, claim validation,
 * issuer whitelisting, and audience matching.
 */
export interface JWTValidationResult {
    valid: boolean;
    error?: string;
    claims?: Record<string, any>;
}
export interface JWTValidationConfig {
    issuerWhitelist?: string[];
    expectedAudience?: string | string[];
    clockSkewSeconds?: number;
    requiredClaims?: string[];
}
/**
 * Validate JWT signature, expiration, required claims, issuer, and audience
 */
export declare function validateJWT(token: string, secret?: string, config?: JWTValidationConfig): JWTValidationResult;
/**
 * Extract roles from JWT claims
 * Supports both realm roles and resource-specific roles
 */
export declare function extractRoles(claims: Record<string, any>): {
    realmRoles: string[];
    resourceRoles: Map<string, string[]>;
    allRoles: string[];
};
/**
 * Detect if JWT is a service account based on subject prefix
 */
export declare function isServiceAccount(claims: Record<string, any>): boolean;
//# sourceMappingURL=jwt-validator.d.ts.map