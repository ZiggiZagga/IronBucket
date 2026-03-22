package com.ironbucket.claimspindel.config;

import com.ironbucket.pactumscroll.config.PactumWebClientSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * REST Client Configuration
 * 
 * Configures WebClient with connection and read timeouts
 * to prevent hanging on slow external services.
 * Uses Reactor Netty for non-blocking HTTP communication.
 */
@Configuration
public class RestClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 5000;   // 5 seconds
    private static final int READ_TIMEOUT_MS = 10000;     // 10 seconds
    private static final int WRITE_TIMEOUT_MS = 10000;    // 10 seconds

    @Bean
    public WebClient webClient() {
        return PactumWebClientSupport.buildWebClient(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, WRITE_TIMEOUT_MS);
    }
}
