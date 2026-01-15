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
 * Validate and enforce tenant isolation
 */
export declare function validateTenantIsolation(identity: NormalizedIdentity, requestTenant?: string, config?: TenantIsolationConfig): TenantIsolationResult;
/**
 * Check if tenant identifier matches allowed format
 */
export declare function isValidTenantIdentifier(tenant: string, config?: TenantIsolationConfig): boolean;
/**
 * Validate that identity belongs to expected tenant
 */
export declare function assertTenantAccess(identity: NormalizedIdentity, expectedTenant: string): boolean;
/**
 * Filter resources by tenant prefix
 * Useful for shared resource isolation
 */
export declare function filterResourcesByTenant(resources: string[], tenant: string, prefix?: string): string[];
/**
 * Build tenant-scoped resource path
 */
export declare function buildTenantScopedPath(tenant: string, resourcePath: string, prefix?: string): string;
//# sourceMappingURL=tenant-isolation-validator.d.ts.map