package com.ironbucket.sentinelgear.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Identity Service
 * 
 * Main service for JWT validation, claim normalization, and identity management
 */
@Service
public class IdentityService {
    
    @Autowired
    private JWTValidator jwtValidator;
    
    @Autowired
    private ClaimNormalizer claimNormalizer;
    
    @Autowired
    private TenantIsolationPolicy tenantIsolationPolicy;
    
    /**
     * Complete identity flow: validate JWT → normalize claims → enforce tenant isolation
     */
    public NormalizedIdentity processIdentity(String token, ProcessingOptions options) {
        // Step 1: Validate JWT
        JWTValidationResult validationResult = jwtValidator.validate(token, 
            new JWTValidator.ValidationOptions()
                .setClockSkew(options.getClockSkew())
                .setIssuerWhitelist(options.getIssuerWhitelist())
                .setExpectedAudience(options.getExpectedAudience())
        );
        
        if (!validationResult.isValid()) {
            throw new IdentityProcessingException(
                "JWT validation failed: " + validationResult.getError().orElse("Unknown error")
            );
        }
        
        // Step 2: Normalize claims
        NormalizedIdentity identity = claimNormalizer.normalize(
            validationResult.getClaims(),
            new ClaimNormalizer.NormalizationOptions()
                .setDefaultTenant(options.getDefaultTenant())
                .setIpAddress(options.getIpAddress())
                .setUserAgent(options.getUserAgent())
                .setRequestId(options.getRequestId())
        );
        
        // Step 3: Enforce tenant isolation
        if (options.getTenantIsolationConfig() != null) {
            identity = tenantIsolationPolicy.enforceIsolation(
                identity, 
                options.getTenantIsolationConfig()
            );
        }
        
        return identity;
    }
    
    /**
     * Validate JWT only
     */
    public JWTValidationResult validateJWT(String token) {
        return jwtValidator.validate(token);
    }
    
    /**
     * Normalize claims only
     */
    public NormalizedIdentity normalizeClaims(Map<String, Object> claims) {
        return claimNormalizer.normalize(claims);
    }
    
    /**
     * Processing Options
     */
    public static class ProcessingOptions {
        private int clockSkew = 30;
        private List<String> issuerWhitelist = new ArrayList<>();
        private String expectedAudience = "sentinel-gear-app";
        private String defaultTenant = "default";
        private String ipAddress;
        private String userAgent;
        private String requestId;
        private TenantIsolationPolicy.TenantIsolationConfig tenantIsolationConfig;
        
        public int getClockSkew() { return clockSkew; }
        public ProcessingOptions setClockSkew(int skew) { 
            this.clockSkew = skew; 
            return this;
        }
        
        public List<String> getIssuerWhitelist() { return issuerWhitelist; }
        public ProcessingOptions setIssuerWhitelist(List<String> whitelist) { 
            this.issuerWhitelist = new ArrayList<>(whitelist); 
            return this;
        }
        
        public String getExpectedAudience() { return expectedAudience; }
        public ProcessingOptions setExpectedAudience(String audience) { 
            this.expectedAudience = audience; 
            return this;
        }
        
        public String getDefaultTenant() { return defaultTenant; }
        public ProcessingOptions setDefaultTenant(String tenant) { 
            this.defaultTenant = tenant; 
            return this;
        }
        
        public String getIpAddress() { return ipAddress; }
        public ProcessingOptions setIpAddress(String ip) { 
            this.ipAddress = ip; 
            return this;
        }
        
        public String getUserAgent() { return userAgent; }
        public ProcessingOptions setUserAgent(String agent) { 
            this.userAgent = agent; 
            return this;
        }
        
        public String getRequestId() { return requestId; }
        public ProcessingOptions setRequestId(String id) { 
            this.requestId = id; 
            return this;
        }
        
        public TenantIsolationPolicy.TenantIsolationConfig getTenantIsolationConfig() { 
            return tenantIsolationConfig; 
        }
        public ProcessingOptions setTenantIsolationConfig(TenantIsolationPolicy.TenantIsolationConfig config) { 
            this.tenantIsolationConfig = config; 
            return this;
        }
    }
    
    /**
     * Identity Processing Exception
     */
    public static class IdentityProcessingException extends RuntimeException {
        public IdentityProcessingException(String message) {
            super(message);
        }
        
        public IdentityProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
