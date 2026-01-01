/**
 * JWT Test Fixture Utilities
 * Provides helper functions to create and sign test JWTs
 */

import jwt from 'jsonwebtoken';

/**
 * Default test signing key
 */
const TEST_SECRET = 'test-signing-key-for-unit-tests';

/**
 * Generate a valid JWT with custom claims
 */
export function createTestJWT(overrides: any = {}): string {
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

  return jwt.sign(payload, TEST_SECRET, { algorithm: 'HS256' });
}

/**
 * Create a JWT with expired token
 */
export function createExpiredJWT(): string {
  const now = Math.floor(Date.now() / 1000);
  return createTestJWT({
    exp: now - 3600 // Expired 1 hour ago
  });
}

/**
 * Create a JWT with invalid signature
 */
export function createInvalidSignatureJWT(): string {
  const token = createTestJWT();
  // Corrupt the signature
  const parts = token.split('.');
  parts[2] = 'invalidsignature';
  return parts.join('.');
}

/**
 * Create a malformed JWT (missing parts)
 */
export function createMalformedJWT(): string {
  return 'invalid.jwt';
}

/**
 * Get the test signing secret
 */
export function getTestSecret(): string {
  return TEST_SECRET;
}

/**
 * Get a valid JWKS endpoint response for testing
 */
export function getMockJWKS() {
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
export function createServiceAccountJWT(): string {
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
export function createAdminJWT(): string {
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
export function createDevJWT(): string {
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
export function createJWTWithoutTenant(): string {
  const token = createTestJWT();
  const payload = jwt.decode(token) as any;
  delete payload.tenant;
  return jwt.sign(payload, TEST_SECRET, { algorithm: 'HS256' });
}

/**
 * JWT with future issued-at claim (clock skew test)
 */
export function createFutureIssuedJWT(): string {
  const now = Math.floor(Date.now() / 1000);
  return createTestJWT({
    iat: now + 15 // 15 seconds in the future
  });
}

/**
 * JWT with missing required claims
 */
export function createJWTMissingClaim(claimName: string): string {
  const token = createTestJWT();
  const payload = jwt.decode(token) as any;
  delete payload[claimName];
  return jwt.sign(payload, TEST_SECRET, { algorithm: 'HS256' });
}
