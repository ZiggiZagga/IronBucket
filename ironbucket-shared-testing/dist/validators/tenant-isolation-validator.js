"use strict";
/**
 * Tenant Isolation Validation Implementation
 *
 * Enforces single-tenant and multi-tenant isolation boundaries
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.validateTenantIsolation = validateTenantIsolation;
exports.isValidTenantIdentifier = isValidTenantIdentifier;
exports.assertTenantAccess = assertTenantAccess;
exports.filterResourcesByTenant = filterResourcesByTenant;
exports.buildTenantScopedPath = buildTenantScopedPath;
/**
 * Default tenant validation pattern: alphanumeric, dashes, underscores
 */
const DEFAULT_TENANT_PATTERN = /^[a-zA-Z0-9_-]+$/;
/**
 * Validate and enforce tenant isolation
 */
function validateTenantIsolation(identity, requestTenant, config = { mode: 'multi-tenant' }) {
    // Single-tenant mode: always use configured tenant
    if (config.mode === 'single-tenant') {
        const tenant = config.singleTenantValue || 'default';
        return {
            valid: true,
            tenant
        };
    }
    // Multi-tenant mode: use identity's tenant
    let tenant = identity.tenant;
    // If request provides a different tenant, validate it
    if (requestTenant && requestTenant !== tenant) {
        if (!isValidTenantIdentifier(requestTenant, config)) {
            return {
                valid: false,
                error: `Invalid tenant format: ${requestTenant}`
            };
        }
        tenant = requestTenant;
    }
    // Validate tenant identifier format
    if (!isValidTenantIdentifier(tenant, config)) {
        return {
            valid: false,
            error: `Invalid tenant format: ${tenant}`
        };
    }
    return {
        valid: true,
        tenant
    };
}
/**
 * Check if tenant identifier matches allowed format
 */
function isValidTenantIdentifier(tenant, config = { mode: 'multi-tenant' }) {
    if (!tenant) {
        return false;
    }
    const pattern = config.allowedTenantPattern || DEFAULT_TENANT_PATTERN;
    return pattern.test(tenant);
}
/**
 * Validate that identity belongs to expected tenant
 */
function assertTenantAccess(identity, expectedTenant) {
    if (identity.tenant !== expectedTenant) {
        throw new Error(`Access denied: identity tenant ${identity.tenant} does not match expected tenant ${expectedTenant}`);
    }
    return true;
}
/**
 * Filter resources by tenant prefix
 * Useful for shared resource isolation
 */
function filterResourcesByTenant(resources, tenant, prefix = 'tenant-') {
    const tenantPrefix = `${prefix}${tenant}/`;
    return resources.filter((resource) => resource.startsWith(tenantPrefix));
}
/**
 * Build tenant-scoped resource path
 */
function buildTenantScopedPath(tenant, resourcePath, prefix = 'tenant-') {
    return `${prefix}${tenant}/${resourcePath}`;
}
//# sourceMappingURL=tenant-isolation-validator.js.map