package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.ContainerizedIntegrationTest;
import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class IdempotencyLifecycleIT extends ContainerizedIntegrationTest {

    @Autowired
    private IdempotencyGuard guard;

    @Autowired
    private RedisIdempotencyCommitter committer;

    @Autowired
    private TemplateKeyRegistry registry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("Lifecycle: Guard and Committer must work on the exact same Redis key")
    void shouldMaintainKeyConsistencyAcrossLifecycle() {
        // Given: Один и тот же бизнес-ивент
        var item = createItem(TemplateType.USER_WELCOME, 123L, "topic-a", 100L);
        String businessKey = registry.forType(item.getTemplateType()).build(item.getPayload());

        // 1. PHASE: GUARD (Locking)
        guard.checkAndLock(item);

        assertTrue(item.getStage().isPending(), "Guard should allow processing for the first time");

        // Проверяем, что в Redis создался лок с fencing-токеном (Lease)
        String leaseValue = redisTemplate.opsForValue().get(businessKey);
        assertNotNull(leaseValue, "Guard must create a key in Redis");
        assertTrue(leaseValue.contains(item.getCoordinates()), "Lease must contain message coordinates");

        // 2. PHASE: PROCESSING (Simulate successful delivery)
        item.tryMarkAsSent(); // Переводим в терминальный статус успеха

        // 3. PHASE: COMMITTER (Finalization)
        committer.commitSuccess(item);

        // 4. VERIFICATION: CONTRACT INTEGRITY
        // Committer должен был обновить Тот Же Самый Ключ, а не создать новый
        String finalValue = redisTemplate.opsForValue().get(businessKey);
        assertEquals("SENT", finalValue, "Committer must update the existing key to SENT status");

        // 5. PHASE: DEDUPLICATION (Second attempt)
        var duplicateItem = createItem(TemplateType.USER_WELCOME, 123L, "topic-a", 101L); // Тот же юзер, другой оффсет
        guard.checkAndLock(duplicateItem);

        assertEquals(PipelineItem.Status.SKIPPED, duplicateItem.getStage().status(),
                "Guard must skip the item because Committer finalized the key");
    }

    private PipelineItem createItem(TemplateType type, Long userId, String topic, long offset) {
        var record = TestKafkaConsumerRecordFactory.record().topic(topic).offset(offset).build();
        PipelineItem item = new PipelineItem(record);
        item.setTemplateType(type);
        item.setPayload(new TriggerCommand("u@t.com", type.name(), Map.of(), "en", userId, "c-id"));
        return item;
    }
}
