package com.ironbucket.brazznossel.config;

import com.ironbucket.pactumscroll.config.PactumCacheSupport;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache Configuration
 * 
 * Configures Caffeine-based caching for S3 metadata and policy data
 * Reduces repeated S3 lookups and policy evaluations
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return PactumCacheSupport.buildCacheManager(
            10000,
            Duration.ofMinutes(5),
            "s3-metadata",     // Cache S3 object metadata
            "policies",        // Cache policy evaluations
            "permissions"      // Cache permission checks
        );
    }
}
