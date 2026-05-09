package com.example.tasktracker.emailsender.util;

import com.example.tasktracker.emailsender.config.RateLimitConfig;
import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@TestComponent
public class RpsSupport {
    private final StringRedisTemplate redisTemplate;
    private final Bucket adminBucket;
    private final BucketConfiguration emptyConfig;
    private final BucketConfiguration fullConfig;

    public RpsSupport(StringRedisTemplate redisTemplate,
                      ProxyManager<String> proxyManager,
                      ReliabilityProperties properties) {
        this.redisTemplate = redisTemplate;

        this.emptyConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(1)
                        .refillGreedy(1, Duration.ofHours(1))
                        .initialTokens(0)
                        .id(RateLimitConfig.BANDWIDTH_ID)
                        .build())
                .build();

        long rps = properties.getCapacity().getClusterWideRateLimit();
        this.fullConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rps)
                        .refillGreedy(rps, Duration.ofSeconds(1))
                        .initialTokens(rps)
                        .id(RateLimitConfig.BANDWIDTH_ID)
                        .build())
                .build();

        this.adminBucket = proxyManager.builder().build(RateLimitConfig.RPS_LIMIT_KEY, () -> fullConfig);
    }

    public void forceEmpty() {
        adminBucket.replaceConfiguration(emptyConfig, TokensInheritanceStrategy.RESET);
    }

    public void resetToInit() {
        adminBucket.replaceConfiguration(fullConfig, TokensInheritanceStrategy.RESET);
    }

    public void clear() {
        redisTemplate.delete(RateLimitConfig.RPS_LIMIT_KEY);
    }
}
