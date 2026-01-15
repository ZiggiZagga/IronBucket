package com.ironbucket.sentinelgear.config;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * REST Client Configuration
 * 
 * Configures RestTemplate with connection and read timeouts
 * to prevent hanging on slow external services
 */
@Configuration
public class RestClientConfig {
    
    private static final int CONNECT_TIMEOUT_MS = 5000;   // 5 seconds
    private static final int READ_TIMEOUT_MS = 10000;     // 10 seconds
    private static final int WRITE_TIMEOUT_MS = 10000;    // 10 seconds
    
    /**
     * Create RestTemplate with configured timeouts
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        HttpClient httpClient = HttpClientBuilder
            .create()
            .setMaxConnTotal(200)
            .setMaxConnPerRoute(50)
            .build();
        
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory(httpClient);
        
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        factory.setWriteTimeout(WRITE_TIMEOUT_MS);
        
        return new RestTemplate(factory);
    }
}
