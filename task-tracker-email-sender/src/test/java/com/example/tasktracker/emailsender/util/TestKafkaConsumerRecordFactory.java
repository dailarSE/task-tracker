package com.example.tasktracker.emailsender.util;

import com.example.tasktracker.emailsender.api.messaging.MessagingHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * Универсальная фабрика для создания ConsumerRecord в тестах.
 * Инкапсулирует сложность конструкторов Kafka и предоставляет Fluent API.
 */
public class TestKafkaConsumerRecordFactory {

    private String topic = "test-topic";
    private int partition = 0;
    private long offset = 0L;
    private long timestamp = Instant.now().toEpochMilli();
    private TimestampType timestampType = TimestampType.CREATE_TIME;
    private byte[] key = null;
    private byte[] value = "{}".getBytes(StandardCharsets.UTF_8);
    private final RecordHeaders headers = new RecordHeaders();

    public static TestKafkaConsumerRecordFactory record() {
        return new TestKafkaConsumerRecordFactory();
    }

    public TestKafkaConsumerRecordFactory topic(String topic) {
        this.topic = topic;
        return this;
    }

    public TestKafkaConsumerRecordFactory offset(long offset) {
        this.offset = offset;
        return this;
    }

    public TestKafkaConsumerRecordFactory timestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public TestKafkaConsumerRecordFactory timestamp(Instant instant) {
        this.timestamp = instant.toEpochMilli();
        return this;
    }

    public TestKafkaConsumerRecordFactory body(String body) {
        this.value = body.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public TestKafkaConsumerRecordFactory header(String key, String value) {
        if (value != null) {
            this.headers.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
        return this;
    }

    // методы для специфичных заголовков проекта
    public TestKafkaConsumerRecordFactory template(String type) {
        return header(MessagingHeaders.X_TEMPLATE_ID, type);
    }

    public TestKafkaConsumerRecordFactory validUntil(String isoDate) {
        return header(MessagingHeaders.X_VALID_UNTIL, isoDate);
    }

    public TestKafkaConsumerRecordFactory correlationId(String id) {
        return header(MessagingHeaders.X_CORRELATION_ID, id);
    }

    /**
     * Собирает ConsumerRecord.
     */
    public ConsumerRecord<byte[], byte[]> build() {
        return new ConsumerRecord<>(
                topic,
                partition,
                offset,
                timestamp,
                timestampType,
                key == null ? -1 : key.length,
                value == null ? -1 : value.length,
                key,
                value,
                headers,
                Optional.empty()
        );
    }
}