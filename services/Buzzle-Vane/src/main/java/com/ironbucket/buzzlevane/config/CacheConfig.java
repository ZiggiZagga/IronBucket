package com.ironbucket.buzzlevane.config;

import com.ironbucket.pactumscroll.config.PactumCacheSupport;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache Configuration
 * 
 * Configures Caffeine-based caching for service discovery and health data
 * Reduces repeated lookups of service instances and health status
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return PactumCacheSupport.buildCacheManager(
            5000,
            Duration.ofMinutes(1),
            "services",        // Cache service registrations
            "health",          // Cache health check results
            "instances"        // Cache service instances
        );
    }
}
