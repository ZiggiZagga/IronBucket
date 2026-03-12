package com.ironbucket.brazznossel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NormalizedIdentity - Standard identity representation after JWT validation.
 * 
 * This class represents the normalized form of a user's identity after JWT
 * validation and claims extraction by Sentinel-Gear. It ensures consistent
 * identity representation across all services.
 * 
 * Aligned with Sentinel-Gear JWT validation standards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedIdentity {
    
    /**
     * Unique user identifier (e.g., "alice@acme.com" or "user-123")
     */
    private String userId;
    
    /**
     * Tenant identifier for multi-tenancy isolation
     */
    private String tenantId;
    
    /**
     * Geographic region claimed by the user
     */
    private String region;
    
    /**
     * User's group memberships (e.g., "developers", "s3-read")
     */
    private List<String> groups;
    
    /**
     * Accessible services (e.g., "s3", "audit", "policy")
     */
    private List<String> services;
    
    /**
     * User roles for authorization (e.g., "s3:read", "s3:write", "s3:admin")
     */
    private List<String> roles;
    
    /**
     * User's preferred username (for display/logging)
     */
    private String preferredUsername;
    
    /**
     * Email address (if available)
     */
    private String email;
    
    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
    
    /**
     * Check if user has access to a service
     */
    public boolean hasService(String service) {
        return services != null && services.contains(service);
    }
    
    /**
     * Check if user belongs to a group
     */
    public boolean hasGroup(String group) {
        return groups != null && groups.contains(group);
    }
}
