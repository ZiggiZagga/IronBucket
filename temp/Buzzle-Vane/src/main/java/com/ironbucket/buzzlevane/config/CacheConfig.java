package com.ironbucket.buzzlevane.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache Configuration
 * 
 * Configures Caffeine-based caching for service discovery and health data
 * Reduces repeated lookups of service instances and health status
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    /**
     * Configure Caffeine cache manager with appropriate expiration times
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "services",        // Cache service registrations
            "health",          // Cache health check results
            "instances"        // Cache service instances
        );
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(5000)  // Max 5k entries (smaller for service registry)
            .expireAfterWrite(1, TimeUnit.MINUTES)  // Expire after 1 minute (shorter for service discovery)
            .recordStats()  // Collect cache statistics
        );
        
        return cacheManager;
    }
}
