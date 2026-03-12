package com.ironbucket.sentinelgear.identity;

import java.time.Instant;
import java.util.*;

/**
 * Normalized Identity Model
 * 
 * Represents a standardized user identity across all IronBucket components.
 * Created after JWT validation and claim normalization.
 */
public class NormalizedIdentity {
    
    // Core identity
    private String userId;
    private String username;
    private String issuer;
    private long issuedAt;
    private long expiresAt;
    
    // Role & Permission Context
    private List<String> roles;
    private List<String> realmRoles;
    private Map<String, List<String>> resourceRoles;
    
    // Organizational Context
    private String tenant;
    private String region;
    private List<String> groups;
    
    // User Metadata
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    
    // Enrichment Context
    private String ipAddress;
    private String userAgent;
    private String requestId;
    
    // Service Account Flag
    private boolean isServiceAccount;
    
    // Raw JWT Claims
    private Map<String, Object> rawClaims;
    
    // Metadata
    private long createdAt;
    
    // Constructors
    public NormalizedIdentity() {
        this.roles = new ArrayList<>();
        this.realmRoles = new ArrayList<>();
        this.resourceRoles = new HashMap<>();
        this.groups = new ArrayList<>();
        this.rawClaims = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.requestId = UUID.randomUUID().toString();
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    
    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }
    
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    
    public List<String> getRoles() { return Collections.unmodifiableList(roles); }
    public void setRoles(List<String> roles) { this.roles = new ArrayList<>(roles); }
    public void addRole(String role) { this.roles.add(role); }
    
    public List<String> getRealmRoles() { return Collections.unmodifiableList(realmRoles); }
    public void setRealmRoles(List<String> realmRoles) { this.realmRoles = new ArrayList<>(realmRoles); }
    
    public Map<String, List<String>> getResourceRoles() { return Collections.unmodifiableMap(resourceRoles); }
    public void setResourceRoles(Map<String, List<String>> resourceRoles) { 
        this.resourceRoles = new HashMap<>(resourceRoles); 
    }
    
    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public List<String> getGroups() { return Collections.unmodifiableList(groups); }
    public void setGroups(List<String> groups) { this.groups = new ArrayList<>(groups); }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    
    public boolean isServiceAccount() { return isServiceAccount; }
    public void setServiceAccount(boolean serviceAccount) { isServiceAccount = serviceAccount; }
    
    public Map<String, Object> getRawClaims() { return Collections.unmodifiableMap(rawClaims); }
    public void setRawClaims(Map<String, Object> rawClaims) { this.rawClaims = new HashMap<>(rawClaims); }
    
    public long getCreatedAt() { return createdAt; }
    
    @Override
    public String toString() {
        return "NormalizedIdentity{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", tenant='" + tenant + '\'' +
                ", isServiceAccount=" + isServiceAccount +
                ", roles=" + roles +
                '}';
    }
}
