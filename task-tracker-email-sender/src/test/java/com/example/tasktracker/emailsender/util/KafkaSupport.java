package com.example.tasktracker.emailsender.util;

import com.example.tasktracker.emailsender.api.messaging.MessagingHeaders;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.messaging.util.KafkaHeaderReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@TestComponent
@RequiredArgsConstructor
@Slf4j
public class KafkaSupport {
    private final Map<String, ConsumerRecord<byte[], byte[]>> dltRecords = new ConcurrentHashMap<>();
    private final Map<String, ConsumerRecord<byte[], byte[]>> retryRecords = new ConcurrentHashMap<>();

    private final KafkaListenerEndpointRegistry registry;
    private final KafkaTemplate<byte[], byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.email.kafka-topic}")
    private String mainTopic;

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        List<String> spyIds = List.of("spy-sup-dlt", "spy-sup-recovery");

        for (String id : spyIds) {
            var container = registry.getListenerContainer(id);
            if (container != null) {
                ContainerTestUtils.waitForAssignment(container, 1);
            } else {
                log.error("[DIAG] Spy container {} NOT FOUND in registry!", id);
            }
        }
    }

    @SneakyThrows
    public void send(TriggerCommand cmd) {
        byte[] payload = objectMapper.writeValueAsBytes(cmd);
        var record = new ProducerRecord<>(mainTopic, cmd.correlationId().getBytes(StandardCharsets.UTF_8), payload);

        record.headers().add(MessagingHeaders.X_CORRELATION_ID, cmd.correlationId().getBytes());
        record.headers().add(MessagingHeaders.X_TEMPLATE_ID, cmd.templateId().getBytes());

        kafkaTemplate.send(record).get(1, TimeUnit.SECONDS);
    }

    @SneakyThrows
    public void send(TriggerCommand cmd, String partitionKey) {
        byte[] payload = objectMapper.writeValueAsBytes(cmd);

        var record = new ProducerRecord<>(mainTopic, partitionKey.getBytes(StandardCharsets.UTF_8), payload);

        record.headers().add(MessagingHeaders.X_CORRELATION_ID, cmd.correlationId().getBytes());
        record.headers().add(MessagingHeaders.X_TEMPLATE_ID, cmd.templateId().getBytes());

        kafkaTemplate.send(record).get(1, TimeUnit.SECONDS);
    }

    @KafkaListener(id = "spy-sup-dlt", topics = "${app.email.dlt-topic}", containerFactory = "rawSingleRetryFactory", groupId = "spy")
    void listenDlt(ConsumerRecord<byte[], byte[]> record) {
        log.info("[DIAG-KAFKA-SUP] dlt listener - record cid - '{}'", extractCorrelationId(record));
        extractCorrelationId(record).ifPresent(id -> dltRecords.put(id, record));
    }

    @KafkaListener(id = "spy-sup-recovery", topics = "${app.email.retry-topic}-retry-60000", containerFactory = "rawSingleRetryFactory", groupId = "spy")
    void listenRetry(ConsumerRecord<byte[], byte[]> record) {
        log.info("[DIAG-KAFKA-SUP] retry listener - record cid - '{}'", extractCorrelationId(record));
        extractCorrelationId(record).ifPresent(id -> retryRecords.put(id, record));
    }

    public Optional<ConsumerRecord<byte[], byte[]>> getDltRecord(String cid) {
        return Optional.ofNullable(dltRecords.get(cid));
    }

    public Optional<ConsumerRecord<byte[], byte[]>> getRetryRecord(String cid) {
        return Optional.ofNullable(retryRecords.get(cid));
    }

    public void clear() {
        dltRecords.clear();
        retryRecords.clear();
    }

    private Optional<String> extractCorrelationId(ConsumerRecord<byte[], byte[]> record) {
        return KafkaHeaderReader.readAsString(record, MessagingHeaders.X_CORRELATION_ID);
    }
}
