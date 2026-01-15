"use strict";
/**
 * JWT Validation Implementation
 *
 * Provides JWT signature verification, expiration checking, claim validation,
 * issuer whitelisting, and audience matching.
 */
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.validateJWT = validateJWT;
exports.extractRoles = extractRoles;
exports.isServiceAccount = isServiceAccount;
const jsonwebtoken_1 = __importDefault(require("jsonwebtoken"));
/**
 * Default configuration for JWT validation
 */
const DEFAULT_CONFIG = {
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
function validateJWT(token, secret = TEST_SECRET, config = DEFAULT_CONFIG) {
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
        const decoded = jsonwebtoken_1.default.verify(token, secret, {
            clockTimestamp: Math.floor(Date.now() / 1000),
            clockTolerance: config.clockSkewSeconds || 30
        });
        // Check required claims
        const missingClaims = (config.requiredClaims || DEFAULT_CONFIG.requiredClaims)?.filter((claim) => !(claim in decoded));
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
            const isValidAudience = tokenAudiences.some((aud) => expectedAudiences.includes(aud));
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
    }
    catch (err) {
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
function extractRoles(claims) {
    const realmRoles = claims?.realm_access?.roles || [];
    const resourceRoles = new Map();
    const allRoles = [...realmRoles];
    // Extract resource-specific roles
    if (claims?.resource_access) {
        for (const [resource, access] of Object.entries(claims.resource_access)) {
            const roles = access?.roles || [];
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
function isServiceAccount(claims) {
    const subject = claims?.sub || '';
    // Service accounts are detected by 'sa-' prefix or 'isServiceAccount' flag
    return subject.startsWith('sa-') || claims?.isServiceAccount === true;
}
//# sourceMappingURL=jwt-validator.js.map