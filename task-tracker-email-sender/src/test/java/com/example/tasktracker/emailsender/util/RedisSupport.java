package com.example.tasktracker.emailsender.util;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.pipeline.idempotency.TemplateKeyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@TestComponent
@RequiredArgsConstructor
public class RedisSupport {
    private final StringRedisTemplate redisTemplate;
    private final TemplateKeyRegistry keyRegistry;

    public String getStatus(TriggerCommand cmd) {
        return redisTemplate.opsForValue().get(buildKey(cmd));
    }

    public void forceStatus(TriggerCommand cmd, String status) {
        redisTemplate.opsForValue().set(buildKey(cmd), status, Duration.ofMinutes(5));
    }

    private String buildKey(TriggerCommand cmd) {
        return keyRegistry.forType(TemplateType.from(cmd.templateId())).build(cmd);
    }

    public void clear() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}
