package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import com.example.tasktracker.emailsender.exception.infrastructure.StateStoreInfrastructureException;
import com.example.tasktracker.emailsender.infra.RuntimeInstanceIdProvider;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisIdempotencyGuardTest {

    private static final String INSTANCE_ID = "test-inst";
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);
    private static final String STATUS_SENT = "SENT";

    private final TemplateKeyRegistry registry = new TemplateKeyRegistry(List.of(
            new UserWelcomeKeyBuilder(),
            new DailyReportKeyBuilder()
    ));

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RuntimeInstanceIdProvider idProvider = () -> INSTANCE_ID;

    @SuppressWarnings("unchecked")
    private final RedisScript<List<String>> script = mock(RedisScript.class);

    private RedisIdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        ReliabilityProperties props = new ReliabilityProperties();
        props.getIdempotency().setProcessingLockDuration(LOCK_TTL);
        guard = new RedisIdempotencyGuard(props, redisTemplate, registry, idProvider, script);
    }

    @Test
    @DisplayName("Should set SKIPPED status when Redis returns SENT")
    void shouldMapSentStatus() {
        var item = createItem(TemplateType.USER_WELCOME, 123L, "topic-a", 100L, Map.of());

        String expectedKey = registry.forType(item.getTemplateType()).build(item.getPayload());
        String expectedLease = item.getCoordinates() + "|" + INSTANCE_ID;
        String expectedTtl = String.valueOf(LOCK_TTL.toSeconds());

        when(redisTemplate.execute(eq(script), anyList(), any(Object[].class)))
                .thenReturn(List.of(STATUS_SENT));

        guard.checkAndLock(item);

        assertEquals(PipelineItem.Status.SKIPPED, item.getStatus());
        verify(redisTemplate).execute(any(), eq(List.of(expectedKey)), eq(expectedLease), eq(expectedTtl), eq(STATUS_SENT));
    }

    @Test
    @DisplayName("Key generation failure for one item should not block others in batch")
    void shouldIsolateKeyGenerationFailures() {
        var noDateReport = createItem(TemplateType.DAILY_TASK_REPORT, 777L, "t", 1L, Map.of());
        var goodWelcome = createItem(TemplateType.USER_WELCOME, 888L, "t", 2L, Map.of());

        String goodKey = registry.forType(goodWelcome.getTemplateType()).build(goodWelcome.getPayload());

        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(List.of("ACQUIRED"));

        guard.checkAndLock(new PipelineBatch(List.of(noDateReport, goodWelcome)));

        assertEquals(PipelineItem.Status.FAILED, noDateReport.getStatus());
        assertEquals(RejectReason.KEY_GENERATION, noDateReport.getRejectReason());

        assertEquals(PipelineItem.Status.PENDING, goodWelcome.getStatus());

        verify(redisTemplate).execute(any(), eq(List.of(goodKey)), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when Redis returns NULL (Protocol Error)")
    void shouldThrowWhenRedisReturnsNull() {
        var item = createItem(TemplateType.USER_WELCOME, 1L, "t", 1L, Map.of());

        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(null);

        var ex = assertThrows(StateStoreInfrastructureException.class, () -> guard.checkAndLock(item));
        assertTrue(ex.getMessage().contains("returned NULL"));
    }

    @Test
    @DisplayName("Should throw when Redis returns partial results (Size Mismatch)")
    void shouldThrowWhenResultSizeMismatch() {
        var items = List.of(
                createItem(TemplateType.USER_WELCOME, 1L, "t", 1L, Map.of()),
                createItem(TemplateType.USER_WELCOME, 2L, "t", 2L, Map.of())
        );

        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of("ACQUIRED"));

        var ex = assertThrows(StateStoreInfrastructureException.class, () -> guard.checkAndLock(new PipelineBatch(items)));
        assertTrue(ex.getMessage().contains("unexpected number of results"));
    }

    @Test
    @DisplayName("Should wrap any Redis Exception into StateStoreInfrastructureException")
    void shouldWrapGeneralRedisExceptions() {
        var item = createItem(TemplateType.USER_WELCOME, 1L, "t", 1L, Map.of());

        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("Redis connection lost"));

        var ex = assertThrows(StateStoreInfrastructureException.class, () -> guard.checkAndLock(item));
        assertTrue(ex.getMessage().contains("unavailable"));
        assertInstanceOf(RuntimeException.class, ex.getCause());
    }

    private PipelineItem createItem(TemplateType type, Long userId, String topic, long offset, Map<String, Object> context) {
        var record = TestKafkaConsumerRecordFactory.record()
                .topic(topic)
                .offset(offset)
                .build();

        PipelineItem item = new PipelineItem(record);
        item.setTemplateType(type);
        item.setPayload(new TriggerCommand("u@t.com", type.name(), context, "en", userId, "c-id"));

        return item;
    }
}