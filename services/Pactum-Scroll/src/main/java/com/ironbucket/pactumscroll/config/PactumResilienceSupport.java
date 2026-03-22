package com.ironbucket.pactumscroll.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;

public final class PactumResilienceSupport {

    private PactumResilienceSupport() {
    }

    public static CircuitBreaker buildCircuitBreaker(
        CircuitBreakerRegistry registry,
        String name,
        Duration slowCallDuration
    ) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .recordExceptions(Exception.class)
            .failureRateThreshold(50.0f)
            .slowCallRateThreshold(50.0f)
            .slowCallDurationThreshold(slowCallDuration)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(100)
            .build();
        return registry.circuitBreaker(name, config);
    }

    public static Retry buildRetry(RetryRegistry registry, String name) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .retryOnException(e -> !(e instanceof IllegalArgumentException))
            .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2))
            .build();
        return registry.retry(name, config);
    }

    public static TimeLimiter buildTimeLimiter(
        TimeLimiterRegistry registry,
        String name,
        Duration timeout
    ) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(timeout)
            .cancelRunningFuture(true)
            .build();
        return registry.timeLimiter(name, config);
    }
}
