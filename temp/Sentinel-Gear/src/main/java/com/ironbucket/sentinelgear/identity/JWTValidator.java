package com.ironbucket.sentinelgear.identity;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * JWTValidator - Core Identity Gateway Component
 * 
 * Responsible for:
 * - JWT signature validation
 * - Claim extraction and normalization
 * - Tenant isolation enforcement
 * - S3 authorization claim verification
 */
public class JWTValidator {
    
    private final String issuer;
    private final SecretKey secretKey;
    private final JwtParser parser;
    
    public JWTValidator(String issuer, SecretKey secretKey) {
        this.issuer = issuer;
        this.secretKey = secretKey;
        this.parser = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build();
    }
    
    /**
     * Validate JWT token: signature, expiration, issuer
     */
    public boolean validate(String token) {
        try {
            Claims claims = parser.parseClaimsJws(token).getBody();
            
            // Check issuer
            if (!issuer.equals(claims.getIssuer())) {
                return false;
            }
            
            // Check expiration
            if (claims.getExpiration().before(new Date())) {
                return false;
            }
            
            return true;
        } catch (SignatureException | ExpiredJwtException | MalformedJwtException | 
                 UnsupportedJwtException | IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Extract subject (user ID) from JWT
     */
    public String extractSubject(String token) {
        try {
            Claims claims = parser.parseClaimsJws(token).getBody();
            return claims.getSubject();
        } catch (JwtException e) {
            return null;
        }
    }
    
    /**
     * Extract tenant claim from JWT
     */
    public String extractTenant(String token) {
        try {
            Claims claims = parser.parseClaimsJws(token).getBody();
            return (String) claims.get("tenant");
        } catch (JwtException e) {
            return null;
        }
    }
    
    /**
     * Extract roles list from JWT
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        try {
            Claims claims = parser.parseClaimsJws(token).getBody();
            Object rolesObj = claims.get("roles");
            
            if (rolesObj instanceof List) {
                return (List<String>) rolesObj;
            }
            return List.of();
        } catch (JwtException e) {
            return List.of();
        }
    }
    
    /**
     * Check if user has specific role
     */
    public boolean hasRole(String token, String role) {
        List<String> roles = extractRoles(token);
        return roles.contains(role);
    }
    
    /**
     * Validate user ID format
     */
    public boolean isValidUser(String userId) {
        return userId != null && !userId.isEmpty() && userId.contains("@");
    }
    
    /**
     * Validate tenant format
     */
    public boolean isTenantValid(String tenant) {
        return tenant != null && !tenant.isEmpty() && tenant.matches("^[a-zA-Z0-9_-]+$");
    }
}
