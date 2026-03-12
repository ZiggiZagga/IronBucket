package com.ironbucket.sentinelgear.identity;

import java.util.*;

/**
 * Identity Service
 * 
 * Main service for JWT validation, claim normalization, and identity management
 */
public class IdentityService {
    
    private final JWTValidator jwtValidator;
    
    public IdentityService(JWTValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }
    
    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        return jwtValidator.validate(token);
    }
    
    /**
     * Extract subject from token
     */
    public String extractSubject(String token) {
        return jwtValidator.extractSubject(token);
    }
    
    /**
     * Extract tenant from token
     */
    public String extractTenant(String token) {
        return jwtValidator.extractTenant(token);
    }
    
    /**
     * Extract roles from token
     */
    public List<String> extractRoles(String token) {
        return jwtValidator.extractRoles(token);
    }
}
