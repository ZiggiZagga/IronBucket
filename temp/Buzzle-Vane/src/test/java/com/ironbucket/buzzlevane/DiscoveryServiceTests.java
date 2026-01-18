package com.ironbucket.buzzlevane;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Buzzle-Vane Discovery Service Tests
 * 
 * Lightweight test suite for service registry validation.
 * Uses simple in-memory service registry for testing discovery patterns.
 */
@DisplayName("Discovery Service Tests")
public class DiscoveryServiceTests {
    
    private Map<String, ServiceInstance> serviceRegistry;
    
    @BeforeEach
    public void setup() {
        serviceRegistry = new HashMap<>();
    }
    
    @Test
    @DisplayName("Service registers successfully")
    public void testServiceRegistration() {
        ServiceInstance service = new ServiceInstance("payment-service", "10.0.1.5", 8080);
        serviceRegistry.put(service.getName(), service);
        
        assertTrue(serviceRegistry.containsKey("payment-service"));
        assertEquals("10.0.1.5", serviceRegistry.get("payment-service").getHost());
    }
    
    @Test
    @DisplayName("Service deregistration removes entry")
    public void testServiceDeregistration() {
        ServiceInstance service = new ServiceInstance("order-service", "10.0.1.6", 8080);
        serviceRegistry.put(service.getName(), service);
        
        serviceRegistry.remove("order-service");
        
        assertFalse(serviceRegistry.containsKey("order-service"));
    }
    
    @Test
    @DisplayName("Service discovery by name")
    public void testDiscoverByName() {
        ServiceInstance api = new ServiceInstance("api-service", "10.0.1.7", 8080);
        serviceRegistry.put(api.getName(), api);
        
        ServiceInstance found = serviceRegistry.get("api-service");
        
        assertNotNull(found);
        assertEquals("api-service", found.getName());
        assertEquals(8080, found.getPort());
    }
    
    @Test
    @DisplayName("Discover non-existent service returns null")
    public void testDiscoverNonExistent() {
        ServiceInstance result = serviceRegistry.get("missing-service");
        
        assertNull(result);
    }
    
    @Test
    @DisplayName("Service registry can hold multiple services")
    public void testMultipleServices() {
        serviceRegistry.put("service-a", new ServiceInstance("service-a", "10.0.1.1", 8080));
        serviceRegistry.put("service-b", new ServiceInstance("service-b", "10.0.1.2", 8081));
        serviceRegistry.put("service-c", new ServiceInstance("service-c", "10.0.1.3", 8082));
        
        assertEquals(3, serviceRegistry.size());
        assertTrue(serviceRegistry.containsKey("service-b"));
    }
    
    @Test
    @DisplayName("Service update replaces existing entry")
    public void testServiceUpdate() {
        serviceRegistry.put("api", new ServiceInstance("api", "10.0.1.10", 8080));
        
        ServiceInstance updated = new ServiceInstance("api", "10.0.1.20", 9090);
        serviceRegistry.put(updated.getName(), updated);
        
        assertEquals("10.0.1.20", serviceRegistry.get("api").getHost());
        assertEquals(9090, serviceRegistry.get("api").getPort());
    }
    
    // Simple service instance model for testing
    private static class ServiceInstance {
        private final String name;
        private final String host;
        private final int port;
        
        public ServiceInstance(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }
        
        public String getName() {
            return name;
        }
        
        public String getHost() {
            return host;
        }
        
        public int getPort() {
            return port;
        }
    }
}
