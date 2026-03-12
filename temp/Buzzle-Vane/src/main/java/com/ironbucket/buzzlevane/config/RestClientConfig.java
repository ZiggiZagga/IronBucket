package com.ironbucket.buzzlevane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * REST Client Configuration
 * 
 * Configures RestTemplate with sane defaults.
 * Uses RestTemplate (blocking client) suitable for Eureka Server.
 */
@Configuration
public class RestClientConfig {
    
    /**
     * Create RestTemplate with default configuration
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
