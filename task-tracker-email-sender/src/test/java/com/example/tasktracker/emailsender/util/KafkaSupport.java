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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
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

    private final KafkaTemplate<byte[], byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.email.kafka-topic}")
    private String mainTopic;

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

    @KafkaListener(topics = "${app.email.dlt-topic}", containerFactory = "rawSingleRetryFactory", groupId = "spy")
    void listenDlt(ConsumerRecord<byte[], byte[]> record) {
        extractCorrelationId(record).ifPresent(id -> dltRecords.put(id, record));
    }

    @KafkaListener(topics = "${app.email.retry-topic}", containerFactory = "rawSingleRetryFactory", groupId = "spy")
    void listenRetry(ConsumerRecord<byte[], byte[]> record) {
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
