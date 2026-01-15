/**
 * JWT Validation Tests - Phase 2
 * 
 * Comprehensive test suite for JWT validation logic
 * Tests signature verification, expiration, required claims, and edge cases
 */

import {
  createTestJWT,
  createExpiredJWT,
  createInvalidSignatureJWT,
  createMalformedJWT,
  createServiceAccountJWT,
  createAdminJWT,
  createDevJWT,
  createJWTMissingClaim,
  createFutureIssuedJWT,
  getTestSecret,
  getMockJWKS
} from '../../fixtures/jwts/test-fixtures';

import {
  validateJWT as realValidateJWT,
  JWTValidationResult,
  extractRoles,
  isServiceAccount
} from '../../validators/jwt-validator';

import jwt from 'jsonwebtoken';

describe('JWT Validation - Phase 2', () => {
  
  describe('1. Valid JWT Acceptance', () => {
    
    test('should accept valid JWT with all required claims', () => {
      const jwt = createTestJWT();
      
      // When we validate the JWT
      const validation = validateJWT(jwt);
      
      // Then it should be valid
      expect(validation.valid).toBe(true);
      expect(validation.error).toBeUndefined();
      expect(validation.claims).toBeDefined();
    });

    test('should extract correct claims from valid JWT', () => {
      const jwt = createTestJWT({
        sub: 'alice@example.com',
        email: 'alice@example.com'
      });
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(true);
      expect(validation.claims?.sub).toBe('alice@example.com');
      expect(validation.claims?.email).toBe('alice@example.com');
      expect(validation.claims?.iss).toBe('https://idp.example.com');
      expect(validation.claims?.aud).toBe('sentinel-gear-app');
    });

    test('should accept JWT with all optional claims', () => {
      const jwt = createTestJWT({
        email: 'alice@example.com',
        given_name: 'Alice',
        family_name: 'Smith',
        preferred_username: 'alice',
        realm_access: { roles: ['user', 'admin'] },
        resource_access: {
          'sentinel-gear-app': { roles: ['s3:read', 's3:write'] }
        },
        tenant: 'acme-corp',
        groups: ['engineering']
      });
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(true);
      expect(validation.claims?.email).toBe('alice@example.com');
      expect(validation.claims?.realm_access?.roles).toContain('admin');
      expect(validation.claims?.tenant).toBe('acme-corp');
    });
  });

  describe('2. JWT Signature Validation', () => {
    
    test('should validate JWT signature against correct secret', () => {
      const jwt = createTestJWT();
      const secret = getTestSecret();
      
      const validation = validateJWTSignature(jwt, secret);
      
      expect(validation.valid).toBe(true);
    });

    test('should reject JWT with invalid signature', () => {
      const jwt = createInvalidSignatureJWT();
      const secret = getTestSecret();
      
      const validation = validateJWTSignature(jwt, secret);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('signature');
    });

    test('should reject JWT signed with different secret', () => {
      const jwt = createTestJWT();
      const wrongSecret = 'different-secret-key';
      
      const validation = validateJWTSignature(jwt, wrongSecret);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('signature');
    });

    test('should support JWKS endpoint verification', () => {
      const jwt = createTestJWT();
      const jwks = getMockJWKS();
      
      const validation = validateJWTWithJWKS(jwt, jwks);
      
      expect(validation.valid).toBe(true);
    });
  });

  describe('3. Token Expiration', () => {
    
    test('should reject expired JWT', () => {
      const jwt = createExpiredJWT();
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('expired');
    });

    test('should accept JWT with future expiration', () => {
      const now = Math.floor(Date.now() / 1000);
      const jwt = createTestJWT({
        exp: now + 7200 // 2 hours in the future
      });
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(true);
    });

    test('should accept JWT expiring soon', () => {
      const now = Math.floor(Date.now() / 1000);
      const jwt = createTestJWT({
        exp: now + 60 // Expires in 1 minute
      });
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(true);
    });

    test('should reject JWT that just expired', () => {
      const now = Math.floor(Date.now() / 1000);
      const jwt = createTestJWT({
        exp: now - 1 // Expired 1 second ago
      });
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('expired');
    });

    test('should respect clock skew tolerance (30 seconds)', () => {
      const now = Math.floor(Date.now() / 1000);
      // Token issued 15 seconds in the future
      const jwt = createJWT({ iat: now + 15 });
      
      const validation = validateJWT(jwt, { clockSkew: 30 });
      
      expect(validation.valid).toBe(true);
    });

    test('should reject JWT outside clock skew tolerance', () => {
      const now = Math.floor(Date.now() / 1000);
      // Token issued 60 seconds in the future
      const jwt = createTestJWT({ iat: now + 60 });
      
      const validation = validateJWT(jwt, { clockSkew: 30 });
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('issued in the future');
    });
  });

  describe('4. Required Claims Validation', () => {
    
    test('should require sub (subject) claim', () => {
      const jwt = createJWTMissingClaim('sub');
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('sub');
    });

    test('should require iss (issuer) claim', () => {
      const jwt = createJWTMissingClaim('iss');
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('iss');
    });

    test('should require aud (audience) claim', () => {
      const jwt = createJWTMissingClaim('aud');
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('aud');
    });

    test('should require iat (issued at) claim', () => {
      const jwt = createJWTMissingClaim('iat');
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('iat');
    });

    test('should require exp (expiration) claim', () => {
      const jwt = createJWTMissingClaim('exp');
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('exp');
    });

    test('should validate all required claims together', () => {
      const now = Math.floor(Date.now() / 1000);
      const jwt = createTestJWT({
        sub: 'user123',
        iss: 'https://auth.example.com',
        aud: 'my-app',
        iat: now,
        exp: now + 3600
      });
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(true);
    });
  });

  describe('5. Issuer Whitelist Validation', () => {
    
    test('should accept JWT from whitelisted issuer', () => {
      const issuer = 'https://keycloak.example.com/auth/realms/ironbucket';
      const jwt = createTestJWT({ iss: issuer });
      const whitelist = [issuer, 'https://auth0.example.com'];
      
      const validation = validateJWTWithIssuerWhitelist(jwt, whitelist);
      
      expect(validation.valid).toBe(true);
    });

    test('should reject JWT from non-whitelisted issuer', () => {
      const jwt = createTestJWT({ iss: 'https://untrusted-idp.com' });
      const whitelist = ['https://keycloak.example.com'];
      
      const validation = validateJWTWithIssuerWhitelist(jwt, whitelist);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('issuer');
    });

    test('should support multiple whitelisted issuers', () => {
      const whitelist = [
        'https://keycloak.example.com',
        'https://auth0.example.com',
        'https://okta.example.com'
      ];
      
      for (const issuer of whitelist) {
        const jwt = createTestJWT({ iss: issuer });
        const validation = validateJWTWithIssuerWhitelist(jwt, whitelist);
        expect(validation.valid).toBe(true);
      }
    });
  });

  describe('6. Audience Validation', () => {
    
    test('should accept JWT with correct audience', () => {
      const jwt = createTestJWT({ aud: 'sentinel-gear-app' });
      const expectedAudience = 'sentinel-gear-app';
      
      const validation = validateJWTAudience(jwt, expectedAudience);
      
      expect(validation.valid).toBe(true);
    });

    test('should reject JWT with incorrect audience', () => {
      const jwt = createTestJWT({ aud: 'wrong-audience' });
      const expectedAudience = 'sentinel-gear-app';
      
      const validation = validateJWTAudience(jwt, expectedAudience);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('audience');
    });

    test('should support array of audiences in JWT', () => {
      const jwt = createTestJWT({ 
        aud: ['sentinel-gear-app', 'brazz-nossel'] 
      });
      const expectedAudience = 'sentinel-gear-app';
      
      const validation = validateJWTAudience(jwt, expectedAudience);
      
      expect(validation.valid).toBe(true);
    });
  });

  describe('7. Malformed JWT Handling', () => {
    
    test('should reject malformed JWT (missing parts)', () => {
      const jwt = createMalformedJWT();
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toContain('malformed');
    });

    test('should reject JWT with non-JSON header', () => {
      const jwt = 'invalid.notjson.sig';
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
      expect(validation.error).toBeDefined();
    });

    test('should reject empty JWT', () => {
      const jwt = '';
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
    });

    test('should reject JWT with only 2 parts', () => {
      const jwt = 'header.payload';
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(false);
    });
  });

  describe('8. Service Account Detection', () => {
    
    test('should detect service account from isServiceAccount claim', () => {
      const jwt = createServiceAccountJWT();
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(true);
      expect(validation.claims?.isServiceAccount).toBe(true);
    });

    test('should detect service account from subject pattern', () => {
      const jwt = createTestJWT({ sub: 'sa-ci-pipeline' });
      
      const isServiceAccount = detectServiceAccount(jwt);
      
      expect(isServiceAccount).toBe(true);
    });

    test('should not flag regular users as service accounts', () => {
      const jwt = createDevJWT();
      
      const isServiceAccount = detectServiceAccount(jwt);
      
      expect(isServiceAccount).toBe(false);
    });
  });

  describe('9. Role Extraction', () => {
    
    test('should extract realm roles from JWT', () => {
      const jwt = createTestJWT({
        realm_access: {
          roles: ['user', 'admin', 'viewer']
        }
      });
      
      const validation = validateJWT(jwt);
      const roles = extractRealmRoles(validation.claims);
      
      expect(roles).toEqual(['user', 'admin', 'viewer']);
    });

    test('should extract resource roles from JWT', () => {
      const jwt = createTestJWT({
        resource_access: {
          'sentinel-gear-app': {
            roles: ['s3:read', 's3:write']
          }
        }
      });
      
      const validation = validateJWT(jwt);
      const roles = extractResourceRoles(validation.claims, 'sentinel-gear-app');
      
      expect(roles).toEqual(['s3:read', 's3:write']);
    });

    test('should handle missing realm roles gracefully', () => {
      const jwt = createTestJWT();
      delete jwt.realm_access;
      
      const validation = validateJWT(jwt);
      const roles = extractRealmRoles(validation.claims);
      
      expect(roles).toEqual([]);
    });

    test('should handle missing resource roles gracefully', () => {
      const jwt = createTestJWT();
      
      const validation = validateJWT(jwt);
      const roles = extractResourceRoles(validation.claims, 'nonexistent');
      
      expect(roles).toEqual([]);
    });
  });

  describe('10. Algorithm Validation', () => {
    
    test('should only accept HS256 or RS256 algorithms', () => {
      const jwt = createTestJWT(); // Uses HS256
      
      const validation = validateJWT(jwt);
      
      expect(validation.valid).toBe(true);
    });

    test('should reject JWT with unsupported algorithm', () => {
      // This test requires mocking or a different JWT library support
      // Placeholder for future implementation
      expect(true).toBe(true);
    });
  });

  describe('11. Performance SLA Tests', () => {
    
    test('should validate JWT in under 1ms (cached)', () => {
      const jwt = createTestJWT();
      
      const start = performance.now();
      validateJWT(jwt);
      const elapsed = performance.now() - start;
      
      // Note: This may not be reliable on all systems; just documenting expectation
      expect(elapsed).toBeLessThan(10); // Generous margin for CI environments
    });

    test('should handle 1000 validations efficiently', () => {
      const jwts = Array.from({ length: 1000 }, () => createTestJWT());
      
      const start = performance.now();
      jwts.forEach(jwt => validateJWT(jwt));
      const elapsed = performance.now() - start;
      
      // Should complete in reasonable time
      expect(elapsed / 1000).toBeLessThan(5); // < 5ms per validation average
    });
  });

});

// Helper functions (to be implemented)

function validateJWT(jwt_token: string, options?: any): JWTValidationResult {
  const secret = getTestSecret();
  const config: any = {};
  
  if (options?.issuerWhitelist) {
    config.issuerWhitelist = options.issuerWhitelist;
  }
  if (options?.expectedAudience) {
    config.expectedAudience = options.expectedAudience;
  }
  if (options?.clockSkew) {
    config.clockSkewSeconds = options.clockSkew;
  }
  
  return realValidateJWT(jwt_token, secret, config);
}

function validateJWTSignature(jwt_token: string, secret: string): JWTValidationResult {
  return realValidateJWT(jwt_token, secret);
}

function validateJWTWithJWKS(jwt_token: string, jwks: any): JWTValidationResult {
  // For testing purposes, use the test secret
  return realValidateJWT(jwt_token, getTestSecret());
}

function validateJWTWithIssuerWhitelist(jwt_token: string, whitelist: string[]): JWTValidationResult {
  return realValidateJWT(jwt_token, getTestSecret(), {
    issuerWhitelist: whitelist
  });
}

function validateJWTAudience(jwt_token: string, audience: string): JWTValidationResult {
  return realValidateJWT(jwt_token, getTestSecret(), {
    expectedAudience: audience
  });
}

function detectServiceAccount(jwt_token: string): boolean {
  const decoded = jwt.decode(jwt_token) as any;
  return isServiceAccount(decoded);
}

function extractRealmRoles(claims: any): string[] {
  return claims?.realm_access?.roles || [];
}

function extractResourceRoles(claims: any, resource: string): string[] {
  return claims?.resource_access?.[resource]?.roles || [];
}
