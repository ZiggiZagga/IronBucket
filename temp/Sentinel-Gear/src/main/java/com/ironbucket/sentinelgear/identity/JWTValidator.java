package com.ironbucket.sentinelgear.identity;

import org.springframework.stereotype.Component;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT Validator
 * 
 * Validates JWT tokens for signature, expiration, required claims,
 * issuer whitelist, and audience.
 */
@Component
public class JWTValidator {
    
    private static final int DEFAULT_CLOCK_SKEW_SECONDS = 30;
    private final Key signingKey;
    private final List<String> issuerWhitelist;
    private final String expectedAudience;
    private final int clockSkewSeconds;
    
    public JWTValidator() {
        this.issuerWhitelist = new ArrayList<>();
        this.expectedAudience = "sentinel-gear-app";
        this.clockSkewSeconds = DEFAULT_CLOCK_SKEW_SECONDS;
        // Use a 256-bit key for HS256
        this.signingKey = Keys.hmacShaKeyFor("this-is-a-256-bit-secret-key-for-testing-purposes-only-123456".getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Validate JWT token
     */
    public JWTValidationResult validate(String token) {
        return validate(token, new ValidationOptions());
    }
    
    /**
     * Validate JWT with options
     */
    public JWTValidationResult validate(String token, ValidationOptions options) {
        try {
            // Check token format
            if (token == null || token.trim().isEmpty()) {
                return JWTValidationResult.invalid("Token is empty");
            }
            
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return JWTValidationResult.invalid("Malformed JWT: expected 3 parts, got " + parts.length);
            }
            
            // Parse token
            JwtParser parser = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build();
                
            Jws<Claims> jws = parser.parseClaimsJws(token);
            Claims claims = jws.getBody();
            
            // Validate required claims
            JWTValidationResult requiredResult = validateRequiredClaims(claims);
            if (!requiredResult.isValid()) {
                return requiredResult;
            }
            
            // Validate expiration
            JWTValidationResult expirationResult = validateExpiration(claims, options.getClockSkew());
            if (!expirationResult.isValid()) {
                return expirationResult;
            }
            
            // Validate issued at
            JWTValidationResult issuedAtResult = validateIssuedAt(claims, options.getClockSkew());
            if (!issuedAtResult.isValid()) {
                return issuedAtResult;
            }
            
            // Validate issuer
            if (!options.getIssuerWhitelist().isEmpty()) {
                JWTValidationResult issuerResult = validateIssuer(claims, options.getIssuerWhitelist());
                if (!issuerResult.isValid()) {
                    return issuerResult;
                }
            }
            
            // Validate audience
            if (options.getExpectedAudience() != null) {
                JWTValidationResult audienceResult = validateAudience(claims, options.getExpectedAudience());
                if (!audienceResult.isValid()) {
                    return audienceResult;
                }
            }
            
            // Convert to Map for return
            Map<String, Object> claimsMap = new HashMap<>(claims);
            return JWTValidationResult.valid(claimsMap);
            
        } catch (SignatureException e) {
            return JWTValidationResult.invalid("Invalid JWT signature: " + e.getMessage());
        } catch (MalformedJwtException e) {
            return JWTValidationResult.invalid("Malformed JWT: " + e.getMessage());
        } catch (ExpiredJwtException e) {
            return JWTValidationResult.invalid("JWT is expired");
        } catch (JwtException e) {
            return JWTValidationResult.invalid("JWT validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Validate required claims
     */
    private JWTValidationResult validateRequiredClaims(Claims claims) {
        List<String> requiredClaims = Arrays.asList("sub", "iss", "aud", "iat", "exp");
        
        for (String claim : requiredClaims) {
            if (!claims.containsKey(claim)) {
                return JWTValidationResult.invalid("Missing required claim: " + claim);
            }
        }
        
        return new JWTValidationResult(true);
    }
    
    /**
     * Validate token expiration
     */
    private JWTValidationResult validateExpiration(Claims claims, int clockSkew) {
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            return JWTValidationResult.invalid("Missing exp claim");
        }
        
        long expirationSeconds = expiration.getTime() / 1000;
        long nowSeconds = Instant.now().getEpochSecond();
        
        if (nowSeconds > expirationSeconds + clockSkew) {
            return JWTValidationResult.invalid("JWT is expired");
        }
        
        return new JWTValidationResult(true);
    }
    
    /**
     * Validate issued at time
     */
    private JWTValidationResult validateIssuedAt(Claims claims, int clockSkew) {
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt == null) {
            return JWTValidationResult.invalid("Missing iat claim");
        }
        
        long issuedSeconds = issuedAt.getTime() / 1000;
        long nowSeconds = Instant.now().getEpochSecond();
        
        if (issuedSeconds > nowSeconds + clockSkew) {
            return JWTValidationResult.invalid("JWT issued in the future");
        }
        
        return new JWTValidationResult(true);
    }
    
    /**
     * Validate issuer
     */
    private JWTValidationResult validateIssuer(Claims claims, List<String> issuerWhitelist) {
        String issuer = claims.getIssuer();
        if (issuer == null) {
            return JWTValidationResult.invalid("Missing iss claim");
        }
        
        if (!issuerWhitelist.contains(issuer)) {
            return JWTValidationResult.invalid("Issuer not whitelisted: " + issuer);
        }
        
        return new JWTValidationResult(true);
    }
    
    /**
     * Validate audience
     */
    private JWTValidationResult validateAudience(Claims claims, String expectedAudience) {
        Object audienceObj = claims.get("aud");
        if (audienceObj == null) {
            return JWTValidationResult.invalid("Missing aud claim");
        }
        
        // Handle both Set<String> and String types
        String audience;
        if (audienceObj instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<String> audiences = (Set<String>) audienceObj;
            if (audiences.contains(expectedAudience)) {
                return new JWTValidationResult(true);
            }
            audience = String.join(",", audiences);
        } else if (audienceObj instanceof String) {
            audience = (String) audienceObj;
            if (audience.equals(expectedAudience)) {
                return new JWTValidationResult(true);
            }
        } else {
            audience = audienceObj.toString();
        }
        
        return JWTValidationResult.invalid("Audience mismatch. Expected: " + expectedAudience + ", got: " + audience);
    }
    
    /**
     * Extract realm roles from JWT
     */
    public List<String> extractRealmRoles(Map<String, Object> claims) {
        @SuppressWarnings("unchecked")
        Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }
        
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles != null ? new ArrayList<>(roles) : Collections.emptyList();
    }
    
    /**
     * Extract resource roles from JWT
     */
    public List<String> extractResourceRoles(Map<String, Object> claims, String resource) {
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
        if (resourceAccess == null) {
            return Collections.emptyList();
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceRoles = (Map<String, Object>) resourceAccess.get(resource);
        if (resourceRoles == null) {
            return Collections.emptyList();
        }
        
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) resourceRoles.get("roles");
        return roles != null ? new ArrayList<>(roles) : Collections.emptyList();
    }
    
    /**
     * Detect if JWT represents a service account
     */
    public boolean isServiceAccount(Map<String, Object> claims) {
        Object isServiceAccount = claims.get("isServiceAccount");
        if (isServiceAccount instanceof Boolean) {
            return (Boolean) isServiceAccount;
        }
        
        String sub = (String) claims.get("sub");
        return sub != null && sub.startsWith("sa-");
    }
    
    /**
     * Validation Options
     */
    public static class ValidationOptions {
        private int clockSkew = DEFAULT_CLOCK_SKEW_SECONDS;
        private List<String> issuerWhitelist = new ArrayList<>();
        private String expectedAudience;
        
        public ValidationOptions() {}
        
        public int getClockSkew() { return clockSkew; }
        public ValidationOptions setClockSkew(int seconds) { 
            this.clockSkew = seconds; 
            return this;
        }
        
        public List<String> getIssuerWhitelist() { return issuerWhitelist; }
        public ValidationOptions setIssuerWhitelist(List<String> whitelist) { 
            this.issuerWhitelist = new ArrayList<>(whitelist); 
            return this;
        }
        
        public String getExpectedAudience() { return expectedAudience; }
        public ValidationOptions setExpectedAudience(String audience) { 
            this.expectedAudience = audience; 
            return this;
        }
    }
}
