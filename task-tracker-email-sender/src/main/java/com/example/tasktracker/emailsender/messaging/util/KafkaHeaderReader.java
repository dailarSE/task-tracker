package com.example.tasktracker.emailsender.messaging.util;

import lombok.experimental.UtilityClass;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@UtilityClass
public class KafkaHeaderReader {
    public static Optional<String> readString(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null || header.value() == null) {
            return Optional.empty();
        }
        return Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }
}
