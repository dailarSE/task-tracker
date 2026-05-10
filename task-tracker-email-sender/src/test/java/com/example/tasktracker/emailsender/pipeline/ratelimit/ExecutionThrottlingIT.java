package com.example.tasktracker.emailsender.pipeline.ratelimit;

import com.example.tasktracker.emailsender.ContainerizedIntegrationTest;
import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import com.example.tasktracker.emailsender.o11y.pipeline.ObservedBucket4jRpsLimiter;
import com.example.tasktracker.emailsender.o11y.pipeline.ObservedValidatingEmailBatchAssembler;
import com.example.tasktracker.emailsender.pipeline.EmailProcessor;
import com.example.tasktracker.emailsender.pipeline.idempotency.IdempotencyCommitter;
import com.example.tasktracker.emailsender.util.EmailSupport;
import com.example.tasktracker.emailsender.util.TestDataFactory;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
class ExecutionThrottlingIT extends ContainerizedIntegrationTest {

    @Autowired
    private ReliabilityProperties properties;
    @Autowired
    RpsLimiter rpsLimiter;
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
    @DisplayName("rpsLimit: Проверка лимитирования RPS (Chunking)")
    void rpsLimitTest() {
        Object rpsTarget = extractDelegate(rpsLimiter);
        ReflectionTestUtils.setField(rpsTarget, "acquisitionTimeout", Duration.ofNanos(1));

        var commands = List.of(
                TestDataFactory.welcome("c-rps-1", 100L),
                TestDataFactory.welcome("c-rps-2", 101L),
                TestDataFactory.welcome("c-rps-3", 102L)
        );

        try {
            rps.forceEmpty();

            commands.forEach(kafka::send);

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertTrue(commands.stream()
                        .anyMatch(command -> null != redis.getStatus(command))
                );

                List<JsonNode> messages = email.fetchAllMessages();
                commands.forEach(cmd ->
                        assertTrue(EmailSupport.findByCorrelationId(messages, cmd.correlationId()).isEmpty(),
                                "Ни одно письмо не должно уйти")
                );

                verifyNoInteractions(committerSpy);

                commands.forEach(cmd ->
                        assertNotEquals("SENT", redis.getStatus(cmd), "Ключи не должны быть финализированы")
                );
            });

        } finally {
            ReflectionTestUtils.setField(
                    rpsTarget, "acquisitionTimeout", properties.getCapacity().getAcquisitionTimeout());
            rps.resetToInit();
            //drop kafka state
            await()
                    .atMost(Duration.ofSeconds(10))
                    .pollDelay(properties.getKafkaRetry().getBlockingRetryInterval())
                    .untilAsserted(() -> {
                        List<JsonNode> messages = email.fetchAllMessages();
                        commands.forEach(cmd ->
                                assertTrue(EmailSupport.findByCorrelationId(messages, cmd.correlationId()).isPresent(),
                                        "письма должны уйти")
                        );
                    });
        }
    }

    @Test
    @DisplayName("batchProcessingTimeLimit: Моментальный аборт батча при нулевом бюджете времени")
    void batchProcessingTimeoutTest() {
        Duration originalBatchBudget = properties.getBudget().getMaxBatchProcessingTime();
        ReflectionTestUtils.setField(properties.getBudget(), "maxBatchProcessingTime", Duration.ofNanos(-1));

        String cid = "c-batch-timeout";
        var cmd = TestDataFactory.welcome(cid, 200L);

        try {


            kafka.send(cmd);

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertNotNull(redis.getStatus(cmd), "лок должен быть взят");

                assertTrue(EmailSupport.findByCorrelationId(email.fetchAllMessages(), cid).isEmpty(), "письмо должно быть не отправлено");

                verifyNoInteractions(committerSpy);
                assertTrue(kafka.getDltRecord(cid).isEmpty(), "dlt должен быть пуст");
            });

        } finally {
            ReflectionTestUtils.setField(properties.getBudget(), "maxBatchProcessingTime", originalBatchBudget);
            //drop kafka state
            await().atMost(Duration.ofSeconds(10))
                    .pollDelay(properties.getKafkaRetry().getBlockingRetryInterval())
                    .untilAsserted(() -> {
                        List<JsonNode> messages = email.fetchAllMessages();
                        assertTrue(EmailSupport.findByCorrelationId(messages, cmd.correlationId()).isPresent(),
                                "письмо должно уйти");
                    });
        }
    }

    private Object extractDelegate(Object proxy) {
        if (proxy instanceof ObservedBucket4jRpsLimiter observed) {
            return ReflectionTestUtils.getField(observed, "delegate");
        }
        if (proxy instanceof ObservedValidatingEmailBatchAssembler observed) {
            return ReflectionTestUtils.getField(observed, "delegate");
        }
        return proxy;
    }
}
