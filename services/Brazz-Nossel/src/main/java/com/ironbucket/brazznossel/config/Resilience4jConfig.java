package com.ironbucket.brazznossel.config;

import com.ironbucket.pactumscroll.config.PactumResilienceSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j Configuration
 * 
 * Configures circuit breaker, retry, and timeout policies
 * for resilient service-to-service communication
 */
@Configuration
public class Resilience4jConfig {

    @Bean
    public CircuitBreaker s3ServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return PactumResilienceSupport.buildCircuitBreaker(registry, "s3-service", Duration.ofSeconds(10));
    }

    @Bean
    public Retry s3ServiceRetry(RetryRegistry registry) {
        return PactumResilienceSupport.buildRetry(registry, "s3-service-retry");
    }

    @Bean
    public TimeLimiter s3ServiceTimeLimiter(TimeLimiterRegistry registry) {
        return PactumResilienceSupport.buildTimeLimiter(registry, "s3-service-timeout", Duration.ofSeconds(15));
    }
}
