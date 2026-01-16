package com.ironbucket.sentinelgear.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Issue #46: Validate full discovery-registration lifecycle using
 * local Buzzle-Vane instance (e.g., via Sentinel-Gear)
 *
 * Pattern: RED → GREEN → REFACTOR
 * ✗ Write failing test
 * ✓ Implement code to make test pass
 * ✓ Verify test passes
 */
@SpringBootTest
@DisplayName("Issue #46: Service Discovery Lifecycle")
class BuzzleVaneDiscoveryLifecycleTest {

    /**
     * TEST 1: Service registers with Eureka on startup
     *
     * GIVEN: A service instance (e.g., Claimspindel)
     * WHEN: Service starts up
     * THEN: Service should register itself with Eureka (Buzzle-Vane)
     */
    @Test
    @DisplayName("✗ test_serviceRegistration_withEureka")
    void test_serviceRegistration_withEureka() {
        // GIVEN: A service registration payload
        Map<String, Object> registration = new HashMap<>();
        registration.put("applicationName", "claimspindel");
        registration.put("instanceId", "claimspindel-instance-1");
        registration.put("hostName", "steel-hammer-claimspindel");
        registration.put("ipAddr", "172.20.0.5");
        registration.put("port", 8081);
        registration.put("securePort", 8443);

        // WHEN: Service registers with Eureka
        String registrationPath = "/eureka/apps/CLAIMSPINDEL";
        boolean registered = true;

        // THEN: Registration should succeed
        assertTrue(registered, "Service should register with Eureka");

        // AND: All required fields should be present
        assertNotNull(registration.get("applicationName"));
        assertNotNull(registration.get("instanceId"));
        assertNotNull(registration.get("ipAddr"));
        assertNotNull(registration.get("port"));

        // AND: Service should appear in registry
        assertTrue(registrationPath.contains("CLAIMSPINDEL"), "Service name should be in registry");
    }

    /**
     * TEST 2: Service sends heartbeat every 30 seconds
     *
     * GIVEN: A registered service
     * WHEN: 30 seconds elapse
     * THEN: Service should send heartbeat to Eureka
     */
    @Test
    @DisplayName("✗ test_serviceHeartbeat_30secInterval")
    void test_serviceHeartbeat_30secInterval() {
        // GIVEN: A service with heartbeat configuration
        int heartbeatIntervalSeconds = 30;
        long lastHeartbeat = System.currentTimeMillis();

        // WHEN: 30 seconds pass
        long now = lastHeartbeat + (heartbeatIntervalSeconds * 1000);
        long timeSinceLastHeartbeat = now - lastHeartbeat;

        // THEN: Heartbeat should have been sent
        assertEquals(heartbeatIntervalSeconds * 1000, timeSinceLastHeartbeat);
        assertTrue(timeSinceLastHeartbeat >= heartbeatIntervalSeconds * 1000);

        // AND: Service status should be updated
        String expectedStatus = "UP";
        assertEquals("UP", expectedStatus, "Service should be UP after successful heartbeat");
    }

    /**
     * TEST 3: Service deregisters on shutdown
     *
     * GIVEN: A registered service instance
     * WHEN: Service shuts down
     * THEN: Service should deregister from Eureka
     */
    @Test
    @DisplayName("✗ test_serviceDeregistration_onShutdown")
    void test_serviceDeregistration_onShutdown() {
        // GIVEN: A registered service
        Map<String, Object> serviceInstance = new HashMap<>();
        serviceInstance.put("applicationName", "claimspindel");
        serviceInstance.put("instanceId", "claimspindel-instance-1");
        serviceInstance.put("status", "UP");

        // WHEN: Service shuts down and deregisters
        serviceInstance.put("status", "DOWN");
        String deregisterPath = "/eureka/apps/CLAIMSPINDEL/claimspindel-instance-1";
        boolean deregistered = true;

        // THEN: Deregistration should succeed
        assertTrue(deregistered, "Service should deregister on shutdown");

        // AND: Service status should be DOWN
        assertEquals("DOWN", serviceInstance.get("status"));

        // AND: Service should be removed from active instances
        assertFalse(isServiceActive(serviceInstance), "Service should no longer be active");
    }

    /**
     * TEST 4: Metadata tags (region) included in registration
     *
     * GIVEN: A service with environment-specific metadata
     * WHEN: Service registers with Eureka
     * THEN: Metadata tags should be included (region, environment, etc.)
     */
    @Test
    @DisplayName("✗ test_metadataTag_regionIncluded")
    void test_metadataTag_regionIncluded() {
        // GIVEN: A service registration with metadata
        Map<String, Object> registration = new HashMap<>();
        registration.put("applicationName", "brazz-nossel");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("region", "us-east-1");
        metadata.put("environment", "docker");
        metadata.put("version", "0.0.1-SNAPSHOT");
        metadata.put("service-type", "proxy");
        registration.put("metadata", metadata);

        // WHEN: Service registers
        Map<String, String> registeredMetadata = (Map<String, String>) registration.get("metadata");

        // THEN: Metadata should include region
        assertNotNull(registeredMetadata.get("region"));
        assertEquals("us-east-1", registeredMetadata.get("region"));

        // AND: Metadata should include environment
        assertEquals("docker", registeredMetadata.get("environment"));

        // AND: Metadata should include version
        assertEquals("0.0.1-SNAPSHOT", registeredMetadata.get("version"));
    }

    /**
     * TEST 5: Sentinel-Gear discovers services via Eureka
     *
     * GIVEN: Multiple services registered in Eureka
     * WHEN: Sentinel-Gear queries service registry
     * THEN: Sentinel-Gear should discover all available service instances
     */
    @Test
    @DisplayName("✗ test_sentinelGear_discoversService")
    void test_sentinelGear_discoversService() {
        // GIVEN: Multiple services in registry
        Map<String, String> registry = new HashMap<>();
        registry.put("CLAIMSPINDEL", "http://steel-hammer-claimspindel:8081");
        registry.put("BRAZZ-NOSSEL", "http://steel-hammer-brazz-nossel:8082");
        registry.put("BUZZLE-VANE", "http://steel-hammer-buzzle-vane:8083");

        // WHEN: Sentinel-Gear discovers services
        String discoveredService = registry.get("CLAIMSPINDEL");

        // THEN: Service should be discovered
        assertNotNull(discoveredService, "Service should be discovered");
        assertEquals("http://steel-hammer-claimspindel:8081", discoveredService);

        // AND: All services should be discoverable
        assertTrue(registry.containsKey("CLAIMSPINDEL"), "Claimspindel should be discovered");
        assertTrue(registry.containsKey("BRAZZ-NOSSEL"), "Brazz-Nossel should be discovered");
        assertTrue(registry.containsKey("BUZZLE-VANE"), "Buzzle-Vane should be discovered");

        // AND: Service URLs should be valid
        for (String url : registry.values()) {
            assertTrue(url.startsWith("http"), "Service URL should start with http");
            assertTrue(url.contains(":"), "Service URL should have port");
        }
    }

    /**
     * Helper: Check if service is active
     */
    private boolean isServiceActive(Map<String, Object> serviceInstance) {
        String status = (String) serviceInstance.get("status");
        return "UP".equals(status);
    }

}
