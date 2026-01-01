# ironbucket-shared-testing

Shared test utilities, fixtures, and test suites for IronBucket Phase 2 and beyond.

## Overview

This module provides:
- ✅ Comprehensive test fixtures for JWT generation and manipulation
- ✅ Reusable TypeScript type definitions
- ✅ 135+ unit tests covering identity, policy, and multi-tenant scenarios
- ✅ Performance benchmark framework
- ✅ Test-first approach (tests written before implementation)

## Quick Start

```bash
# Install dependencies
npm install

# Compile TypeScript
npm run build

# Run all tests
npm test

# Watch mode for development
npm run test:watch

# Generate coverage report
npm run test:coverage
```

## Test Suite Structure

```
src/__tests__/unit/identity/
├── jwt-validation.test.ts (59 tests)
│   └── JWT signature, expiration, claims validation
├── claim-normalization.test.ts (40 tests)
│   └── JWT → NormalizedIdentity transformation
└── tenant-isolation.test.ts (36 tests)
    └── Single/multi-tenant isolation enforcement
```

## Fixtures

### JWT Generation

```typescript
import {
  createTestJWT,
  createAdminJWT,
  createDevJWT,
  createServiceAccountJWT,
  createExpiredJWT,
  createInvalidSignatureJWT,
  createMalformedJWT
} from './fixtures/jwts/test-fixtures';

// Basic JWT with custom claims
const jwt = createTestJWT({
  sub: 'user@example.com',
  tenant: 'customer-a',
  realm_access: { roles: ['admin'] }
});

// Pre-built user types
const adminToken = createAdminJWT();
const devToken = createDevJWT();
const saToken = createServiceAccountJWT();

// Edge cases
const expiredToken = createExpiredJWT();
const tamperedToken = createInvalidSignatureJWT();
const malformedToken = createMalformedJWT();
```

## Type System

### NormalizedIdentity

Complete representation of a user identity after JWT validation and claim normalization:

```typescript
interface NormalizedIdentity {
  // Core identity
  userId: string;                      // sub claim
  username: string;                    // preferred_username or email
  issuer: string;                      // iss claim
  issuedAt: number;                    // iat (Unix timestamp)
  expiresAt: number;                   // exp (Unix timestamp)
  
  // Roles & Permissions
  roles: string[];                     // All roles combined
  realmRoles: string[];                // Keycloak realm roles
  resourceRoles: Map<string, string[]>;// Per-resource roles
  
  // Organization
  tenant: string;                      // Tenant identifier
  region?: string;                     // Geographic region
  groups: string[];                    // Organizational groups
  
  // User metadata
  email?: string;
  firstName?: string;
  lastName?: string;
  fullName?: string;
  
  // Enrichment
  ipAddress?: string;                  // Source IP
  userAgent?: string;                  // Source User-Agent
  requestId?: string;                  // Unique request ID
  
  // Flags & Raw Data
  isServiceAccount: boolean;
  rawClaims: Record<string, any>;
  createdAt: number;
}
```

## Test Coverage

### JWT Validation (59 tests)
- ✅ Valid JWT acceptance
- ✅ Signature verification (HS256, RS256, JWKS)
- ✅ Expiration and clock skew tolerance
- ✅ Required claims validation
- ✅ Issuer whitelist enforcement
- ✅ Audience validation
- ✅ Malformed JWT handling
- ✅ Service account detection
- ✅ Role extraction
- ✅ Performance benchmarks

### Claim Normalization (40 tests)
- ✅ JWT claims extraction
- ✅ Role normalization (realm + resource)
- ✅ Tenant handling
- ✅ Organizational context
- ✅ Service account flagging
- ✅ Username resolution chains
- ✅ Name composition
- ✅ Enrichment context
- ✅ Raw claims preservation
- ✅ Validation completeness

### Tenant Isolation (36 tests)
- ✅ Single vs. multi-tenant modes
- ✅ Tenant identifier validation
- ✅ Cross-tenant access prevention
- ✅ Policy filtering by tenant
- ✅ Audit log segregation
- ✅ Per-tenant caching
- ✅ Header-based tenant override
- ✅ Tenant-aware authorization
- ✅ Tenant migration
- ✅ Cache isolation and limits

## Performance Targets

All tests verify:
- JWT validation (cached): < 1ms
- Claim normalization: < 5ms
- 1000 JWT validations: < 5ms average
- Per-tenant cache efficiency
- Zero performance regressions

## Security Properties

Tests validate:
- ✅ JWT signature prevents forgery
- ✅ Expiration prevents replay attacks
- ✅ Issuer whitelist prevents spoofing
- ✅ Tenant isolation prevents data leakage
- ✅ Service account constraints prevent privilege escalation
- ✅ Audit logs segregated by tenant

## Usage in Implementation

### For Phase 3 Developers

When implementing a feature:

1. **Review the tests** for exact requirements
   ```bash
   cat src/__tests__/unit/identity/jwt-validation.test.ts
   ```

2. **Create implementation** in target service
   ```
   Sentinel-Gear/src/main/java/.../identity/JWTValidator.java
   ```

3. **Use fixtures** for validation
   ```java
   import com.ironbucket.testing.fixtures.JWTFixtures;
   
   String token = JWTFixtures.createValidJWT();
   JWTValidationResult result = validator.validate(token);
   ```

4. **Run tests** to verify correctness
   ```bash
   cd ironbucket-shared-testing && npm test
   ```

## Dependencies

- `jsonwebtoken@^9.0.2` - JWT generation and validation
- `yaml@^2.3.4` - YAML policy parsing
- `jest@^29.7.0` - Testing framework
- `ts-jest@^29.1.1` - TypeScript support for Jest
- `typescript@^5.9.2` - TypeScript compiler

## Configuration

### Jest Configuration
Defined in `jest` section of `package.json`:
- Uses ts-jest preset
- Node test environment
- Includes `src/**/*` in tests
- Excludes implementation code (not in repo)

### TypeScript Configuration
Defined in `tsconfig.json`:
- Target: ES2024
- Strict type checking enabled
- Module: CommonJS
- Source maps and declarations included

## Contributing

When adding new tests:

1. Create test file in appropriate subdirectory
2. Use fixtures from `src/fixtures/`
3. Import types from `src/types/`
4. Follow naming convention: `*.test.ts`
5. Ensure all tests pass: `npm test`
6. Update test counts in documentation

## Test Results

Run `npm test` to see:
- Number of test suites
- Number of tests
- Pass/fail status
- Execution time
- Code coverage (with `--coverage`)

Example:
```
PASS src/__tests__/unit/identity/jwt-validation.test.ts
  JWT Validation - Phase 2
    ✓ accepts valid JWT with all required claims
    ✓ validates JWT signature against JWKS
    ✓ rejects expired JWT
    ...

Tests: 59 passed, 59 total
Time: 2.134s
```

## Next Steps

After tests pass:
- Begin Phase 3 implementation
- Create implementation modules in Sentinel-Gear
- Run npm test alongside mvn test
- Track code coverage
- Optimize for performance

## Documentation

- `PHASE-2-TEST-FIRST.md` - Detailed test documentation
- `PHASE-3-IMPLEMENTATION.md` - Implementation roadmap
- `PHASE-2-3-QUICK-REFERENCE.md` - Quick reference
- Phase 1 contracts in `/docs/`

## License

Same as IronBucket main project

## Authors

IronBucket Development Team

---

**Status:** Phase 2 Complete - Tests Ready for Phase 3 Implementation
**Last Updated:** 2025-12-26
