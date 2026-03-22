package com.ironbucket.claimspindel.config;

import com.ironbucket.pactumscroll.config.PactumCacheSupport;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache Configuration
 * 
 * Configures Caffeine-based caching for routing and policy data
 * Reduces repeated evaluations of the same policy rules
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return PactumCacheSupport.buildCacheManager(
            10000,
            Duration.ofMinutes(5),
            "policies",        // Cache policy evaluations
            "routes",          // Cache route decisions
            "claims"           // Cache claim extractions
        );
    }
}
