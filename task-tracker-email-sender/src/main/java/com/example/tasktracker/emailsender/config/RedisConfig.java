package com.example.tasktracker.emailsender.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

@Configuration
public class RedisConfig {

    @Bean(name = "rateLimitRedisClient", destroyMethod = "close")
    public RedisClient rateLimitRedisClient(RedisConnectionDetails details) {
        var standalone = details.getStandalone();
        if (standalone == null) {
            throw new IllegalStateException("Rate limiting requires standalone Redis configuration.");
        }

        RedisURI.Builder builder = RedisURI.builder()
                .withHost(standalone.getHost())
                .withPort(standalone.getPort());

        String password = details.getPassword();
        if (password != null && !password.isBlank()) {
            builder.withPassword(password);
        }

        return RedisClient.create(builder.build());
    }

    @Bean("rateLimitProxyManager")
    public ProxyManager<String> lettuceProxyManager(@Qualifier("rateLimitRedisClient") RedisClient redisClient) {
        StatefulRedisConnection<String, byte[]> connection = redisClient
                .connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return Bucket4jLettuce.casBasedBuilder(connection.async())
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1))
                )
                .requestTimeout(Duration.ofSeconds(3)) //TimeoutException
                .build();
    }

    @Bean("batchIdempotencyScript")
    public RedisScript<List<String>> idempotencyScript() {
        DefaultRedisScript<List<String>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/acquire_batch_locks.lua"));
        script.setResultType((Class<List<String>>) (Class<?>) List.class);
        return script;
    }
}
