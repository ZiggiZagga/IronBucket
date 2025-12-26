/**
 * JWT Test Fixture Utilities
 * Provides helper functions to create and sign test JWTs
 */
/**
 * Generate a valid JWT with custom claims
 */
export declare function createTestJWT(overrides?: any): string;
/**
 * Create a JWT with expired token
 */
export declare function createExpiredJWT(): string;
/**
 * Create a JWT with invalid signature
 */
export declare function createInvalidSignatureJWT(): string;
/**
 * Create a malformed JWT (missing parts)
 */
export declare function createMalformedJWT(): string;
/**
 * Get the test signing secret
 */
export declare function getTestSecret(): string;
/**
 * Get a valid JWKS endpoint response for testing
 */
export declare function getMockJWKS(): {
    keys: {
        alg: string;
        kty: string;
        use: string;
        kid: string;
        key: string;
    }[];
};
/**
 * Service account JWT fixture
 */
export declare function createServiceAccountJWT(): string;
/**
 * Admin role JWT fixture
 */
export declare function createAdminJWT(): string;
/**
 * Dev team JWT fixture
 */
export declare function createDevJWT(): string;
/**
 * JWT with no tenant (should use default)
 */
export declare function createJWTWithoutTenant(): string;
/**
 * JWT with future issued-at claim (clock skew test)
 */
export declare function createFutureIssuedJWT(): string;
/**
 * JWT with missing required claims
 */
export declare function createJWTMissingClaim(claimName: string): string;
//# sourceMappingURL=test-fixtures.d.ts.map