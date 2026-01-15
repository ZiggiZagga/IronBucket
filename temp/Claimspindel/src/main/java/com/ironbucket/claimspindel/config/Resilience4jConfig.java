package com.ironbucket.claimspindel.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j Configuration
 * 
 * Configures circuit breaker, retry, and timeout policies
 * for resilient service-to-service communication
 */
@Configuration
public class Resilience4jConfig {
    
    /**
     * Circuit Breaker Configuration for Brazz-Nossel calls
     * Opens circuit after 5 failures
     */
    @Bean
    public CircuitBreaker brazzNosselCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .recordExceptions(Exception.class)
            .failureRateThreshold(50.0f)  // 50% failure rate
            .slowCallRateThreshold(50.0f)  // 50% slow calls
            .slowCallDurationThreshold(Duration.ofSeconds(10))  // Calls > 10s are slow
            .waitDurationInOpenState(Duration.ofSeconds(30))  // Wait 30s before retry
            .permittedNumberOfCallsInHalfOpenState(3)  // Try 3 calls in half-open state
            .slidingWindowSize(100)  // Track last 100 calls
            .build();
        
        return registry.circuitBreaker("brazz-nossel", config);
    }
    
    /**
     * Retry Configuration
     * Retries with exponential backoff: 1s, 2s, 4s
     */
    @Bean
    public Retry brazzNosselRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .retryOnException(e -> !(e instanceof IllegalArgumentException))
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(1000, 2))
            .build();
        
        return registry.retry("brazz-nossel-retry", config);
    }
    
    /**
     * Time Limiter Configuration
     * Fails if operation takes > 10 seconds
     */
    @Bean
    public TimeLimiter brazzNosselTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(10))
            .cancelRunningFuture(true)
            .build();
        
        return registry.timeLimiter("brazz-nossel-timeout", config);
    }
}
