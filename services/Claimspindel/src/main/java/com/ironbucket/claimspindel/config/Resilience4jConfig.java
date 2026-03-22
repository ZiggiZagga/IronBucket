package com.ironbucket.claimspindel.config;

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
    public CircuitBreaker brazzNosselCircuitBreaker(CircuitBreakerRegistry registry) {
        return PactumResilienceSupport.buildCircuitBreaker(registry, "brazz-nossel", Duration.ofSeconds(10));
    }

    @Bean
    public Retry brazzNosselRetry(RetryRegistry registry) {
        return PactumResilienceSupport.buildRetry(registry, "brazz-nossel-retry");
    }

    @Bean
    public TimeLimiter brazzNosselTimeLimiter(TimeLimiterRegistry registry) {
        return PactumResilienceSupport.buildTimeLimiter(registry, "brazz-nossel-timeout", Duration.ofSeconds(10));
    }
}
