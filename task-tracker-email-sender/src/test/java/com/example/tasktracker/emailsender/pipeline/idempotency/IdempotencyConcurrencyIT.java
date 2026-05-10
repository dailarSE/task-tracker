package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.ContainerizedIntegrationTest;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.messaging.util.KafkaHeaderReader;
import com.example.tasktracker.emailsender.pipeline.EmailProcessor;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.BatchMatchers;
import com.example.tasktracker.emailsender.util.EmailSupport;
import com.example.tasktracker.emailsender.util.TestDataFactory;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static com.example.tasktracker.emailsender.api.messaging.MessagingHeaders.X_REJECT_REASON;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class IdempotencyConcurrencyIT extends ContainerizedIntegrationTest {

    @Autowired
    private EmailProcessor processor;

    @Autowired
    private IdempotencyCommitter realCommitter;

    private IdempotencyCommitter committerSpy;

    @BeforeEach
    void setupSpy() {
        committerSpy = Mockito.spy(realCommitter);
        ReflectionTestUtils.setField(processor, "committer", committerSpy);
    }

    @AfterEach
    void resetSpy() {
        Mockito.clearInvocations(committerSpy);
        ReflectionTestUtils.setField(processor, "committer", realCommitter);
    }

    @Test
    @DisplayName("concurrent lock: Блокировка при параллельной обработке (Fencing)")
    void concurrentLockTest() {
        String cid = "trace-concurrent-1";
        var cmd = TestDataFactory.welcome(cid, 5L);
        redis.forceForeignLock(cmd);

        kafka.send(cmd);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var messages = email.fetchAllMessages();
            assertFalse(EmailSupport.findByCorrelationId(messages, cid).isPresent(), "Email should not be sent");

            var retryRecord = kafka.getRetryRecord(cid);
            assertTrue(retryRecord.isPresent(), "Message must be routed to RETRY topic");

            Optional<String> reason = KafkaHeaderReader.readAsString(retryRecord.get(), X_REJECT_REASON);
            assertEquals(RejectReason.CONCURRENT_LOCK.name(), reason.orElseThrow());
        });
    }

    @Test
    @DisplayName("CommitterFail: Ошибка финализации в Redis не должна валить батч (Best Effort)")
    void committerFailTest() {
        String pKey = "integrity-key";
        String cidRetry = "marker-retry-2";
        String cidDlt = "marker-dlt-3";
        String cidTarget = "trace-committer-fail-1";

        var validCmd = TestDataFactory.welcome(cidTarget, 6L);
        TriggerCommand dltCmd = TestDataFactory.toDlt(cidDlt, 7L);
        TriggerCommand retryCmd = TestDataFactory.welcome(cidRetry, 8L);
        redis.forceForeignLock(retryCmd);

        Mockito.doAnswer(invocation -> {
                    redisProxy.toxics().latency("redis-timeout", ToxicDirection.DOWNSTREAM, 5000);
                    try {
                        return invocation.callRealMethod();
                    } finally {
                        try {
                            redisProxy.toxics().get("redis-timeout").remove();
                        } catch (IOException ignored) {
                        }
                    }
                })
                .when(committerSpy).commitSuccess(BatchMatchers.containsCid(cidTarget));

        kafka.send(validCmd, pKey);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Mockito.verify(committerSpy).commitSuccess(BatchMatchers.containsCid(cidTarget));

            var messages = email.fetchAllMessages();
            assertTrue(EmailSupport.findByCorrelationId(messages, cidTarget).isPresent(), "Email must be delivered");
        });

        kafka.send(dltCmd, pKey);
        kafka.send(retryCmd, pKey);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> {
                    assertTrue(kafka.getRetryRecord(cidRetry).isPresent(), "Retry marker failed to arrive");
                    assertTrue(kafka.getDltRecord(cidDlt).isPresent(), "DLT marker failed to arrive");
                }
        );


        assertTrue(kafka.getRetryRecord(cidTarget).isEmpty());
        assertTrue(kafka.getDltRecord(cidTarget).isEmpty());
    }
}