package com.ironbucket.sentinelgear.identity;

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
 * Comprehensive Identity Service Test Suite
 * 
 * Tests cover JWT validation, claim normalization, tenant isolation, 
 * and edge cases across all identity modules.
 */
public class IdentityServiceIntegrationTest {
    
    private JWTValidator jwtValidator;
    private ClaimNormalizer claimNormalizer;
    private TenantIsolationPolicy tenantIsolationPolicy;
    private Key signingKey;
    private Key invalidSigningKey;
    
    @BeforeEach
    public void setup() {
        jwtValidator = new JWTValidator();
        claimNormalizer = new ClaimNormalizer();
        tenantIsolationPolicy = new TenantIsolationPolicy();
        
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
    
    private String createFutureIssuedJWT(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(new Date(now + 3600000))
            .setExpiration(new Date(now + 7200000))
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    private String createInvalidSignatureJWT(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(new Date(now))
            .setExpiration(new Date(now + 3600000))
            .signWith(invalidSigningKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    private Map<String, Object> createValidClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put("iss", "https://example.com");
        claims.put("aud", "sentinel-gear-app");
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().getEpochSecond() + 3600);
        return claims;
    }
    
    @Nested
    @DisplayName("JWT Validation Tests")
    class JWTValidationTests {
        
        @Test
        @DisplayName("Valid JWT passes validation")
        public void testValidJWT() {
            Map<String, Object> claims = createValidClaims();
            String token = createTestJWT(claims);
            
            JWTValidationResult result = jwtValidator.validate(token);
            
            assertTrue(result.isValid());
            assertEquals("user-123", result.getClaims().get("sub"));
        }
        
        @Test
        @DisplayName("Expired JWT fails validation")
        public void testExpiredJWT() {
            Map<String, Object> claims = createValidClaims();
            String token = createExpiredJWT(claims);
            
            JWTValidationResult result = jwtValidator.validate(token);
            
            assertFalse(result.isValid());
            assertTrue(result.getError().isPresent());
            assertTrue(result.getError().get().contains("expired"));
        }
        
        @Test
        @DisplayName("Invalid signature fails validation")
        public void testInvalidSignature() {
            Map<String, Object> claims = createValidClaims();
            String token = createInvalidSignatureJWT(claims);
            
            JWTValidationResult result = jwtValidator.validate(token);
            
            assertFalse(result.isValid());
            assertTrue(result.getError().isPresent());
        }
        
        @Test
        @DisplayName("Empty token fails validation")
        public void testEmptyToken() {
            JWTValidationResult result = jwtValidator.validate("");
            
            assertFalse(result.isValid());
            assertTrue(result.getError().isPresent());
        }
        
        @Test
        @DisplayName("Null token fails validation")
        public void testNullToken() {
            JWTValidationResult result = jwtValidator.validate(null);
            
            assertFalse(result.isValid());
            assertTrue(result.getError().isPresent());
        }
        
        @Test
        @DisplayName("Malformed JWT fails validation")
        public void testMalformedJWT() {
            String malformedToken = "not.a.valid.jwt";
            
            JWTValidationResult result = jwtValidator.validate(malformedToken);
            
            assertFalse(result.isValid());
            assertTrue(result.getError().isPresent());
        }
        
        @Test
        @DisplayName("JWT missing required claims fails")
        public void testMissingRequiredClaims() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "user-123");
            String token = createTestJWT(claims);
            
            JWTValidationResult result = jwtValidator.validate(token);
            
            assertFalse(result.isValid());
            assertTrue(result.getError().isPresent());
        }
        
        @Test
        @DisplayName("JWT with invalid issuer fails when whitelist is provided")
        public void testInvalidIssuer() {
            Map<String, Object> claims = createValidClaims();
            claims.put("iss", "https://untrusted.com");
            String token = createTestJWT(claims);
            
            JWTValidator.ValidationOptions options = new JWTValidator.ValidationOptions()
                .setIssuerWhitelist(Arrays.asList("https://trusted.com"));
            
            JWTValidationResult result = jwtValidator.validate(token, options);
            
            assertFalse(result.isValid());
            assertTrue(result.getError().isPresent());
        }
        
        @Test
        @DisplayName("JWT with invalid audience fails when expected audience is set")
        public void testInvalidAudience() {
            Map<String, Object> claims = createValidClaims();
            claims.put("aud", "wrong-app");
            String token = createTestJWT(claims);
            
            JWTValidator.ValidationOptions options = new JWTValidator.ValidationOptions()
                .setExpectedAudience("sentinel-gear-app");
            
            JWTValidationResult result = jwtValidator.validate(token, options);
            
            assertFalse(result.isValid());
            assertTrue(result.getError().isPresent());
        }
        
        @Test
        @DisplayName("JWT with future issue date fails")
        public void testFutureIssuedJWT() {
            Map<String, Object> claims = createValidClaims();
            String token = createFutureIssuedJWT(claims);
            
            JWTValidationResult result = jwtValidator.validate(token);
            
            assertFalse(result.isValid());
            assertTrue(result.getError().isPresent());
        }
        
        @Test
        @DisplayName("Service account detection by prefix")
        public void testServiceAccountDetectionByPrefix() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "sa-my-service-account");
            
            boolean isSA = jwtValidator.isServiceAccount(claims);
            
            assertTrue(isSA);
        }
        
        @Test
        @DisplayName("Service account detection by flag")
        public void testServiceAccountDetectionByFlag() {
            Map<String, Object> claims = createValidClaims();
            claims.put("isServiceAccount", true);
            
            boolean isSA = jwtValidator.isServiceAccount(claims);
            
            assertTrue(isSA);
        }
        
        @Test
        @DisplayName("Regular user not detected as service account")
        public void testRegularUserNotServiceAccount() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "user-regular");
            
            boolean isSA = jwtValidator.isServiceAccount(claims);
            
            assertFalse(isSA);
        }
        
        @Test
        @DisplayName("Realm role extraction")
        public void testRealmRoleExtraction() {
            Map<String, Object> claims = createValidClaims();
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("admin", "user", "viewer"));
            claims.put("realm_access", realmAccess);
            String token = createTestJWT(claims);
            
            JWTValidationResult result = jwtValidator.validate(token);
            List<String> roles = jwtValidator.extractRealmRoles(result.getClaims());
            
            assertEquals(3, roles.size());
            assertTrue(roles.contains("admin"));
            assertTrue(roles.contains("user"));
            assertTrue(roles.contains("viewer"));
        }
        
        @Test
        @DisplayName("Resource role extraction")
        public void testResourceRoleExtraction() {
            Map<String, Object> claims = createValidClaims();
            Map<String, Object> resourceAccess = new HashMap<>();
            Map<String, Object> appRoles = new HashMap<>();
            appRoles.put("roles", Arrays.asList("editor", "manager"));
            resourceAccess.put("my-app", appRoles);
            claims.put("resource_access", resourceAccess);
            String token = createTestJWT(claims);
            
            JWTValidationResult result = jwtValidator.validate(token);
            List<String> roles = jwtValidator.extractResourceRoles(result.getClaims(), "my-app");
            
            assertEquals(2, roles.size());
            assertTrue(roles.contains("editor"));
            assertTrue(roles.contains("manager"));
        }
        
        @Test
        @DisplayName("Missing realm roles returns empty list")
        public void testMissingRealmRoles() {
            Map<String, Object> claims = createValidClaims();
            String token = createTestJWT(claims);
            
            JWTValidationResult result = jwtValidator.validate(token);
            List<String> roles = jwtValidator.extractRealmRoles(result.getClaims());
            
            assertTrue(roles.isEmpty());
        }
        
        @Test
        @DisplayName("Missing resource returns empty list")
        public void testMissingResource() {
            Map<String, Object> claims = createValidClaims();
            String token = createTestJWT(claims);
            
            JWTValidationResult result = jwtValidator.validate(token);
            List<String> roles = jwtValidator.extractResourceRoles(result.getClaims(), "non-existent-app");
            
            assertTrue(roles.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Claim Normalization Tests")
    class ClaimNormalizationTests {
        
        @Test
        @DisplayName("Basic claim normalization")
        public void testBasicNormalization() {
            Map<String, Object> claims = createValidClaims();
            claims.put("preferred_username", "john.doe");
            claims.put("email", "john@example.com");
            claims.put("given_name", "John");
            claims.put("family_name", "Doe");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertNotNull(identity);
            assertEquals("user-123", identity.getUserId());
            assertEquals("john.doe", identity.getUsername());
            assertEquals("John Doe", identity.getFullName());
            assertEquals("john@example.com", identity.getEmail());
        }
        
        @Test
        @DisplayName("Username resolution: preferred_username takes priority")
        public void testUsernameResolutionPriority() {
            Map<String, Object> claims = createValidClaims();
            claims.put("preferred_username", "preferred");
            claims.put("email", "email@example.com");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertEquals("preferred", identity.getUsername());
        }
        
        @Test
        @DisplayName("Username resolution: email as fallback")
        public void testUsernameResolutionEmailFallback() {
            Map<String, Object> claims = createValidClaims();
            claims.put("email", "email@example.com");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertEquals("email@example.com", identity.getUsername());
        }
        
        @Test
        @DisplayName("Username resolution: subject as last resort")
        public void testUsernameResolutionSubjectFallback() {
            Map<String, Object> claims = createValidClaims();
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertEquals("user-123", identity.getUsername());
        }
        
        @Test
        @DisplayName("Full name composition with both names")
        public void testFullNameComposition() {
            Map<String, Object> claims = createValidClaims();
            claims.put("given_name", "Jane");
            claims.put("family_name", "Smith");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertEquals("Jane Smith", identity.getFullName());
        }
        
        @Test
        @DisplayName("Full name with only given name")
        public void testFullNameOnlyGivenName() {
            Map<String, Object> claims = createValidClaims();
            claims.put("given_name", "Jane");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertNotNull(identity.getFullName());
        }
        
        @Test
        @DisplayName("Full name with only family name")
        public void testFullNameOnlyFamilyName() {
            Map<String, Object> claims = createValidClaims();
            claims.put("family_name", "Smith");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertNotNull(identity.getFullName());
        }
        
        @Test
        @DisplayName("Role normalization with realm roles")
        public void testRoleNormalizationRealmRoles() {
            Map<String, Object> claims = createValidClaims();
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("admin", "user"));
            claims.put("realm_access", realmAccess);
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertTrue(identity.getRoles().contains("admin"));
            assertTrue(identity.getRoles().contains("user"));
        }
        
        @Test
        @DisplayName("Role normalization with resource roles")
        public void testRoleNormalizationResourceRoles() {
            Map<String, Object> claims = createValidClaims();
            Map<String, Object> resourceAccess = new HashMap<>();
            Map<String, Object> appRoles = new HashMap<>();
            appRoles.put("roles", Arrays.asList("editor", "publisher"));
            resourceAccess.put("my-app", appRoles);
            claims.put("resource_access", resourceAccess);
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertTrue(identity.getRoles().contains("editor"));
            assertTrue(identity.getRoles().contains("publisher"));
        }
        
        @Test
        @DisplayName("Role normalization combines realm and resource roles")
        public void testRoleNormalizationCombined() {
            Map<String, Object> claims = createValidClaims();
            
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("admin"));
            claims.put("realm_access", realmAccess);
            
            Map<String, Object> resourceAccess = new HashMap<>();
            Map<String, Object> appRoles = new HashMap<>();
            appRoles.put("roles", Arrays.asList("editor"));
            resourceAccess.put("my-app", appRoles);
            claims.put("resource_access", resourceAccess);
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertEquals(2, identity.getRoles().size());
            assertTrue(identity.getRoles().contains("admin"));
            assertTrue(identity.getRoles().contains("editor"));
        }
        
        @Test
        @DisplayName("Tenant extraction from claims")
        public void testTenantExtraction() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertEquals("company-a", identity.getTenant());
        }
        
        @Test
        @DisplayName("Missing tenant defaults to empty")
        public void testMissingTenantDefault() {
            Map<String, Object> claims = createValidClaims();
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertNotNull(identity.getTenant());
        }
        
        @Test
        @DisplayName("Enrichment context capture")
        public void testEnrichmentContextCapture() {
            Map<String, Object> claims = createValidClaims();
            ClaimNormalizer.NormalizationOptions options = new ClaimNormalizer.NormalizationOptions();
            options.setIpAddress("192.168.1.1");
            options.setUserAgent("Mozilla/5.0");
            options.setRequestId("req-123");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, options);
            
            assertEquals("192.168.1.1", identity.getIpAddress());
            assertEquals("Mozilla/5.0", identity.getUserAgent());
            assertEquals("req-123", identity.getRequestId());
        }
        
        @Test
        @DisplayName("Service account flag detection")
        public void testServiceAccountFlagDetection() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "sa-service");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertTrue(identity.isServiceAccount());
        }
        
        @Test
        @DisplayName("Issuer capture from claims")
        public void testIssuerCapture() {
            Map<String, Object> claims = createValidClaims();
            claims.put("iss", "https://auth.example.com");
            
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            assertEquals("https://auth.example.com", identity.getIssuer());
        }
    }
    
    @Nested
    @DisplayName("Tenant Isolation Tests")
    class TenantIsolationTests {
        
        @Test
        @DisplayName("Single tenant mode with matching tenant")
        public void testSingleTenantModeMatch() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            TenantIsolationPolicy.TenantIsolationConfig config = new TenantIsolationPolicy.TenantIsolationConfig();
            config.setMode(TenantIsolationPolicy.TenantMode.SINGLE);
            config.setTenant("company-a");
            
            assertDoesNotThrow(() -> tenantIsolationPolicy.enforceIsolation(identity, config));
        }
        
        @Test
        @DisplayName("Multi tenant mode with valid tenant")
        public void testMultiTenantModeValid() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            TenantIsolationPolicy.TenantIsolationConfig config = new TenantIsolationPolicy.TenantIsolationConfig();
            config.setMode(TenantIsolationPolicy.TenantMode.MULTI);
            
            assertDoesNotThrow(() -> tenantIsolationPolicy.enforceIsolation(identity, config));
        }
        
        @Test
        @DisplayName("Multi tenant mode rejects invalid tenant format")
        public void testMultiTenantModeInvalidFormat() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "invalid tenant!");
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            TenantIsolationPolicy.TenantIsolationConfig config = new TenantIsolationPolicy.TenantIsolationConfig();
            config.setMode(TenantIsolationPolicy.TenantMode.MULTI);
            
            assertThrows(TenantIsolationPolicy.TenantIsolationException.class, 
                () -> tenantIsolationPolicy.enforceIsolation(identity, config));
        }
        
        @Test
        @DisplayName("Tenant identifier validation - valid formats")
        public void testTenantIdentifierValidationValid() {
            assertTrue(tenantIsolationPolicy.isValidTenantIdentifier("valid-tenant"));
            assertTrue(tenantIsolationPolicy.isValidTenantIdentifier("valid_tenant"));
            assertTrue(tenantIsolationPolicy.isValidTenantIdentifier("ValidTenant123"));
            assertTrue(tenantIsolationPolicy.isValidTenantIdentifier("a"));
            assertTrue(tenantIsolationPolicy.isValidTenantIdentifier("tenant-1-2-3"));
        }
        
        @Test
        @DisplayName("Tenant identifier validation - invalid formats")
        public void testTenantIdentifierValidationInvalid() {
            assertFalse(tenantIsolationPolicy.isValidTenantIdentifier("invalid tenant!"));
            assertFalse(tenantIsolationPolicy.isValidTenantIdentifier("tenant@host"));
            assertFalse(tenantIsolationPolicy.isValidTenantIdentifier("tenant#name"));
            assertFalse(tenantIsolationPolicy.isValidTenantIdentifier(""));
            assertFalse(tenantIsolationPolicy.isValidTenantIdentifier(" "));
        }
        
        @Test
        @DisplayName("Multi tenant mode uses default tenant when not provided")
        public void testMultiTenantModeDefaultTenant() {
            Map<String, Object> claims = createValidClaims();
            // Note: ClaimNormalizer sets default tenant to "default" if not provided
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            TenantIsolationPolicy.TenantIsolationConfig config = new TenantIsolationPolicy.TenantIsolationConfig();
            config.setMode(TenantIsolationPolicy.TenantMode.MULTI);
            
            NormalizedIdentity result = assertDoesNotThrow(
                () -> tenantIsolationPolicy.enforceIsolation(identity, config));
            
            assertEquals("default", result.getTenant());
        }
        
        @Test
        @DisplayName("Multi tenant mode auto-assigns tenant when configured")
        public void testMultiTenantModeAutoAssign() {
            Map<String, Object> claims = createValidClaims();
            // Create identity with explicitly null/cleared tenant
            ClaimNormalizer.NormalizationOptions options = new ClaimNormalizer.NormalizationOptions();
            options.setDefaultTenant(""); // Override default tenant to empty
            NormalizedIdentity identity = claimNormalizer.normalize(claims, options);
            // Manually clear the tenant since normalizer might still assign it
            identity.setTenant(null);
            
            TenantIsolationPolicy.TenantIsolationConfig config = new TenantIsolationPolicy.TenantIsolationConfig();
            config.setMode(TenantIsolationPolicy.TenantMode.MULTI);
            config.setAutoAssignTenant("auto-assigned-tenant");
            
            NormalizedIdentity result = assertDoesNotThrow(() -> tenantIsolationPolicy.enforceIsolation(identity, config));
            
            assertEquals("auto-assigned-tenant", result.getTenant());
        }
        
        @Test
        @DisplayName("Single tenant mode overrides identity tenant")
        public void testSingleTenantModeOverrides() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-b");
            NormalizedIdentity identity = claimNormalizer.normalize(claims, new ClaimNormalizer.NormalizationOptions());
            
            TenantIsolationPolicy.TenantIsolationConfig config = new TenantIsolationPolicy.TenantIsolationConfig();
            config.setMode(TenantIsolationPolicy.TenantMode.SINGLE);
            config.setTenant("company-a");
            
            NormalizedIdentity result = tenantIsolationPolicy.enforceIsolation(identity, config);
            
            assertEquals("company-a", result.getTenant());
        }
        
        @Test
        @DisplayName("Tenant identifier with numbers and special chars")
        public void testTenantIdentifierSpecialChars() {
            assertTrue(tenantIsolationPolicy.isValidTenantIdentifier("tenant-123"));
            assertTrue(tenantIsolationPolicy.isValidTenantIdentifier("tenant_456"));
            assertTrue(tenantIsolationPolicy.isValidTenantIdentifier("Tenant-ABC"));
            assertFalse(tenantIsolationPolicy.isValidTenantIdentifier("tenant.123"));
            assertFalse(tenantIsolationPolicy.isValidTenantIdentifier("tenant@123"));
        }
    }
    
    @Nested
    @DisplayName("End-to-End Integration Tests")
    class EndToEndTests {
        
        @Test
        @DisplayName("Complete identity flow with all components")
        public void testCompleteIdentityFlow() {
            Map<String, Object> claims = createValidClaims();
            claims.put("preferred_username", "john");
            claims.put("tenant", "company-a");
            claims.put("given_name", "John");
            claims.put("family_name", "Smith");
            
            String token = createTestJWT(claims);
            
            // Step 1: Validate JWT
            JWTValidationResult validationResult = jwtValidator.validate(token);
            assertTrue(validationResult.isValid());
            
            // Step 2: Normalize claims
            NormalizedIdentity identity = claimNormalizer.normalize(
                validationResult.getClaims(), 
                new ClaimNormalizer.NormalizationOptions()
            );
            assertNotNull(identity);
            assertEquals("john", identity.getUsername());
            assertEquals("John Smith", identity.getFullName());
            
            // Step 3: Apply tenant isolation
            TenantIsolationPolicy.TenantIsolationConfig config = new TenantIsolationPolicy.TenantIsolationConfig();
            config.setMode(TenantIsolationPolicy.TenantMode.SINGLE);
            config.setTenant("company-a");
            
            NormalizedIdentity result = assertDoesNotThrow(() -> 
                tenantIsolationPolicy.enforceIsolation(identity, config)
            );
            assertEquals("company-a", result.getTenant());
        }
        
        @Test
        @DisplayName("Multi-tenant flow with role extraction")
        public void testMultiTenantFlowWithRoles() {
            Map<String, Object> claims = createValidClaims();
            claims.put("preferred_username", "admin");
            claims.put("tenant", "company-x");
            
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("admin", "user"));
            claims.put("realm_access", realmAccess);
            
            Map<String, Object> resourceAccess = new HashMap<>();
            Map<String, Object> appRoles = new HashMap<>();
            appRoles.put("roles", Arrays.asList("manager"));
            resourceAccess.put("my-app", appRoles);
            claims.put("resource_access", resourceAccess);
            
            String token = createTestJWT(claims);
            
            // Validate and normalize
            JWTValidationResult validationResult = jwtValidator.validate(token);
            assertTrue(validationResult.isValid());
            
            NormalizedIdentity identity = claimNormalizer.normalize(
                validationResult.getClaims(), 
                new ClaimNormalizer.NormalizationOptions()
            );
            
            // Verify roles were combined
            assertEquals(3, identity.getRoles().size());
            assertTrue(identity.getRoles().contains("admin"));
            assertTrue(identity.getRoles().contains("user"));
            assertTrue(identity.getRoles().contains("manager"));
            
            // Apply multi-tenant isolation
            TenantIsolationPolicy.TenantIsolationConfig config = new TenantIsolationPolicy.TenantIsolationConfig();
            config.setMode(TenantIsolationPolicy.TenantMode.MULTI);
            
            NormalizedIdentity result = assertDoesNotThrow(() -> 
                tenantIsolationPolicy.enforceIsolation(identity, config)
            );
            assertEquals("company-x", result.getTenant());
        }
        
        @Test
        @DisplayName("Service account with restricted permissions")
        public void testServiceAccountFlow() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "sa-backup-service");
            claims.put("isServiceAccount", true);
            claims.put("tenant", "company-ops");
            
            String token = createTestJWT(claims);
            
            JWTValidationResult validationResult = jwtValidator.validate(token);
            assertTrue(validationResult.isValid());
            
            NormalizedIdentity identity = claimNormalizer.normalize(
                validationResult.getClaims(), 
                new ClaimNormalizer.NormalizationOptions()
            );
            
            assertTrue(identity.isServiceAccount());
            assertEquals("sa-backup-service", identity.getUserId());
            assertEquals("company-ops", identity.getTenant());
        }
        
        @Test
        @DisplayName("Enriched context flow")
        public void testEnrichedContextFlow() {
            Map<String, Object> claims = createValidClaims();
            String token = createTestJWT(claims);
            
            JWTValidationResult validationResult = jwtValidator.validate(token);
            assertTrue(validationResult.isValid());
            
            ClaimNormalizer.NormalizationOptions options = new ClaimNormalizer.NormalizationOptions();
            options.setIpAddress("203.0.113.42");
            options.setUserAgent("curl/7.68.0");
            options.setRequestId("req-abc-123-def");
            
            NormalizedIdentity identity = claimNormalizer.normalize(
                validationResult.getClaims(), 
                options
            );
            
            assertEquals("203.0.113.42", identity.getIpAddress());
            assertEquals("curl/7.68.0", identity.getUserAgent());
            assertEquals("req-abc-123-def", identity.getRequestId());
        }
    }
}
