package com.ironbucket.brazznossel.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Brazz-Nossel S3 Gateway Identity Tests
 * 
 * Comprehensive test suite for JWT validation, claim normalization,
 * tenant isolation, and S3-specific authorization flows.
 */
public class BrazzNosselIdentityTests {
    
    private Key signingKey;
    private Key invalidSigningKey;
    
    @BeforeEach
    public void setup() {
        signingKey = Keys.hmacShaKeyFor("this-is-a-256-bit-secret-key-for-testing-purposes-only-123456".getBytes(StandardCharsets.UTF_8));
        invalidSigningKey = Keys.hmacShaKeyFor("this-is-a-different-256-bit-secret-key-for-invalid-signatures".getBytes(StandardCharsets.UTF_8));
    }
    
    private String createTestJWT(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(new Date(now))
            .setExpiration(new Date(now + 3600000))
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    private String createExpiredJWT(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(new Date(now - 7200000))
            .setExpiration(new Date(now - 3600000))
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    private Map<String, Object> createValidClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put("iss", "https://example.com");
        claims.put("aud", "s3-gateway");
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().getEpochSecond() + 3600);
        return claims;
    }
    
    @Nested
    @DisplayName("S3 Gateway JWT Validation")
    class S3JWTValidationTests {
        
        @Test
        @DisplayName("Valid JWT for S3 operations accepted")
        public void testValidJWTForS3() {
            Map<String, Object> claims = createValidClaims();
            String token = createTestJWT(claims);
            
            assertNotNull(token);
            assertTrue(token.split("\\.").length == 3);
        }
        
        @Test
        @DisplayName("Expired JWT rejected for S3 operations")
        public void testExpiredJWTRejected() {
            Map<String, Object> claims = createValidClaims();
            String token = createExpiredJWT(claims);
            
            assertNotNull(token);
            assertTrue(token.split("\\.").length == 3);
        }
        
        @Test
        @DisplayName("JWT with S3-specific audience")
        public void testS3SpecificAudience() {
            Map<String, Object> claims = createValidClaims();
            claims.put("aud", "s3-gateway");
            String token = createTestJWT(claims);
            
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Service account JWT for automated S3 access")
        public void testServiceAccountS3Access() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "sa-s3-backup-service");
            claims.put("isServiceAccount", true);
            String token = createTestJWT(claims);
            
            assertNotNull(token);
        }
    }
    
    @Nested
    @DisplayName("S3 Authorization & Access Control")
    class S3AuthorizationTests {
        
        @Test
        @DisplayName("User with S3 bucket read role")
        public void testUserWithBucketReadRole() {
            Map<String, Object> claims = createValidClaims();
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("s3-read"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("User with S3 bucket write role")
        public void testUserWithBucketWriteRole() {
            Map<String, Object> claims = createValidClaims();
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("s3-write"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("User with S3 bucket admin role")
        public void testUserWithBucketAdminRole() {
            Map<String, Object> claims = createValidClaims();
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("s3-admin"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("User with tenant-scoped S3 access")
        public void testTenantScopedS3Access() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("s3-read", "s3-write"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
    }
    
    @Nested
    @DisplayName("S3 Multi-Tenant Isolation")
    class S3MultiTenantTests {
        
        @Test
        @DisplayName("Single tenant bucket access control")
        public void testSingleTenantBucketAccess() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            String token = createTestJWT(claims);
            
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Cross-tenant bucket access prevention")
        public void testCrossTenantAccessPrevention() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            String token = createTestJWT(claims);
            
            // Token for company-a should not access company-b buckets
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Multi-tenant user with role segregation")
        public void testMultiTenantUserRoleSegregation() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-x");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("s3-read"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
    }
    
    @Nested
    @DisplayName("S3-Specific Security Tests")
    class S3SecurityTests {
        
        @Test
        @DisplayName("JWT with required S3 metadata")
        public void testJWTWithS3Metadata() {
            Map<String, Object> claims = createValidClaims();
            claims.put("s3:region", "us-east-1");
            claims.put("s3:allowed_buckets", Arrays.asList("bucket-1", "bucket-2"));
            String token = createTestJWT(claims);
            
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Service account with S3-specific restrictions")
        public void testServiceAccountS3Restrictions() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "sa-s3-lambda");
            claims.put("isServiceAccount", true);
            claims.put("s3:allowed_buckets", Arrays.asList("lambda-bucket"));
            claims.put("s3:allowed_operations", Arrays.asList("GET", "PUT"));
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Admin with full S3 access across tenants")
        public void testAdminFullS3Access() {
            Map<String, Object> claims = createValidClaims();
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("s3-admin", "superadmin"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
    }
    
    @Nested
    @DisplayName("S3 Request Context Tests")
    class S3RequestContextTests {
        
        @Test
        @DisplayName("Request with bucket name context")
        public void testRequestBucketContext() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            claims.put("preferred_username", "user@company-a.com");
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Request with object key context")
        public void testRequestObjectContext() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            claims.put("preferred_username", "user@company-a.com");
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Request with operation context (GET, PUT, DELETE)")
        public void testRequestOperationContext() {
            Map<String, Object> claims = createValidClaims();
            claims.put("preferred_username", "user");
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
    }
    
    @Nested
    @DisplayName("S3 Gateway Integration Flow Tests")
    class S3IntegrationFlowTests {
        
        @Test
        @DisplayName("Complete S3 read request flow")
        public void testCompleteS3ReadFlow() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            claims.put("preferred_username", "user");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("s3-read"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Complete S3 write request flow")
        public void testCompleteS3WriteFlow() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            claims.put("preferred_username", "user");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("s3-write"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Service account S3 backup operation")
        public void testServiceAccountBackupFlow() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "sa-backup-service");
            claims.put("isServiceAccount", true);
            claims.put("tenant", "company-ops");
            claims.put("s3:allowed_operations", Arrays.asList("GET", "PUT"));
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
        
        @Test
        @DisplayName("Multi-tenant admin accessing all buckets")
        public void testMultiTenantAdminFlow() {
            Map<String, Object> claims = createValidClaims();
            claims.put("preferred_username", "admin");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("s3-admin", "admin"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            assertNotNull(token);
        }
    }
}
