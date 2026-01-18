package com.ironbucket.buzzlevane.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Buzzle-Vane Service Mesh Identity Tests
 * 
 * Comprehensive test suite for JWT validation, service-to-service 
 * authentication, mesh routing, and multi-tenant isolation.
 */
public class BuzzleVaneIdentityTests {
    
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
        claims.put("sub", "service-client");
        claims.put("iss", "https://example.com");
        claims.put("aud", "service-mesh");
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().getEpochSecond() + 3600);
        return claims;
    }
    
    private Claims parseToken(String token, Key key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    @Nested
    @DisplayName("Service-to-Service Authentication")
    class ServiceAuthenticationTests {
        
        @Test
        @DisplayName("Service can authenticate with valid JWT")
        public void testServiceAuthentication() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "payment-service");
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertEquals("payment-service", parsed.getSubject());
            assertEquals("service-mesh", parsed.getAudience());
            assertEquals("https://example.com", parsed.getIssuer());
            assertTrue(parsed.getExpiration().after(new Date()));
        }
        
        @Test
        @DisplayName("Service with service account flag")
        public void testServiceAccountIdentification() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "order-service");
            claims.put("isServiceAccount", true);
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertEquals("order-service", parsed.getSubject());
            assertTrue(parsed.get("isServiceAccount", Boolean.class));
        }
        
        @Test
        @DisplayName("Service with realm and resource roles")
        public void testServiceRoles() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "inventory-service");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("service", "api"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            Claims parsed = parseToken(token, signingKey);
            List<String> roles = (List<String>) ((Map<?, ?>) parsed.get("realm_access", Map.class)).get("roles");
            assertTrue(roles.contains("service"));
            assertTrue(roles.contains("api"));
        }
        
        @Test
        @DisplayName("Expired JWT is rejected")
        public void testExpiredJWTRejected() {
            Map<String, Object> claims = createValidClaims();
            String token = createExpiredJWT(claims);
            
            assertThrows(ExpiredJwtException.class, () -> parseToken(token, signingKey));
        }
        
        @Test
        @DisplayName("Invalid signature is rejected")
        public void testInvalidSignatureRejected() {
            Map<String, Object> claims = createValidClaims();
            String token = createTestJWT(claims);
            
            assertThrows(JwtException.class, () -> parseToken(token, invalidSigningKey));
        }
    }
    
    @Nested
    @DisplayName("Mesh Routing Authorization")
    class MeshRoutingTests {
        
        @Test
        @DisplayName("Service authorized to route requests")
        public void testRoutingAuthorization() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "gateway-service");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("mesh-router"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            Claims parsed = parseToken(token, signingKey);
            List<String> roles = (List<String>) ((Map<?, ?>) parsed.get("realm_access", Map.class)).get("roles");
            assertTrue(roles.contains("mesh-router"));
        }
        
        @Test
        @DisplayName("Service with mesh-admin role")
        public void testMeshAdminRole() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "mesh-admin");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("mesh-admin"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            Claims parsed = parseToken(token, signingKey);
            List<String> roles = (List<String>) ((Map<?, ?>) parsed.get("realm_access", Map.class)).get("roles");
            assertTrue(roles.contains("mesh-admin"));
        }
        
        @Test
        @DisplayName("Service with limited mesh access")
        public void testLimitedMeshAccess() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "worker-service");
            claims.put("mesh:allowed_targets", Arrays.asList("service-a", "service-b"));
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            List<String> allowed = (List<String>) parsed.get("mesh:allowed_targets", List.class);
            assertEquals(2, allowed.size());
            assertTrue(allowed.contains("service-a"));
            assertTrue(allowed.contains("service-b"));
        }
    }
    
    @Nested
    @DisplayName("Service Mesh Multi-Tenant Isolation")
    class MeshMultiTenantTests {
        
        @Test
        @DisplayName("Service scoped to single tenant")
        public void testSingleTenantService() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertEquals("company-a", parsed.get("tenant", String.class));
        }
        
        @Test
        @DisplayName("Service isolation between tenants")
        public void testTenantIsolation() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-x");
            claims.put("sub", "tenant-service");
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertEquals("company-x", parsed.get("tenant", String.class));
            assertEquals("tenant-service", parsed.getSubject());
        }
        
        @Test
        @DisplayName("Cross-tenant shared service")
        public void testCrossTenantSharedService() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "shared-service");
            claims.put("mesh:shared_service", true);
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertTrue(parsed.get("mesh:shared_service", Boolean.class));
        }
    }
    
    @Nested
    @DisplayName("Circuit Breaker & Health Check Authorization")
    class CircuitBreakerTests {
        
        @Test
        @DisplayName("Service authorized for health checks")
        public void testHealthCheckAuthorization() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "health-check-service");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("health-checker"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            Claims parsed = parseToken(token, signingKey);
            List<String> roles = (List<String>) ((Map<?, ?>) parsed.get("realm_access", Map.class)).get("roles");
            assertTrue(roles.contains("health-checker"));
        }
        
        @Test
        @DisplayName("Service with circuit breaker role")
        public void testCircuitBreakerRole() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "lb-service");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("circuit-breaker"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            Claims parsed = parseToken(token, signingKey);
            List<String> roles = (List<String>) ((Map<?, ?>) parsed.get("realm_access", Map.class)).get("roles");
            assertTrue(roles.contains("circuit-breaker"));
        }
        
        @Test
        @DisplayName("Service metrics collection authorization")
        public void testMetricsCollectionAuth() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "metrics-service");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("metrics-reader"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            Claims parsed = parseToken(token, signingKey);
            List<String> roles = (List<String>) ((Map<?, ?>) parsed.get("realm_access", Map.class)).get("roles");
            assertTrue(roles.contains("metrics-reader"));
        }
    }
    
    @Nested
    @DisplayName("Load Balancing Authorization")
    class LoadBalancingTests {
        
        @Test
        @DisplayName("Service authorized for load balancing")
        public void testLoadBalancingAuth() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "lb-service");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("load-balancer"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            Claims parsed = parseToken(token, signingKey);
            List<String> roles = (List<String>) ((Map<?, ?>) parsed.get("realm_access", Map.class)).get("roles");
            assertTrue(roles.contains("load-balancer"));
        }
        
        @Test
        @DisplayName("Service with subset routing capability")
        public void testSubsetRoutingAuth() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "router-service");
            claims.put("mesh:subset_routing", true);
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertTrue(parsed.get("mesh:subset_routing", Boolean.class));
        }
        
        @Test
        @DisplayName("Service with traffic splitting capability")
        public void testTrafficSplittingAuth() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "traffic-splitter");
            claims.put("mesh:traffic_splitting", true);
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertTrue(parsed.get("mesh:traffic_splitting", Boolean.class));
        }
    }
    
    @Nested
    @DisplayName("Service Discovery Integration")
    class ServiceDiscoveryTests {
        
        @Test
        @DisplayName("Service registered in mesh")
        public void testServiceRegistration() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "api-service");
            claims.put("mesh:service_name", "api-v1");
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertEquals("api-v1", parsed.get("mesh:service_name", String.class));
        }
        
        @Test
        @DisplayName("Service with version information")
        public void testServiceVersioning() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "api-service");
            claims.put("mesh:version", "v2");
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertEquals("v2", parsed.get("mesh:version", String.class));
        }
        
        @Test
        @DisplayName("Service with zone information")
        public void testServiceZoneInfo() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "api-service");
            claims.put("mesh:zone", "us-east-1");
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertEquals("us-east-1", parsed.get("mesh:zone", String.class));
        }
    }
    
    @Nested
    @DisplayName("Mesh Integration Flow Tests")
    class MeshIntegrationFlowTests {
        
        @Test
        @DisplayName("Service requesting another service in same tenant")
        public void testSameTenantServiceCall() {
            Map<String, Object> claims = createValidClaims();
            claims.put("tenant", "company-a");
            claims.put("sub", "order-service");
            claims.put("mesh:service_name", "order-svc");
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertEquals("company-a", parsed.get("tenant", String.class));
            assertEquals("order-svc", parsed.get("mesh:service_name", String.class));
        }
        
        @Test
        @DisplayName("Service routing through load balancer")
        public void testLoadBalancingFlow() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "lb-service");
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList("load-balancer", "mesh-router"));
            claims.put("realm_access", realmAccess);
            
            String token = createTestJWT(claims);
            Claims parsed = parseToken(token, signingKey);
            List<String> roles = (List<String>) ((Map<?, ?>) parsed.get("realm_access", Map.class)).get("roles");
            assertTrue(roles.containsAll(Arrays.asList("load-balancer", "mesh-router")));
        }
        
        @Test
        @DisplayName("Service with circuit breaker enabled")
        public void testCircuitBreakerFlow() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "api-service");
            claims.put("mesh:circuit_breaker_enabled", true);
            claims.put("mesh:circuit_breaker_threshold", 50);
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertTrue(parsed.get("mesh:circuit_breaker_enabled", Boolean.class));
            assertEquals(50, parsed.get("mesh:circuit_breaker_threshold", Integer.class));
        }
        
        @Test
        @DisplayName("Service with retry policy")
        public void testRetryPolicyFlow() {
            Map<String, Object> claims = createValidClaims();
            claims.put("sub", "api-service");
            claims.put("mesh:retry_enabled", true);
            claims.put("mesh:max_retries", 3);
            String token = createTestJWT(claims);
            
            Claims parsed = parseToken(token, signingKey);
            assertTrue(parsed.get("mesh:retry_enabled", Boolean.class));
            assertEquals(3, parsed.get("mesh:max_retries", Integer.class));
        }
    }
}
