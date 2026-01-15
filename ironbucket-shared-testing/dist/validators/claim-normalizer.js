"use strict";
/**
 * Claim Normalization Implementation
 *
 * Transforms raw JWT claims into a standardized NormalizedIdentity format
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.normalizeClaims = normalizeClaims;
exports.resolveUsername = resolveUsername;
exports.resolveTenant = resolveTenant;
exports.validateNormalizedIdentity = validateNormalizedIdentity;
/**
 * Normalize JWT claims into standardized NormalizedIdentity
 */
function normalizeClaims(claims, enrichmentContext, config = {}) {
    const now = Math.floor(Date.now() / 1000);
    // Resolve username with fallback chain
    const username = resolveUsername(claims);
    // Resolve full name
    const firstName = claims.given_name || '';
    const lastName = claims.family_name || '';
    const fullName = [firstName, lastName].filter(Boolean).join(' ') || username;
    // Extract roles
    const realmRoles = claims?.realm_access?.roles || [];
    const resourceRoles = new Map();
    const allRoles = [...realmRoles];
    if (claims?.resource_access) {
        for (const [resource, access] of Object.entries(claims.resource_access)) {
            const roles = access?.roles || [];
            if (roles.length > 0) {
                resourceRoles.set(resource, roles);
                allRoles.push(...roles);
            }
        }
    }
    // Extract tenant
    const tenant = resolveTenant(claims, config);
    // Detect service account
    const isServiceAccount = (claims?.sub || '').startsWith('sa-') || claims?.isServiceAccount === true;
    // Resolve user groups
    const groups = claims?.groups || [];
    // Build normalized identity
    const normalized = {
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
function resolveUsername(claims) {
    // Fallback chain: preferred_username → email → sub
    return claims?.preferred_username || claims?.email || claims?.sub || 'unknown';
}
/**
 * Resolve tenant from claims or configuration
 */
function resolveTenant(claims, config = {}) {
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
function validateNormalizedIdentity(identity) {
    const errors = [];
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
//# sourceMappingURL=claim-normalizer.js.map