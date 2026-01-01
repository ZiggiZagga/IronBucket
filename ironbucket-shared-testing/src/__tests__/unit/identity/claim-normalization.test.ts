/**
 * Claim Normalization Tests - Phase 2
 * 
 * Tests conversion of raw JWT claims into normalized identity object
 */

import {
  createTestJWT,
  createServiceAccountJWT,
  createAdminJWT,
  createDevJWT,
  createJWTWithoutTenant
} from '../../fixtures/jwts/test-fixtures';
import { NormalizedIdentity } from '../../types/identity';

describe('Claim Normalization - Phase 2', () => {
  
  describe('1. Basic Normalization', () => {
    
    test('should normalize standard JWT claims to NormalizedIdentity', () => {
      const jwt = createTestJWT({
        sub: 'alice@example.com',
        iss: 'https://keycloak.example.com',
        aud: 'sentinel-gear-app',
        email: 'alice@example.com',
        preferred_username: 'alice',
        given_name: 'Alice',
        family_name: 'Smith'
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.userId).toBe('alice@example.com');
      expect(normalized.username).toBe('alice');
      expect(normalized.issuer).toBe('https://keycloak.example.com');
      expect(normalized.email).toBe('alice@example.com');
      expect(normalized.firstName).toBe('Alice');
      expect(normalized.lastName).toBe('Smith');
      expect(normalized.fullName).toBe('Alice Smith');
    });

    test('should extract iat and exp timestamps correctly', () => {
      const now = Math.floor(Date.now() / 1000);
      const jwt = createTestJWT({
        iat: now,
        exp: now + 3600
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.issuedAt).toBe(now);
      expect(normalized.expiresAt).toBe(now + 3600);
    });

    test('should set createdAt timestamp', () => {
      const jwt = createTestJWT();
      const beforeNormalization = Math.floor(Date.now() / 1000);
      
      const normalized = normalizeJWTClaims(jwt);
      
      const afterNormalization = Math.floor(Date.now() / 1000);
      expect(normalized.createdAt).toBeGreaterThanOrEqual(beforeNormalization * 1000);
      expect(normalized.createdAt).toBeLessThanOrEqual(afterNormalization * 1000);
    });
  });

  describe('2. Role Normalization', () => {
    
    test('should normalize realm roles', () => {
      const jwt = createTestJWT({
        realm_access: {
          roles: ['user', 'admin', 'viewer']
        }
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.realmRoles).toEqual(['user', 'admin', 'viewer']);
    });

    test('should normalize resource roles', () => {
      const jwt = createTestJWT({
        resource_access: {
          'sentinel-gear-app': {
            roles: ['s3:read', 's3:write']
          },
          'policy-engine': {
            roles: ['policy:read', 'policy:evaluate']
          }
        }
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.resourceRoles.get('sentinel-gear-app')).toEqual(['s3:read', 's3:write']);
      expect(normalized.resourceRoles.get('policy-engine')).toEqual(['policy:read', 'policy:evaluate']);
    });

    test('should combine realm and resource roles into flat roles array', () => {
      const jwt = createTestJWT({
        realm_access: {
          roles: ['user', 'admin']
        },
        resource_access: {
          'sentinel-gear-app': {
            roles: ['s3:read', 's3:write']
          }
        }
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      // Should contain all roles flattened
      expect(normalized.roles).toContain('user');
      expect(normalized.roles).toContain('admin');
      expect(normalized.roles).toContain('s3:read');
      expect(normalized.roles).toContain('s3:write');
    });

    test('should handle empty role lists gracefully', () => {
      const jwt = createTestJWT({
        realm_access: undefined,
        resource_access: undefined
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.realmRoles).toEqual([]);
      expect(normalized.roles).toEqual([]);
    });
  });

  describe('3. Tenant Isolation', () => {
    
    test('should extract tenant from JWT claim', () => {
      const jwt = createTestJWT({ tenant: 'acme-corp' });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.tenant).toBe('acme-corp');
    });

    test('should use default tenant if not present', () => {
      const jwt = createJWTWithoutTenant();
      
      const normalized = normalizeJWTClaims(jwt, { defaultTenant: 'default' });
      
      expect(normalized.tenant).toBe('default');
    });

    test('should require tenant validation', () => {
      const jwt = createTestJWT({ tenant: '../../etc/passwd' }); // Injection attempt
      
      const normalized = normalizeJWTClaims(jwt, { validateTenant: true });
      
      // Should be sanitized or rejected
      expect(normalized.tenant).toMatch(/^[a-zA-Z0-9-]+$/);
    });

    test('should support multi-tenant deployments', () => {
      const jwtTenant1 = createTestJWT({ tenant: 'customer-a' });
      const jwtTenant2 = createTestJWT({ tenant: 'customer-b' });
      
      const normalized1 = normalizeJWTClaims(jwtTenant1);
      const normalized2 = normalizeJWTClaims(jwtTenant2);
      
      expect(normalized1.tenant).toBe('customer-a');
      expect(normalized2.tenant).toBe('customer-b');
      expect(normalized1.tenant).not.toBe(normalized2.tenant);
    });
  });

  describe('4. Organizational Context', () => {
    
    test('should extract groups from JWT', () => {
      const jwt = createTestJWT({
        groups: ['engineering', 'platform', 'security']
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.groups).toEqual(['engineering', 'platform', 'security']);
    });

    test('should extract region if present', () => {
      const jwt = createTestJWT({ region: 'eu-central-1' });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.region).toBe('eu-central-1');
    });

    test('should handle multiple organizational attributes', () => {
      const jwt = createTestJWT({
        groups: ['eng', 'platform'],
        region: 'us-west-2',
        department: 'infrastructure',
        cost_center: 'CC-123'
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.groups).toContain('eng');
      expect(normalized.region).toBe('us-west-2');
      expect(normalized.rawClaims.department).toBe('infrastructure');
    });
  });

  describe('5. Service Account Detection', () => {
    
    test('should detect service account from claim', () => {
      const jwt = createServiceAccountJWT();
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.isServiceAccount).toBe(true);
    });

    test('should detect service account from subject prefix', () => {
      const jwt = createTestJWT({
        sub: 'sa-ci-pipeline',
        isServiceAccount: true
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.isServiceAccount).toBe(true);
    });

    test('should not mark regular users as service accounts', () => {
      const jwt = createDevJWT();
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.isServiceAccount).toBe(false);
    });

    test('should enforce stricter role constraints for service accounts', () => {
      const jwt = createServiceAccountJWT();
      
      const normalized = normalizeJWTClaims(jwt, { enforceServiceAccountConstraints: true });
      
      // Service accounts should not have interactive user roles
      expect(normalized.roles.some(r => r.includes('s3:*'))).toBe(false);
    });
  });

  describe('6. Username Resolution', () => {
    
    test('should use preferred_username if available', () => {
      const jwt = createTestJWT({
        preferred_username: 'alice_smith',
        email: 'alice@example.com'
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.username).toBe('alice_smith');
    });

    test('should fallback to email if preferred_username missing', () => {
      const jwt = createTestJWT({
        email: 'alice@example.com'
      });
      delete jwt.preferred_username;
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.username).toBe('alice@example.com');
    });

    test('should fallback to sub if both are missing', () => {
      const jwt = createTestJWT({
        sub: 'user-id-123'
      });
      delete jwt.preferred_username;
      delete jwt.email;
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.username).toBe('user-id-123');
    });
  });

  describe('7. Enrichment Context', () => {
    
    test('should preserve enrichment context if provided', () => {
      const jwt = createTestJWT();
      
      const enrichmentContext = {
        ipAddress: '192.168.1.100',
        userAgent: 'curl/7.68.0',
        requestId: 'req-12345'
      };
      
      const normalized = normalizeJWTClaims(jwt, enrichmentContext);
      
      expect(normalized.ipAddress).toBe('192.168.1.100');
      expect(normalized.userAgent).toBe('curl/7.68.0');
      expect(normalized.requestId).toBe('req-12345');
    });

    test('should auto-generate requestId if not provided', () => {
      const jwt = createTestJWT();
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.requestId).toBeDefined();
      expect(normalized.requestId).toMatch(/^[a-f0-9-]+$/);
    });

    test('should generate unique requestIds', () => {
      const jwt1 = createTestJWT();
      const jwt2 = createTestJWT();
      
      const normalized1 = normalizeJWTClaims(jwt1);
      const normalized2 = normalizeJWTClaims(jwt2);
      
      expect(normalized1.requestId).not.toBe(normalized2.requestId);
    });
  });

  describe('8. Raw Claims Preservation', () => {
    
    test('should preserve raw JWT claims for policy evaluation', () => {
      const jwt = createTestJWT({
        custom_claim: 'custom_value',
        department: 'engineering'
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.rawClaims.custom_claim).toBe('custom_value');
      expect(normalized.rawClaims.department).toBe('engineering');
    });

    test('should preserve sensitive claims securely', () => {
      const jwt = createTestJWT({
        sub: 'alice@example.com'
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.rawClaims.sub).toBe('alice@example.com');
      // Should not expose JWT signature
      expect(normalized.rawClaims.__signature).toBeUndefined();
    });
  });

  describe('9. Name Handling', () => {
    
    test('should build fullName from given_name and family_name', () => {
      const jwt = createTestJWT({
        given_name: 'John',
        family_name: 'Doe'
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.fullName).toBe('John Doe');
    });

    test('should use given_name alone if family_name missing', () => {
      const jwt = createTestJWT({
        given_name: 'John'
      });
      delete jwt.family_name;
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.fullName).toBe('John');
    });

    test('should handle name with special characters', () => {
      const jwt = createTestJWT({
        given_name: 'José',
        family_name: "O'Brien"
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.fullName).toContain('José');
      expect(normalized.fullName).toContain("O'Brien");
    });
  });

  describe('10. Validation', () => {
    
    test('should validate normalized identity completeness', () => {
      const jwt = createTestJWT();
      
      const normalized = normalizeJWTClaims(jwt);
      
      // All required fields should be present
      expect(normalized.userId).toBeDefined();
      expect(normalized.username).toBeDefined();
      expect(normalized.issuer).toBeDefined();
      expect(normalized.issuedAt).toBeDefined();
      expect(normalized.expiresAt).toBeDefined();
      expect(Array.isArray(normalized.roles)).toBe(true);
      expect(normalized.tenant).toBeDefined();
      expect(Array.isArray(normalized.groups)).toBe(true);
    });

    test('should reject invalid tenant values', () => {
      const jwt = createTestJWT({ tenant: 'invalid@tenant!' });
      
      expect(() => {
        normalizeJWTClaims(jwt, { validateTenant: true });
      }).toThrow();
    });

    test('should handle timezone-aware timestamps', () => {
      const now = Math.floor(Date.now() / 1000);
      const jwt = createTestJWT({
        iat: now,
        exp: now + 86400 // 1 day
      });
      
      const normalized = normalizeJWTClaims(jwt);
      
      expect(normalized.issuedAt).toBe(now);
      expect(normalized.expiresAt).toBe(now + 86400);
    });
  });

});

// Helper function (to be implemented)
function normalizeJWTClaims(jwt: string, options?: any): NormalizedIdentity {
  // Implementation placeholder
  return {} as NormalizedIdentity;
}
