package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class RedisIdempotencyCommitter implements IdempotencyCommitter {
    private final StringRedisTemplate redisTemplate;
    private final TemplateKeyRegistry keyRegistry;
    private final ReliabilityProperties properties;

    private static final String STATUS_SENT = "SENT";

    public void commitSuccess(PipelineItem item) {
        if (item.getStatus() != PipelineItem.Status.SENT) return;

        try {
            String key = buildKey(item);
            Duration ttl = resolveTtl(item);
            redisTemplate.opsForValue().set(key, STATUS_SENT, ttl);
            log.debug("Finalized item [{}]. Key: {}", item.getCoordinates(), key);
        } catch (Exception e) {
            log.error("Failed to finalize single item in Redis: [{}].", item.getCoordinates(), e);
        }
    }

    /**
     * Выполняет финализацию успешно обработанных элементов батча в хранилище состояний (Redis).
     * <p>
     * Метод переводит ключи идемпотентности из временного состояния блокировки (PROCESSING) в терминальное
     * состояние (SENT) и устанавливает для них долгосрочный TTL, согласно правилам дедупликации
     * для конкретного типа шаблона.
     * </p>
     *
     * @param batch Пакет элементов конвейера. Обрабатываются только элементы со статусом {@link PipelineItem.Status#SENT}.
     */
    public void commitSuccess(PipelineBatch batch) {
        List<PipelineItem> sentItems = batch.getSentItems();
        if (sentItems.isEmpty()) return;

        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (PipelineItem item : sentItems) {
                    finalizeInConnection(item, ((StringRedisConnection) connection));
                }
                return null;
            });
            log.debug("Finalized batch of {} items.", sentItems.size());
        } catch (Exception e) {
            log.error("Failed to finalize batch in Redis.", e);
        }
    }

    private void finalizeInConnection(PipelineItem item, StringRedisConnection connection) {
        try {
            String key = buildKey(item);
            Duration ttl = resolveTtl(item);
            connection.setEx(key, ttl.toSeconds(), STATUS_SENT);
        } catch (Exception e) {
            log.warn("Skipping finalization for item [{}]: logic failed.", item.getCoordinates());
        }
    }

    private String buildKey(PipelineItem item) {
        return keyRegistry.forType(item.getTemplateType()).build(item.getPayload());
    }

    private Duration resolveTtl(PipelineItem item) {
        return properties.getIdempotency().getTtlFor(item.getTemplateType());
    }
}
