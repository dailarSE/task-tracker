package com.example.tasktracker.emailsender.messaging.util;

import lombok.experimental.UtilityClass;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@UtilityClass
public class KafkaHeaderReader {
    public static Optional<String> readAsString(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return getValue(header);
    }

    public static Optional<String> readAsString(ProducerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return getValue(header);
    }

    private static Optional<String> getValue(Header header) {
        if (header == null || header.value() == null) {
            return Optional.empty();
        }
        return Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }
}
