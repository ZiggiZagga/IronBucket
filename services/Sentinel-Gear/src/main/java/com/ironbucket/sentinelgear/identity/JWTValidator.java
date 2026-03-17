package com.ironbucket.sentinelgear.identity;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

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
            String tenant = firstNonBlankClaim(claims,
                    "tenant",
                    "tenant_id",
                    "tenantId");
            if (tenant != null) {
                return tenant;
            }
            return extractOrganizationFromClaims(claims);
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * Extract primary organization identifier from JWT.
     * Supports common Keycloak organization claim shapes.
     */
    public String extractOrganization(String token) {
        try {
            Claims claims = parser.parseClaimsJws(token).getBody();
            return extractOrganizationFromClaims(claims);
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * Extract all organization identifiers from JWT.
     */
    public List<String> extractOrganizations(String token) {
        try {
            Claims claims = parser.parseClaimsJws(token).getBody();
            return extractOrganizationsFromClaims(claims);
        } catch (JwtException e) {
            return List.of();
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

    private String extractOrganizationFromClaims(Map<String, Object> claims) {
        String directOrg = firstNonBlankClaim(claims,
                "organization",
                "organization_id",
                "org",
                "org_id",
                "kc_org");
        if (directOrg != null) {
            return directOrg;
        }

        Object organizationClaim = claims.get("organization");
        if (organizationClaim instanceof Map<?, ?> organizationMap) {
            Object id = organizationMap.get("id");
            if (id instanceof String idValue && !idValue.isBlank()) {
                return idValue;
            }
            Object name = organizationMap.get("name");
            if (name instanceof String nameValue && !nameValue.isBlank()) {
                return nameValue;
            }
        }

        List<String> organizations = extractOrganizationsFromClaims(claims);
        return organizations.isEmpty() ? null : organizations.get(0);
    }

    private List<String> extractOrganizationsFromClaims(Map<String, Object> claims) {
        Set<String> organizations = new LinkedHashSet<>();

        Object organizationsClaim = claims.get("organizations");
        if (organizationsClaim instanceof List<?> orgList) {
            for (Object orgEntry : orgList) {
                if (orgEntry instanceof String orgId && !orgId.isBlank()) {
                    organizations.add(orgId);
                } else if (orgEntry instanceof Map<?, ?> orgMap) {
                    Object id = orgMap.get("id");
                    if (id instanceof String idValue && !idValue.isBlank()) {
                        organizations.add(idValue);
                        continue;
                    }
                    Object name = orgMap.get("name");
                    if (name instanceof String nameValue && !nameValue.isBlank()) {
                        organizations.add(nameValue);
                    }
                }
            }
        }

        Object groupsClaim = claims.get("groups");
        if (groupsClaim instanceof List<?> groups) {
            for (Object groupEntry : groups) {
                if (!(groupEntry instanceof String group) || group.isBlank()) {
                    continue;
                }
                if (group.startsWith("org:")) {
                    organizations.add(group.substring("org:".length()));
                } else if (group.startsWith("/org/")) {
                    organizations.add(group.substring("/org/".length()));
                } else if (group.startsWith("/orgs/")) {
                    organizations.add(group.substring("/orgs/".length()));
                }
            }
        }

        return new ArrayList<>(organizations);
    }

    private String firstNonBlankClaim(Map<String, Object> claims, String... keys) {
        for (String key : keys) {
            Object value = claims.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }
}
