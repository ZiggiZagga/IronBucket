package com.ironbucket.sentinelgear.identity;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Claim Normalizer
 * 
 * Converts raw JWT claims into a NormalizedIdentity object
 */
@Component
public class ClaimNormalizer {
    
    private static final String DEFAULT_TENANT = "default";
    
    /**
     * Normalize JWT claims to NormalizedIdentity
     */
    public NormalizedIdentity normalize(Map<String, Object> claims) {
        return normalize(claims, new NormalizationOptions());
    }
    
    /**
     * Normalize with custom options
     */
    public NormalizedIdentity normalize(Map<String, Object> claims, NormalizationOptions options) {
        NormalizedIdentity identity = new NormalizedIdentity();
        
        // Set raw claims
        identity.setRawClaims(new HashMap<>(claims));
        
        // Extract core identity
        String userId = (String) claims.get("sub");
        identity.setUserId(userId);
        
        String issuer = (String) claims.get("iss");
        identity.setIssuer(issuer);
        
        Long iat = extractLongClaim(claims, "iat");
        if (iat != null) {
            identity.setIssuedAt(iat);
        }
        
        Long exp = extractLongClaim(claims, "exp");
        if (exp != null) {
            identity.setExpiresAt(exp);
        }
        
        // Extract user metadata
        String email = (String) claims.get("email");
        identity.setEmail(email);
        
        String preferredUsername = (String) claims.get("preferred_username");
        String givenName = (String) claims.get("given_name");
        String familyName = (String) claims.get("family_name");
        
        // Resolve username (preferred_username > email > sub)
        String username = preferredUsername != null ? preferredUsername :
                         email != null ? email : userId;
        identity.setUsername(username);
        
        // Set name fields
        identity.setFirstName(givenName);
        identity.setLastName(familyName);
        
        // Compute full name
        if (givenName != null && familyName != null) {
            identity.setFullName(givenName + " " + familyName);
        } else if (givenName != null) {
            identity.setFullName(givenName);
        } else if (familyName != null) {
            identity.setFullName(familyName);
        }
        
        // Extract tenant
        String tenant = (String) claims.get("tenant");
        if (tenant == null) {
            tenant = options.getDefaultTenant();
        }
        identity.setTenant(tenant);
        
        // Extract region
        String region = (String) claims.get("region");
        identity.setRegion(region);
        
        // Extract groups
        @SuppressWarnings("unchecked")
        List<String> groups = (List<String>) claims.get("groups");
        if (groups != null) {
            identity.setGroups(new ArrayList<>(groups));
        }
        
        // Extract roles
        normalizeRoles(claims, identity);
        
        // Set enrichment context if provided
        if (options.getIpAddress() != null) {
            identity.setIpAddress(options.getIpAddress());
        }
        if (options.getUserAgent() != null) {
            identity.setUserAgent(options.getUserAgent());
        }
        if (options.getRequestId() != null) {
            identity.setRequestId(options.getRequestId());
        }
        
        // Detect service account
        boolean isServiceAccount = isServiceAccount(claims, userId);
        identity.setServiceAccount(isServiceAccount);
        
        return identity;
    }
    
    /**
     * Normalize roles from JWT claims
     */
    private void normalizeRoles(Map<String, Object> claims, NormalizedIdentity identity) {
        Set<String> allRoles = new HashSet<>();
        
        // Extract realm roles
        @SuppressWarnings("unchecked")
        Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
        if (realmAccess != null) {
            @SuppressWarnings("unchecked")
            List<String> realmRoles = (List<String>) realmAccess.get("roles");
            if (realmRoles != null) {
                identity.setRealmRoles(new ArrayList<>(realmRoles));
                allRoles.addAll(realmRoles);
            }
        }
        
        // Extract resource roles
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
        if (resourceAccess != null) {
            Map<String, List<String>> resourceRoles = new HashMap<>();
            for (Map.Entry<String, Object> entry : resourceAccess.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> roles = (Map<String, Object>) entry.getValue();
                if (roles != null) {
                    @SuppressWarnings("unchecked")
                    List<String> roleList = (List<String>) roles.get("roles");
                    if (roleList != null) {
                        resourceRoles.put(entry.getKey(), new ArrayList<>(roleList));
                        allRoles.addAll(roleList);
                    }
                }
            }
            identity.setResourceRoles(resourceRoles);
        }
        
        // Set combined roles
        identity.setRoles(new ArrayList<>(allRoles));
    }
    
    /**
     * Detect if identity is a service account
     */
    private boolean isServiceAccount(Map<String, Object> claims, String userId) {
        Object isServiceAccount = claims.get("isServiceAccount");
        if (isServiceAccount instanceof Boolean) {
            return (Boolean) isServiceAccount;
        }
        
        return userId != null && userId.startsWith("sa-");
    }
    
    /**
     * Extract long value from claims
     */
    private Long extractLongClaim(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
    
    /**
     * Normalization Options
     */
    public static class NormalizationOptions {
        private String defaultTenant = DEFAULT_TENANT;
        private String ipAddress;
        private String userAgent;
        private String requestId;
        
        public NormalizationOptions() {
            if (this.requestId == null) {
                this.requestId = UUID.randomUUID().toString();
            }
        }
        
        public String getDefaultTenant() { return defaultTenant; }
        public NormalizationOptions setDefaultTenant(String tenant) { 
            this.defaultTenant = tenant; 
            return this;
        }
        
        public String getIpAddress() { return ipAddress; }
        public NormalizationOptions setIpAddress(String ip) { 
            this.ipAddress = ip; 
            return this;
        }
        
        public String getUserAgent() { return userAgent; }
        public NormalizationOptions setUserAgent(String agent) { 
            this.userAgent = agent; 
            return this;
        }
        
        public String getRequestId() { return requestId; }
        public NormalizationOptions setRequestId(String id) { 
            this.requestId = id; 
            return this;
        }
    }
}
