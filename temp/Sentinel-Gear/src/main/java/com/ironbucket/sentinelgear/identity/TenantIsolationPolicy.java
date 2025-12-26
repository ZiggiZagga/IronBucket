package com.ironbucket.sentinelgear.identity;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Tenant Isolation Policy
 * 
 * Enforces single-tenant and multi-tenant isolation
 */
@Component
public class TenantIsolationPolicy {
    
    private static final String TENANT_PATTERN = "^[a-zA-Z0-9\\-_]+$";
    private static final Pattern VALID_TENANT = Pattern.compile(TENANT_PATTERN);
    
    /**
     * Enforce tenant isolation
     */
    public NormalizedIdentity enforceIsolation(NormalizedIdentity identity, TenantIsolationConfig config) {
        if (config.getMode() == TenantMode.SINGLE) {
            return enforceSingleTenant(identity, config);
        } else if (config.getMode() == TenantMode.MULTI) {
            return enforceMultiTenant(identity, config);
        }
        
        return identity;
    }
    
    /**
     * Enforce single tenant mode
     */
    private NormalizedIdentity enforceSingleTenant(NormalizedIdentity identity, TenantIsolationConfig config) {
        String configuredTenant = config.getTenant();
        String identityTenant = identity.getTenant();
        
        // If tenant is configured, override JWT tenant
        if (configuredTenant != null && !configuredTenant.isEmpty()) {
            identity.setTenant(configuredTenant);
        } else if (identityTenant != null) {
            // Use JWT tenant if no config
            identity.setTenant(identityTenant);
        }
        
        // Verify match if both exist and validation is strict
        if (configuredTenant != null && identityTenant != null && 
            config.isValidateMode() && !configuredTenant.equals(identityTenant)) {
            throw new TenantIsolationException("Tenant mismatch in single-tenant mode");
        }
        
        return identity;
    }
    
    /**
     * Enforce multi tenant mode
     */
    private NormalizedIdentity enforceMultiTenant(NormalizedIdentity identity, TenantIsolationConfig config) {
        String tenant = identity.getTenant();
        
        // Tenant is required in multi-tenant mode
        if (tenant == null || tenant.isEmpty()) {
            if (config.getAutoAssignTenant() != null) {
                identity.setTenant(config.getAutoAssignTenant());
                tenant = identity.getTenant(); // Update local variable with newly assigned tenant
            } else {
                throw new TenantIsolationException("Tenant required in multi-tenant mode");
            }
        }
        
        // Validate tenant format
        if (!isValidTenantIdentifier(tenant)) {
            throw new TenantIsolationException("Invalid tenant identifier format: " + tenant);
        }
        
        return identity;
    }
    
    /**
     * Validate tenant identifier format
     */
    public boolean isValidTenantIdentifier(String tenant) {
        if (tenant == null || tenant.isEmpty()) {
            return false;
        }
        
        return VALID_TENANT.matcher(tenant).matches() && tenant.length() <= 255;
    }
    
    /**
     * Filter policies by tenant
     */
    public List<Map<String, Object>> filterPoliciesByTenant(List<Map<String, Object>> policies, String tenant) {
        return policies.stream()
            .filter(policy -> {
                Object policyTenant = policy.get("tenant");
                if (policyTenant == null) {
                    return true; // Include policies without tenant restriction
                }
                return policyTenant.equals(tenant);
            })
            .toList();
    }
    
    /**
     * Check if identity can access bucket
     */
    public boolean canAccessBucket(NormalizedIdentity identity, String bucketName) {
        String tenant = identity.getTenant();
        if (tenant == null) {
            return false;
        }
        
        // Tenant-prefixed bucket access
        return bucketName.startsWith(tenant + "-") || bucketName.equals(tenant);
    }
    
    /**
     * Check if identity can access resource
     */
    public boolean canAccessResource(NormalizedIdentity identity, String resource) {
        String tenant = identity.getTenant();
        if (tenant == null) {
            return false;
        }
        
        // Check if resource contains tenant prefix
        String[] parts = resource.split("/");
        if (parts.length > 0) {
            String prefix = parts[0];
            // ARN-style or path-style with tenant
            return resource.contains(tenant) && !resource.contains("customer-") || 
                   prefix.contains(tenant);
        }
        
        return false;
    }
    
    /**
     * Tenant Mode enumeration
     */
    public enum TenantMode {
        SINGLE,
        MULTI
    }
    
    /**
     * Tenant Isolation Configuration
     */
    public static class TenantIsolationConfig {
        private TenantMode mode = TenantMode.SINGLE;
        private String tenant;
        private String autoAssignTenant;
        private String tenantHeaderName = "x-tenant-id";
        private boolean validateMode = false;
        private boolean validateHeaderMatch = false;
        
        public TenantMode getMode() { return mode; }
        public TenantIsolationConfig setMode(TenantMode mode) { 
            this.mode = mode; 
            return this;
        }
        
        public String getTenant() { return tenant; }
        public TenantIsolationConfig setTenant(String tenant) { 
            this.tenant = tenant; 
            return this;
        }
        
        public String getAutoAssignTenant() { return autoAssignTenant; }
        public TenantIsolationConfig setAutoAssignTenant(String tenant) { 
            this.autoAssignTenant = tenant; 
            return this;
        }
        
        public String getTenantHeaderName() { return tenantHeaderName; }
        public TenantIsolationConfig setTenantHeaderName(String header) { 
            this.tenantHeaderName = header; 
            return this;
        }
        
        public boolean isValidateMode() { return validateMode; }
        public TenantIsolationConfig setValidateMode(boolean validate) { 
            this.validateMode = validate; 
            return this;
        }
        
        public boolean isValidateHeaderMatch() { return validateHeaderMatch; }
        public TenantIsolationConfig setValidateHeaderMatch(boolean validate) { 
            this.validateHeaderMatch = validate; 
            return this;
        }
    }
    
    /**
     * Tenant Isolation Exception
     */
    public static class TenantIsolationException extends RuntimeException {
        public TenantIsolationException(String message) {
            super(message);
        }
        
        public TenantIsolationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
