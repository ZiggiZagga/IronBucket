/**
 * JWT Validation Implementation
 * 
 * Provides JWT signature verification, expiration checking, claim validation,
 * issuer whitelisting, and audience matching.
 */

import jwt from 'jsonwebtoken';

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
 * Default configuration for JWT validation
 */
const DEFAULT_CONFIG: JWTValidationConfig = {
  clockSkewSeconds: 30,
  requiredClaims: ['sub', 'iss', 'aud', 'iat', 'exp']
};

/**
 * Test secret for unit tests
 */
const TEST_SECRET = 'test-signing-key-for-unit-tests';

/**
 * Validate JWT signature, expiration, required claims, issuer, and audience
 */
export function validateJWT(
  token: string,
  secret: string = TEST_SECRET,
  config: JWTValidationConfig = DEFAULT_CONFIG
): JWTValidationResult {
  // Validate input
  if (!token) {
    return {
      valid: false,
      error: 'Token is empty or null'
    };
  }

  // Check JWT structure
  const parts = token.split('.');
  if (parts.length !== 3) {
    return {
      valid: false,
      error: 'JWT is malformed'
    };
  }

  try {
    // Verify signature and decode
    const decoded = jwt.verify(token, secret, {
      clockTimestamp: Math.floor(Date.now() / 1000),
      clockTolerance: config.clockSkewSeconds || 30
    }) as Record<string, any>;

    // Check required claims
    const missingClaims = (config.requiredClaims || DEFAULT_CONFIG.requiredClaims)?.filter(
      (claim) => !(claim in decoded)
    );

    if (missingClaims && missingClaims.length > 0) {
      return {
        valid: false,
        error: `Missing required claims: ${missingClaims.join(', ')}`
      };
    }

    // Check issuer whitelist
    if (config.issuerWhitelist && config.issuerWhitelist.length > 0) {
      if (!config.issuerWhitelist.includes(decoded.iss)) {
        return {
          valid: false,
          error: `Invalid issuer: ${decoded.iss}`
        };
      }
    }

    // Check audience
    if (config.expectedAudience) {
      const expectedAudiences = Array.isArray(config.expectedAudience)
        ? config.expectedAudience
        : [config.expectedAudience];

      const tokenAudiences = Array.isArray(decoded.aud)
        ? decoded.aud
        : [decoded.aud];

      const isValidAudience = tokenAudiences.some((aud: string) =>
        expectedAudiences.includes(aud)
      );

      if (!isValidAudience) {
        return {
          valid: false,
          error: `Invalid audience: ${decoded.aud}`
        };
      }
    }

    // Check if issued in the future
    const now = Math.floor(Date.now() / 1000);
    const clockSkew = config.clockSkewSeconds || 30;
    if (decoded.iat > now + clockSkew) {
      return {
        valid: false,
        error: 'Token was issued in the future'
      };
    }

    // All validations passed
    return {
      valid: true,
      claims: decoded
    };
  } catch (err: any) {
    if (err.name === 'TokenExpiredError') {
      return {
        valid: false,
        error: `Token expired at ${new Date(err.expiredAt).toISOString()}`
      };
    }

    if (err.name === 'JsonWebTokenError') {
      if (err.message.includes('signature')) {
        return {
          valid: false,
          error: 'Invalid signature'
        };
      }
      return {
        valid: false,
        error: `JWT validation error: ${err.message}`
      };
    }

    return {
      valid: false,
      error: err.message || 'Unknown validation error'
    };
  }
}

/**
 * Extract roles from JWT claims
 * Supports both realm roles and resource-specific roles
 */
export function extractRoles(claims: Record<string, any>): {
  realmRoles: string[];
  resourceRoles: Map<string, string[]>;
  allRoles: string[];
} {
  const realmRoles = claims?.realm_access?.roles || [];
  const resourceRoles = new Map<string, string[]>();
  const allRoles = [...realmRoles];

  // Extract resource-specific roles
  if (claims?.resource_access) {
    for (const [resource, access] of Object.entries(claims.resource_access)) {
      const roles = (access as any)?.roles || [];
      if (roles.length > 0) {
        resourceRoles.set(resource, roles);
        allRoles.push(...roles);
      }
    }
  }

  return {
    realmRoles,
    resourceRoles,
    allRoles
  };
}

/**
 * Detect if JWT is a service account based on subject prefix
 */
export function isServiceAccount(claims: Record<string, any>): boolean {
  const subject = claims?.sub || '';
  // Service accounts are detected by 'sa-' prefix or 'isServiceAccount' flag
  return subject.startsWith('sa-') || claims?.isServiceAccount === true;
}
