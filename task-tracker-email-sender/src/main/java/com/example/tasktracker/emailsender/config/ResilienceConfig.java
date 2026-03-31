package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;

@Configuration
@EnableConfigurationProperties(ReliabilityProperties.class)
public class ResilienceConfig {
    public static final String DEFAULT_EMAIL_PROVIDER_ID = "email-provider";
    public static final String EMAIL_CIRCUIT_BREAKER = "emailCircuitBreaker";
    public static final String EMAIL_BULKHEAD = "emailBulkhead";
    public static final String EMAIL_TIME_LIMITER = "emailTimeLimiter";

    @Bean(EMAIL_CIRCUIT_BREAKER)
    public CircuitBreaker emailProviderCircuitBreaker(
            CircuitBreakerRegistry registry,
            ReliabilityProperties properties) {

        int chunkSize = properties.getCapacity().getTokenChunkSize();
        int slidingWindowSize = chunkSize * 5;
        int minCalls = chunkSize * 2;

        var circuitBreakerPolicy = properties.getCircuitBreakerPolicy();

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minCalls)
                .failureRateThreshold(circuitBreakerPolicy.getFailureRateThreshold().floatValue())
                .slowCallDurationThreshold(properties.getNetworkLimit().getSlowCallThreshold())
                .slowCallRateThreshold(circuitBreakerPolicy.getSlowCallRateThreshold().floatValue())
                .waitDurationInOpenState(circuitBreakerPolicy.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(circuitBreakerPolicy.getPermittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreException(this::shouldCBIgnoreFailure)
                .build();

        return registry.circuitBreaker(DEFAULT_EMAIL_PROVIDER_ID, config);
    }

    @Bean(EMAIL_BULKHEAD)
    public Bulkhead emailProviderBulkhead(BulkheadRegistry registry, ReliabilityProperties properties) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.getCapacity().getMaxActiveConnections())
                .maxWaitDuration(properties.getBudget().getMaxBatchProcessingTime())
                .build();

        return registry.bulkhead(DEFAULT_EMAIL_PROVIDER_ID, config);
    }

    @Bean(EMAIL_TIME_LIMITER)
    public TimeLimiter emailProviderTimeLimiter(TimeLimiterRegistry registry, ReliabilityProperties properties) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .cancelRunningFuture(true)
                .timeoutDuration(properties.getNetworkLimit().getExecutionDeadline())
                .build();

        return registry.timeLimiter(DEFAULT_EMAIL_PROVIDER_ID, config);
    }

    @Bean(name = "resilienceScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService resilienceScheduler() {
        return Executors.newScheduledThreadPool(1, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread t = new Thread(r, "resilience-timer-" + counter.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        });
    }

    private boolean shouldCBIgnoreFailure(Throwable throwable) {
        return switch (throwable) {
            case FatalProcessingException ignored -> true;
            case null -> true;
            default -> false;
        };
    }
}
