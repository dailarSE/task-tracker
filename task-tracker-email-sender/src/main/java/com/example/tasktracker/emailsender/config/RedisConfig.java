package com.example.tasktracker.emailsender.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean("batchIdempotencyScript")
    public RedisScript<List<String>> idempotencyScript() {
        DefaultRedisScript<List<String>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/acquire_batch_locks.lua"));
        script.setResultType((Class<List<String>>) (Class<?>) List.class);
        return script;
    }
}
