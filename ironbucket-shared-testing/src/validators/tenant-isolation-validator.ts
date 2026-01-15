/**
 * Tenant Isolation Validation Implementation
 * 
 * Enforces single-tenant and multi-tenant isolation boundaries
 */

import { NormalizedIdentity } from '../types/identity';

export interface TenantIsolationConfig {
  mode: 'single-tenant' | 'multi-tenant';
  singleTenantValue?: string;
  allowedTenantPattern?: RegExp;
}

export interface TenantIsolationResult {
  valid: boolean;
  tenant?: string;
  error?: string;
}

/**
 * Default tenant validation pattern: alphanumeric, dashes, underscores
 */
const DEFAULT_TENANT_PATTERN = /^[a-zA-Z0-9_-]+$/;

/**
 * Validate and enforce tenant isolation
 */
export function validateTenantIsolation(
  identity: NormalizedIdentity,
  requestTenant?: string,
  config: TenantIsolationConfig = { mode: 'multi-tenant' }
): TenantIsolationResult {
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
export function isValidTenantIdentifier(
  tenant: string,
  config: TenantIsolationConfig = { mode: 'multi-tenant' }
): boolean {
  if (!tenant) {
    return false;
  }

  const pattern = config.allowedTenantPattern || DEFAULT_TENANT_PATTERN;
  return pattern.test(tenant);
}

/**
 * Validate that identity belongs to expected tenant
 */
export function assertTenantAccess(
  identity: NormalizedIdentity,
  expectedTenant: string
): boolean {
  if (identity.tenant !== expectedTenant) {
    throw new Error(
      `Access denied: identity tenant ${identity.tenant} does not match expected tenant ${expectedTenant}`
    );
  }
  return true;
}

/**
 * Filter resources by tenant prefix
 * Useful for shared resource isolation
 */
export function filterResourcesByTenant(
  resources: string[],
  tenant: string,
  prefix: string = 'tenant-'
): string[] {
  const tenantPrefix = `${prefix}${tenant}/`;
  return resources.filter((resource) => resource.startsWith(tenantPrefix));
}

/**
 * Build tenant-scoped resource path
 */
export function buildTenantScopedPath(
  tenant: string,
  resourcePath: string,
  prefix: string = 'tenant-'
): string {
  return `${prefix}${tenant}/${resourcePath}`;
}
