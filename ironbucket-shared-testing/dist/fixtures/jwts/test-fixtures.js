"use strict";
/**
 * JWT Test Fixture Utilities
 * Provides helper functions to create and sign test JWTs
 */
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.createTestJWT = createTestJWT;
exports.createExpiredJWT = createExpiredJWT;
exports.createInvalidSignatureJWT = createInvalidSignatureJWT;
exports.createMalformedJWT = createMalformedJWT;
exports.getTestSecret = getTestSecret;
exports.getMockJWKS = getMockJWKS;
exports.createServiceAccountJWT = createServiceAccountJWT;
exports.createAdminJWT = createAdminJWT;
exports.createDevJWT = createDevJWT;
exports.createJWTWithoutTenant = createJWTWithoutTenant;
exports.createFutureIssuedJWT = createFutureIssuedJWT;
exports.createJWTMissingClaim = createJWTMissingClaim;
const jsonwebtoken_1 = __importDefault(require("jsonwebtoken"));
/**
 * Default test signing key
 */
const TEST_SECRET = 'test-signing-key-for-unit-tests';
/**
 * Generate a valid JWT with custom claims
 */
function createTestJWT(overrides = {}) {
    const now = Math.floor(Date.now() / 1000);
    const payload = {
        sub: 'test-user-123',
        iss: 'https://idp.example.com',
        aud: 'sentinel-gear-app',
        iat: now,
        exp: now + 3600,
        email: 'alice@example.com',
        preferred_username: 'alice',
        given_name: 'Alice',
        family_name: 'Smith',
        realm_access: {
            roles: ['user', 'viewer']
        },
        resource_access: {
            'sentinel-gear-app': {
                roles: ['s3:read', 's3:write']
            }
        },
        tenant: 'acme-corp',
        region: 'eu-central-1',
        groups: ['engineering', 'platform'],
        ...overrides
    };
    return jsonwebtoken_1.default.sign(payload, TEST_SECRET, { algorithm: 'HS256' });
}
/**
 * Create a JWT with expired token
 */
function createExpiredJWT() {
    const now = Math.floor(Date.now() / 1000);
    return createTestJWT({
        exp: now - 3600 // Expired 1 hour ago
    });
}
/**
 * Create a JWT with invalid signature
 */
function createInvalidSignatureJWT() {
    const token = createTestJWT();
    // Corrupt the signature
    const parts = token.split('.');
    parts[2] = 'invalidsignature';
    return parts.join('.');
}
/**
 * Create a malformed JWT (missing parts)
 */
function createMalformedJWT() {
    return 'invalid.jwt';
}
/**
 * Get the test signing secret
 */
function getTestSecret() {
    return TEST_SECRET;
}
/**
 * Get a valid JWKS endpoint response for testing
 */
function getMockJWKS() {
    return {
        keys: [
            {
                alg: 'HS256',
                kty: 'oct',
                use: 'sig',
                kid: 'test-key-1',
                key: TEST_SECRET
            }
        ]
    };
}
/**
 * Service account JWT fixture
 */
function createServiceAccountJWT() {
    return createTestJWT({
        sub: 'sa-ci-cd',
        preferred_username: 'ci-cd-service',
        isServiceAccount: true,
        realm_access: {
            roles: ['service-account', 'ci-cd']
        }
    });
}
/**
 * Admin role JWT fixture
 */
function createAdminJWT() {
    return createTestJWT({
        sub: 'admin-user-456',
        preferred_username: 'admin',
        realm_access: {
            roles: ['admin', 'user']
        },
        resource_access: {
            'sentinel-gear-app': {
                roles: ['s3:*', 'admin:*']
            }
        }
    });
}
/**
 * Dev team JWT fixture
 */
function createDevJWT() {
    return createTestJWT({
        sub: 'dev-user-789',
        preferred_username: 'dev-alice',
        realm_access: {
            roles: ['dev', 'user']
        },
        resource_access: {
            'sentinel-gear-app': {
                roles: ['s3:read', 's3:write']
            }
        },
        groups: ['engineering']
    });
}
/**
 * JWT with no tenant (should use default)
 */
function createJWTWithoutTenant() {
    const token = createTestJWT();
    const payload = jsonwebtoken_1.default.decode(token);
    delete payload.tenant;
    return jsonwebtoken_1.default.sign(payload, TEST_SECRET, { algorithm: 'HS256' });
}
/**
 * JWT with future issued-at claim (clock skew test)
 */
function createFutureIssuedJWT() {
    const now = Math.floor(Date.now() / 1000);
    return createTestJWT({
        iat: now + 15 // 15 seconds in the future
    });
}
/**
 * JWT with missing required claims
 */
function createJWTMissingClaim(claimName) {
    const token = createTestJWT();
    const payload = jsonwebtoken_1.default.decode(token);
    delete payload[claimName];
    return jsonwebtoken_1.default.sign(payload, TEST_SECRET, { algorithm: 'HS256' });
}
//# sourceMappingURL=test-fixtures.js.map