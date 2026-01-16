package com.ironbucket.sentinelgear.fixtures;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;

/**
 * Generates test JWT tokens with various claim configurations
 * for testing identity extraction and validation in Sentinel-Gear
 */
@Component
public class JWTFixtures {

    private static final String ISSUER = "https://keycloak:7081/auth/realms/iron-bucket";
    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";
    private static final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

    /**
     * Generate a valid JWT token with all required claims
     */
    public String generateValidJWT(String subject, String region, List<String> groups, List<String> services) {
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(subject)
                .setAudience("sentinel-gear-app")  // Add audience claim
                .claim("region", region)
                .claim("groups", groups)
                .claim("services", services)
                .setIssuedAt(new Date())  // Add issued at claim
                .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate a JWT with alice@acme-corp as subject
     */
    public String generateAliceACMEJWT() {
        return generateValidJWT(
                "alice@acme-corp",
                "us-east-1",
                List.of("acme-corp:admins", "acme-corp:devs"),
                List.of("s3", "kms")
        );
    }

    /**
     * Generate a JWT with bob@evil-corp as subject (for multi-tenant tests)
     */
    public String generateBobEvilJWT() {
        return generateValidJWT(
                "bob@evil-corp",
                "us-west-2",
                List.of("evil-corp:admins"),
                List.of("s3")
        );
    }

    /**
     * Generate an expired JWT for alice@acme-corp
     */
    public String generateExpiredAliceACMEJWT() {
        return generateExpiredJWT("alice@acme-corp");
    }

    /**
     * Generate an expired JWT token
     */
    public String generateExpiredJWT(String subject) {
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(subject)
                .setAudience("sentinel-gear-app")
                .claim("region", "us-east-1")
                .claim("groups", List.of("test:group"))
                .claim("services", List.of("s3"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate a JWT with wrong issuer
     */
    public String generateWrongIssuerJWT(String subject) {
        return Jwts.builder()
                .setIssuer("https://wrong-issuer.example.com")
                .setSubject(subject)
                .setAudience("sentinel-gear-app")
                .claim("region", "us-east-1")
                .claim("groups", List.of("test:group"))
                .claim("services", List.of("s3"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate an unsigned JWT (malformed)
     */
    public String generateUnsignedJWT(String subject) {
        return "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.";
    }

    /**
     * Generate completely malformed JWT
     */
    public String generateMalformedJWT() {
        return "not.a.valid.token.at.all";
    }

    /**
     * Generate JWT with minimal claims (missing region)
     */
    public String generateMinimalJWT(String subject) {
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(subject)
                .claim("groups", List.of("test:group"))
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Get the test issuer value
     */
    public String getTestIssuer() {
        return ISSUER;
    }

    /**
     * Get the test secret key
     */
    public SecretKey getTestSecretKey() {
        return key;
    }

    /**
     * Generate JWT with invalid signature (signed with different key)
     */
    public String generateJWTWithInvalidSignature() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("different-secret-key-for-invalid-signature-1234567890".getBytes());
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject("alice@acme-corp")
                .setAudience("sentinel-gear-app")
                .claim("region", "us-east-1")
                .claim("groups", List.of("acme-corp:admins"))
                .claim("services", List.of("s3"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(wrongKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate JWT for service account with sa- prefix
     */
    public String generateServiceAccountJWT(String serviceAccountName) {
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(serviceAccountName)
                .setAudience("sentinel-gear-app")
                .claim("isServiceAccount", true)
                .claim("region", "us-east-1")
                .claim("groups", List.of("service-accounts"))
                .claim("services", List.of("s3"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate JWT with realm roles
     */
    public String generateJWTWithRealmRoles(List<String> roles) {
        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", roles);
        
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject("alice@acme-corp")
                .setAudience("sentinel-gear-app")
                .claim("realm_access", realmAccess)
                .claim("region", "us-east-1")
                .claim("groups", List.of("acme-corp:admins"))
                .claim("services", List.of("s3"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate JWT with resource roles for specific app
     */
    public String generateJWTWithResourceRoles(String appName, List<String> roles) {
        Map<String, Object> appRoles = new HashMap<>();
        appRoles.put("roles", roles);
        
        Map<String, Object> resourceAccess = new HashMap<>();
        resourceAccess.put(appName, appRoles);
        
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject("alice@acme-corp")
                .setAudience("sentinel-gear-app")
                .claim("resource_access", resourceAccess)
                .claim("region", "us-east-1")
                .claim("groups", List.of("acme-corp:admins"))
                .claim("services", List.of("s3"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate complete JWT with all identity claims
     */
    public String generateCompleteIdentityJWT(String subject, String tenant, List<String> groups, List<String> regions) {
        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", List.of("user", "admin"));
        
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(subject)
                .setAudience("sentinel-gear-app")
                .claim("tenant", tenant)
                .claim("realm_access", realmAccess)
                .claim("region", regions.get(0))
                .claim("regions", regions)
                .claim("groups", groups)
                .claim("services", List.of("s3", "kms"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate JWT with specific audience
     */
    public String generateJWTWithAudience(String audience) {
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject("alice@acme-corp")
                .setAudience(audience)
                .claim("region", "us-east-1")
                .claim("groups", List.of("acme-corp:admins"))
                .claim("services", List.of("s3"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate JWT with groups claim
     */
    public String generateJWTWithGroups(List<String> groups) {
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject("alice@acme-corp")
                .setAudience("sentinel-gear-app")
                .claim("groups", groups)
                .claim("region", "us-east-1")
                .claim("services", List.of("s3"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate JWT with region claim
     */
    public String generateJWTWithRegion(String region) {
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject("alice@acme-corp")
                .setAudience("sentinel-gear-app")
                .claim("region", region)
                .claim("groups", List.of("acme-corp:admins"))
                .claim("services", List.of("s3"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }}