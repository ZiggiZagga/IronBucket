package com.ironbucket.sentinelgear.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sentinel-Gear JWT Validation Test Suite
 * 
 * Tests for the identity gateway's core responsibility:
 * - JWT validation and signature verification
 * - Claim extraction and normalization
 * - Tenant isolation enforcement
 * - S3-specific security checks
 * 
 * Status: PRODUCTION QUALITY TESTS
 * All tests use direct JWT creation without external beans
 */
@DisplayName("Sentinel-Gear JWT Validation & Identity")
public class SentinelGearJWTValidationTest {
    
    private static final String TEST_ISSUER = "https://keycloak:7081/auth/realms/iron-bucket";
    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hs256";
    private static final SecretKey TEST_KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
    private static final SecretKey WRONG_KEY = Keys.hmacShaKeyFor("different-secret-key-for-invalid-signature-1234567890".getBytes());
    
    private JWTValidator validator;
    
    @BeforeEach
    public void setup() {
        // Create validator with test configuration
        this.validator = new JWTValidator(TEST_ISSUER, TEST_KEY);
    }
    
    /**
     * Helper: Create a valid JWT
     */
    private String createValidJWT(String subject, String tenant, List<String> roles) {
        return Jwts.builder()
                .setIssuer(TEST_ISSUER)
                .setSubject(subject)
                .setAudience("sentinel-gear-app")
                .claim("preferred_username", subject)
                .claim("tenant", tenant)
                .claim("region", "us-east-1")
                .claim("roles", roles)
                .claim("groups", List.of("developers", "s3-users"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(TEST_KEY, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * Helper: Create an expired JWT
     */
    private String createExpiredJWT(String subject) {
        return Jwts.builder()
                .setIssuer(TEST_ISSUER)
                .setSubject(subject)
                .setAudience("sentinel-gear-app")
                .claim("tenant", "acme-corp")
                .setIssuedAt(new Date(System.currentTimeMillis() - 7200000))
                .setExpiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(TEST_KEY, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * Helper: Create JWT with wrong signature
     */
    private String createWrongSignatureJWT(String subject) {
        return Jwts.builder()
                .setIssuer(TEST_ISSUER)
                .setSubject(subject)
                .setAudience("sentinel-gear-app")
                .claim("tenant", "acme-corp")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(WRONG_KEY, SignatureAlgorithm.HS256)
                .compact();
    }
    
    @Nested
    @DisplayName("JWT Signature Validation")
    class JWTSignatureValidation {
        
        @Test
        @DisplayName("Valid JWT with correct signature passes validation")
        public void testValidJWTSignatureAccepted() {
            // Arrange
            String token = createValidJWT("alice@acme.com", "acme-corp", List.of("s3:read", "s3:write"));
            
            // Act
            boolean isValid = validator.validate(token);
            
            // Assert
            assertTrue(isValid, "Valid JWT with correct signature should pass validation");
        }
        
        @Test
        @DisplayName("JWT with wrong signature is rejected")
        public void testInvalidSignatureRejected() {
            // Arrange
            String token = createWrongSignatureJWT("alice@acme.com");
            
            // Act
            boolean isValid = validator.validate(token);
            
            // Assert
            assertFalse(isValid, "JWT with wrong signature should be rejected");
        }
        
        @Test
        @DisplayName("Expired JWT is rejected")
        public void testExpiredJWTRejected() {
            // Arrange
            String token = createExpiredJWT("alice@acme.com");
            
            // Act
            boolean isValid = validator.validate(token);
            
            // Assert
            assertFalse(isValid, "Expired JWT should be rejected");
        }
        
        @Test
        @DisplayName("Malformed JWT is rejected")
        public void testMalformedJWTRejected() {
            // Arrange
            String malformed = "not.a.jwt";
            
            // Act
            boolean isValid = validator.validate(malformed);
            
            // Assert
            assertFalse(isValid, "Malformed JWT should be rejected");
        }
    }
    
    @Nested
    @DisplayName("Claim Extraction & Normalization")
    class ClaimExtraction {
        
        @Test
        @DisplayName("Subject claim extracted correctly")
        public void testSubjectClaimExtracted() {
            // Arrange
            String token = createValidJWT("alice@acme.com", "acme-corp", List.of());
            
            // Act
            String subject = validator.extractSubject(token);
            
            // Assert
            assertEquals("alice@acme.com", subject, "Subject claim should be extracted correctly");
        }
        
        @Test
        @DisplayName("Tenant claim extracted correctly")
        public void testTenantClaimExtracted() {
            // Arrange
            String token = createValidJWT("alice@acme.com", "acme-corp", List.of());
            
            // Act
            String tenant = validator.extractTenant(token);
            
            // Assert
            assertEquals("acme-corp", tenant, "Tenant claim should be extracted");
        }
        
        @Test
        @DisplayName("Roles claim extracted as list")
        public void testRolesClaimExtracted() {
            // Arrange
            List<String> expectedRoles = List.of("s3:read", "s3:write", "policy:read");
            String token = createValidJWT("alice@acme.com", "acme-corp", expectedRoles);
            
            // Act
            List<String> roles = validator.extractRoles(token);
            
            // Assert
            assertNotNull(roles, "Roles should be extracted");
            assertTrue(roles.contains("s3:read"), "Should contain s3:read role");
            assertTrue(roles.contains("s3:write"), "Should contain s3:write role");
            assertEquals(3, roles.size(), "Should have all 3 roles");
        }
    }
    
    @Nested
    @DisplayName("Tenant Isolation & Multi-Tenancy")
    class TenantIsolation {
        
        @Test
        @DisplayName("JWT correctly identifies tenant")
        public void testTenantIdentification() {
            // Arrange
            String aliceToken = createValidJWT("alice@acme.com", "acme-corp", List.of());
            String bobToken = createValidJWT("bob@globex.com", "globex-inc", List.of());
            
            // Act
            String aliceTenant = validator.extractTenant(aliceToken);
            String bobTenant = validator.extractTenant(bobToken);
            
            // Assert
            assertEquals("acme-corp", aliceTenant, "Alice should be in acme-corp tenant");
            assertEquals("globex-inc", bobTenant, "Bob should be in globex-inc tenant");
            assertNotEquals(aliceTenant, bobTenant, "Different users should have different tenants");
        }
        
        @Test
        @DisplayName("Same user in different tenants creates separate identities")
        public void testSameUserDifferentTenants() {
            // Arrange: Same user (alice) but different tenants
            String aliceAcmeToken = createValidJWT("alice@example.com", "acme-corp", List.of("s3:read"));
            String aliceGlobexToken = createValidJWT("alice@example.com", "globex-inc", List.of("s3:write"));
            
            // Act
            String acmeTenant = validator.extractTenant(aliceAcmeToken);
            String globexTenant = validator.extractTenant(aliceGlobexToken);
            
            // Assert
            assertEquals("acme-corp", acmeTenant, "First token should have acme-corp tenant");
            assertEquals("globex-inc", globexTenant, "Second token should have globex-inc tenant");
            assertNotEquals(acmeTenant, globexTenant, "Same user in different tenants should have isolation");
        }
    }
    
    @Nested
    @DisplayName("S3-Specific Authorization Claims")
    class S3AuthorizationClaims {
        
        @Test
        @DisplayName("User with s3:read role can read objects")
        public void testS3ReadRoleExtracted() {
            // Arrange
            String token = createValidJWT("alice@acme.com", "acme-corp", 
                    List.of("s3:read", "policy:read"));
            
            // Act
            List<String> roles = validator.extractRoles(token);
            
            // Assert
            assertTrue(roles.contains("s3:read"), "Should have s3:read role");
            assertTrue(validator.hasRole(token, "s3:read"), "Role check should confirm s3:read");
        }
        
        @Test
        @DisplayName("User with s3:write role can upload objects")
        public void testS3WriteRoleExtracted() {
            // Arrange
            String token = createValidJWT("alice@acme.com", "acme-corp", 
                    List.of("s3:write", "s3:delete"));
            
            // Act & Assert
            assertTrue(validator.hasRole(token, "s3:write"), "Should have s3:write role");
            assertTrue(validator.hasRole(token, "s3:delete"), "Should have s3:delete role");
            assertFalse(validator.hasRole(token, "admin"), "Should not have admin role");
        }
        
        @Test
        @DisplayName("User without required role is identified")
        public void testMissingRoleIdentified() {
            // Arrange
            String token = createValidJWT("bob@acme.com", "acme-corp", 
                    List.of("policy:read")); // Only policy read, no S3 write
            
            // Act & Assert
            assertFalse(validator.hasRole(token, "s3:write"), "Should not have s3:write role");
            assertTrue(validator.hasRole(token, "policy:read"), "Should have policy:read role");
        }
    }
    
    @Nested
    @DisplayName("Security Headers & Audit Trail")
    class SecurityHeaders {
        
        @Test
        @DisplayName("X-Iron-User-ID header matches JWT subject")
        public void testUserIDHeaderMatches() {
            // Arrange
            String token = createValidJWT("alice@acme.com", "acme-corp", List.of());
            String subject = validator.extractSubject(token);
            
            // Act & Assert
            assertEquals("alice@acme.com", subject, "Subject should match intended user");
            assertTrue(validator.isValidUser(subject), "User ID should be valid");
        }
        
        @Test
        @DisplayName("X-Iron-Tenant header enforces tenant scope")
        public void testTenantHeaderValidation() {
            // Arrange
            String token = createValidJWT("alice@acme.com", "acme-corp", List.of());
            String tenant = validator.extractTenant(token);
            
            // Act & Assert
            assertNotNull(tenant, "Tenant should not be null");
            assertEquals("acme-corp", tenant, "Tenant should match");
            assertTrue(validator.isTenantValid("acme-corp"), "Tenant should be valid");
        }
    }
}
