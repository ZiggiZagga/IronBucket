package com.ironbucket.sentinelgear.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
    
    /**
     * Create WebClient with configured timeouts
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .responseTimeout(Duration.ofMillis(READ_TIMEOUT_MS))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)));
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
