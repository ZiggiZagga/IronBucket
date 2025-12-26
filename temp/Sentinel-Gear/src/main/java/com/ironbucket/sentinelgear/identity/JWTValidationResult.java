package com.ironbucket.sentinelgear.identity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JWT Validation Result
 * 
 * Represents the outcome of JWT validation
 */
public class JWTValidationResult {
    
    private boolean valid;
    private String error;
    private Map<String, Object> claims;
    
    public JWTValidationResult() {
        this.claims = new HashMap<>();
    }
    
    public JWTValidationResult(boolean valid) {
        this();
        this.valid = valid;
    }
    
    public JWTValidationResult(boolean valid, String error) {
        this();
        this.valid = valid;
        this.error = error;
    }
    
    public JWTValidationResult(boolean valid, Map<String, Object> claims) {
        this.valid = valid;
        this.claims = new HashMap<>(claims);
    }
    
    public static JWTValidationResult valid(Map<String, Object> claims) {
        return new JWTValidationResult(true, claims);
    }
    
    public static JWTValidationResult invalid(String error) {
        return new JWTValidationResult(false, error);
    }
    
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    
    public Optional<String> getError() { return Optional.ofNullable(error); }
    public void setError(String error) { this.error = error; }
    
    public Map<String, Object> getClaims() { return new HashMap<>(claims); }
    public void setClaims(Map<String, Object> claims) { this.claims = new HashMap<>(claims); }
    
    public Object getClaim(String name) { return claims.get(name); }
    public void setClaim(String name, Object value) { claims.put(name, value); }
    
    @Override
    public String toString() {
        return "JWTValidationResult{" +
                "valid=" + valid +
                ", error='" + error + '\'' +
                ", claims=" + claims +
                '}';
    }
}
