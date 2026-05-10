package com.example.tasktracker.emailsender.pipeline;

import com.example.tasktracker.emailsender.ContainerizedIntegrationTest;
import com.example.tasktracker.emailsender.api.email.EmailHeaders;
import com.example.tasktracker.emailsender.pipeline.idempotency.IdempotencyStatus;
import com.example.tasktracker.emailsender.util.EmailSupport;
import com.example.tasktracker.emailsender.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EmailLifecycleIT extends ContainerizedIntegrationTest {

    @Test
    @DisplayName("happy: Сквозная проверка успешной отправки")
    void happyPath() {
        String cid = "happy-1";
        var cmd = TestDataFactory.welcome(cid, 1L);

        kafka.send(cmd);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var messages = email.fetchAllMessages();
            assertEquals(1, messages.size());
            assertEquals(cid, messages.getFirst().get("Content").get("Headers").get(EmailHeaders.X_CORRELATION_ID).get(0).asText());

            assertEquals(IdempotencyStatus.SENT, redis.getStatus(cmd));

            assertTrue(kafka.getDltRecord(cid).isEmpty());
            assertTrue(kafka.getRetryRecord(cid).isEmpty());
        });
    }

    @Test
    @DisplayName("duplicate: Повторная отправка должна игнорироваться")
    void duplicateTest() {
        String cidDup = "trace-dup-A";
        var cmdDup = TestDataFactory.welcome(cidDup, 2L);
        redis.forceStatus(cmdDup, IdempotencyStatus.SENT);

        String cidMarker = "trace-sentinel-B";
        var cmdMarker = TestDataFactory.welcome(cidMarker, 3L);

        String commonKey = "dub";
        kafka.send(cmdDup, commonKey);
        kafka.send(cmdMarker, commonKey);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var messages = email.fetchAllMessages();

            boolean sentinelArrived = EmailSupport.findByCorrelationId(messages, cidMarker).isPresent();
            assertTrue(sentinelArrived, "Sentinel message should arrive");

            boolean duplicateArrived = EmailSupport.findByCorrelationId(messages, cidDup).isPresent();
            assertFalse(duplicateArrived, "Duplicate message should NOT be delivered");

            assertEquals(1, messages.size());

            assertTrue(kafka.getDltRecord(cidDup).isEmpty());
            assertTrue(kafka.getRetryRecord(cidDup).isEmpty());
        });
    }
}