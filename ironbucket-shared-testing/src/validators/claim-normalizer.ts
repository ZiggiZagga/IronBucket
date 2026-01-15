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
export function normalizeClaims(
  claims: Record<string, any>,
  enrichmentContext?: {
    ipAddress?: string;
    userAgent?: string;
    requestId?: string;
  },
  config: ClaimNormalizationConfig = {}
): NormalizedIdentity {
  const now = Math.floor(Date.now() / 1000);

  // Resolve username with fallback chain
  const username = resolveUsername(claims);

  // Resolve full name
  const firstName = claims.given_name || '';
  const lastName = claims.family_name || '';
  const fullName = [firstName, lastName].filter(Boolean).join(' ') || username;

  // Extract roles
  const realmRoles = claims?.realm_access?.roles || [];
  const resourceRoles = new Map<string, string[]>();
  const allRoles: string[] = [...realmRoles];

  if (claims?.resource_access) {
    for (const [resource, access] of Object.entries(claims.resource_access)) {
      const roles = (access as any)?.roles || [];
      if (roles.length > 0) {
        resourceRoles.set(resource, roles);
        allRoles.push(...roles);
      }
    }
  }

  // Extract tenant
  const tenant = resolveTenant(claims, config);

  // Detect service account
  const isServiceAccount =
    (claims?.sub || '').startsWith('sa-') || claims?.isServiceAccount === true;

  // Resolve user groups
  const groups = claims?.groups || [];

  // Build normalized identity
  const normalized: NormalizedIdentity = {
    userId: claims.sub,
    username,
    issuer: claims.iss,
    issuedAt: claims.iat,
    expiresAt: claims.exp,
    roles: allRoles,
    realmRoles,
    resourceRoles,
    tenant,
    region: claims.region,
    groups,
    email: claims.email,
    firstName: firstName || undefined,
    lastName: lastName || undefined,
    fullName,
    ipAddress: enrichmentContext?.ipAddress,
    userAgent: enrichmentContext?.userAgent,
    requestId: enrichmentContext?.requestId,
    isServiceAccount,
    rawClaims: claims,
    createdAt: now
  };

  return normalized;
}

/**
 * Resolve username from JWT claims with fallback chain
 */
export function resolveUsername(claims: Record<string, any>): string {
  // Fallback chain: preferred_username → email → sub
  return claims?.preferred_username || claims?.email || claims?.sub || 'unknown';
}

/**
 * Resolve tenant from claims or configuration
 */
export function resolveTenant(
  claims: Record<string, any>,
  config: ClaimNormalizationConfig = {}
): string {
  // If multi-tenant mode is disabled, use configured default
  if (config.multiTenantMode === false) {
    return config.defaultTenant || 'default';
  }

  // Use tenant from claims if available
  if (claims?.tenant) {
    return claims.tenant;
  }

  // Fall back to configured default
  return config.defaultTenant || 'default';
}

/**
 * Validate that normalized identity has all required fields
 */
export function validateNormalizedIdentity(
  identity: NormalizedIdentity
): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!identity.userId) {
    errors.push('Missing userId (sub claim)');
  }
  if (!identity.username) {
    errors.push('Missing username');
  }
  if (!identity.issuer) {
    errors.push('Missing issuer (iss claim)');
  }
  if (!identity.tenant) {
    errors.push('Missing tenant');
  }
  if (typeof identity.issuedAt !== 'number') {
    errors.push('Missing or invalid issuedAt (iat claim)');
  }
  if (typeof identity.expiresAt !== 'number') {
    errors.push('Missing or invalid expiresAt (exp claim)');
  }

  return {
    valid: errors.length === 0,
    errors
  };
}
