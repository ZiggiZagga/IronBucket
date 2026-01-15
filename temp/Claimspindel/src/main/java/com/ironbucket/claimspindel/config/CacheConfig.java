package com.ironbucket.claimspindel.config;

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
 * Configures Caffeine-based caching for routing and policy data
 * Reduces repeated evaluations of the same policy rules
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
            "policies",        // Cache policy evaluations
            "routes",          // Cache route decisions
            "claims"           // Cache claim extractions
        );
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10000)  // Max 10k entries
            .expireAfterWrite(5, TimeUnit.MINUTES)  // Expire after 5 minutes
            .recordStats()  // Collect cache statistics
        );
        
        return cacheManager;
    }
}
