package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.ContainerizedIntegrationTest;
import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("ci")
class RedisIdempotencyGuardIT extends ContainerizedIntegrationTest {

    @Autowired
    private IdempotencyGuard guard;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TemplateKeyRegistry registry;

    @Test
    @DisplayName("Should handle real business keys and Redis Lua logic")
    void shouldHandleRealIdempotencyFlow() {
        var welcomeItem = createItem(TemplateType.USER_WELCOME, 100L, "topic-welcome", 1L);
        var reportItem = createItem(TemplateType.DAILY_TASK_REPORT, 100L, "topic-report", 2L, "2025-01-01");

        String welcomeKey = registry.forType(TemplateType.USER_WELCOME).build(welcomeItem.getPayload());
        String reportKey = registry.forType(TemplateType.DAILY_TASK_REPORT).build(reportItem.getPayload());

        guard.checkAndLock(new PipelineBatch(List.of(welcomeItem, reportItem)));

        assertTrue(welcomeItem.getStage().isPending());
        assertTrue(reportItem.getStage().isPending());

        assertNotNull(redisTemplate.opsForValue().get(welcomeKey));
        assertNotNull(redisTemplate.opsForValue().get(reportKey));

        // 2. Имитируем успешную отправку первого письма (Finalization)
        redisTemplate.opsForValue().set(welcomeKey, "SENT", Duration.ofHours(1));

        // 3. Повторный проход того же батча (например, ретрай Kafka)
        // Обнуляем статус для теста
        var retryWelcome = createItem(TemplateType.USER_WELCOME, 100L, "topic-welcome", 1L);

        guard.checkAndLock(retryWelcome);

        // Результат: Welcome уже отправлен (SENT), поэтому SKIPPED.
        assertEquals(PipelineItem.Status.SKIPPED, retryWelcome.getStage().status());
        assertEquals(RejectReason.DUPLICATE, retryWelcome.getStage().rejectReason());
    }

    @Test
    @DisplayName("Fencing: Should block concurrent processing of the same business event")
    void shouldBlockConcurrentProcessing() {
        var item1 = createItem(TemplateType.USER_WELCOME, 555L, "t", 10L);
        String key = registry.forType(TemplateType.USER_WELCOME).build(item1.getPayload());

        // Имитируем другой инстанс/воркер
        redisTemplate.opsForValue().set(key, "other-topic@0|other-instance", Duration.ofMinutes(1));

        guard.checkAndLock(item1);

        // Результат: Конфликт блокировки (PROCESSING)
        assertEquals(PipelineItem.Status.RETRY, item1.getStage().status());
        assertEquals(RejectReason.CONCURRENT_LOCK, item1.getStage().rejectReason());
    }

    private PipelineItem createItem(TemplateType type, Long userId, String topic, long offset, String... date) {
        var record = TestKafkaConsumerRecordFactory.record().topic(topic).offset(offset).build();
        PipelineItem item = new PipelineItem(record);
        item.setTemplateType(type);

        Map<String, Object> context = new HashMap<>();
        if (date.length > 0) context.put("reportDate", date[0]);

        item.setPayload(new TriggerCommand("test@test.com", type.name(), context, "en", userId, "c-id"));
        return item;
    }
}