package com.example.tasktracker.emailsender.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BlockingBucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

@Configuration
public class RateLimitConfig {

    public static final String RPS_LIMIT_KEY = "email:ratelimit:global_rps";

    @Bean
    public BlockingBucket rpsBucket(@Qualifier("rateLimitProxyManager") ProxyManager<String> proxyManager,
                                    EmailSenderProperties properties) {
        long rps = properties.getRateLimit().getGlobalRps();

        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(rps)
                .refillIntervally(rps, Duration.ofSeconds(1))
                .initialTokens(rps)
                .build();

        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(bandwidth)
                .build();

        return proxyManager.builder()
                .build(RPS_LIMIT_KEY, configSupplier)
                .asBlocking();
    }
}
