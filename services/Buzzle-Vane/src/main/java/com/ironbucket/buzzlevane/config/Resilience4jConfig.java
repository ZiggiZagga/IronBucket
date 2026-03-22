package com.ironbucket.buzzlevane.config;

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
    public CircuitBreaker eurekaCircuitBreaker(CircuitBreakerRegistry registry) {
        return PactumResilienceSupport.buildCircuitBreaker(registry, "eureka", Duration.ofSeconds(5));
    }

    @Bean
    public Retry eurekaRetry(RetryRegistry registry) {
        return PactumResilienceSupport.buildRetry(registry, "eureka-retry");
    }

    @Bean
    public TimeLimiter eurekaTimeLimiter(TimeLimiterRegistry registry) {
        return PactumResilienceSupport.buildTimeLimiter(registry, "eureka-timeout", Duration.ofSeconds(10));
    }
}
