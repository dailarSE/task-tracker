package com.example.tasktracker.emailsender.messaging.util;

import com.example.tasktracker.emailsender.api.messaging.MessagingHeaders;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class KafkaMetadataEnricher {

    private static final List<String> METADATA_KEYS = List.of(
            KafkaHeaders.ORIGINAL_TOPIC,
            KafkaHeaders.ORIGINAL_PARTITION,
            KafkaHeaders.ORIGINAL_OFFSET,
            KafkaHeaders.ORIGINAL_TIMESTAMP,
            KafkaHeaders.ORIGINAL_TIMESTAMP_TYPE,
            KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP,
            KafkaHeaders.EXCEPTION_FQCN,
            KafkaHeaders.EXCEPTION_MESSAGE,
            KafkaHeaders.EXCEPTION_STACKTRACE,
            KafkaHeaders.EXCEPTION_CAUSE_FQCN,
            MessagingHeaders.X_REJECT_DESCRIPTION
    );


    /**
     * Обогащает заголовки информацией об оригинальном сообщении и причине отказа.
     */
    public void enrichWithFailureMetadata(@NonNull Headers targetHeaders,
                                          @NonNull ConsumerRecord<byte[], byte[]> sourceRecord,
                                          @NonNull PipelineItem.ExecutionStage failureStage) {
        clearExistingMetadata(targetHeaders);
        copySourceCoordinates(targetHeaders, sourceRecord);
        injectErrorDetails(targetHeaders, failureStage);
    }

    public void clearExistingMetadata(@NonNull Headers headers) {
        METADATA_KEYS.forEach(headers::remove);
    }

    /**
     * Записывает координаты сообщения (Topic, Partition, Offset).
     */
    private void copySourceCoordinates(@NonNull Headers headers, @NonNull ConsumerRecord<byte[], byte[]> original) {
        addHeader(headers, KafkaHeaders.ORIGINAL_TOPIC, original.topic());
        addHeader(headers, KafkaHeaders.ORIGINAL_PARTITION, original.partition());
        addHeader(headers, KafkaHeaders.ORIGINAL_OFFSET, original.offset());
        addHeader(headers, KafkaHeaders.ORIGINAL_TIMESTAMP, original.timestamp());
        addHeader(headers, KafkaHeaders.ORIGINAL_TIMESTAMP_TYPE, original.timestampType().name());

        String groupId = KafkaUtils.getConsumerGroupId();
        if (groupId != null) {
            addHeader(headers, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, groupId);
        }
    }

    /**
     * Записывает детали исключения для отладки.
     */
    private void injectErrorDetails(@NonNull Headers headers, @NonNull PipelineItem.ExecutionStage failureStage) {
        Exception ex = failureStage.rejectCause();
        if (ex != null) {
            addHeader(headers, KafkaHeaders.EXCEPTION_FQCN, ex.getClass().getName());
            addHeader(headers, KafkaHeaders.EXCEPTION_MESSAGE, ex.getMessage());
            addHeader(headers, KafkaHeaders.EXCEPTION_STACKTRACE, getStackTrace(ex));

            Optional.ofNullable(ex.getCause())
                    .ifPresent(cause -> addHeader(headers, KafkaHeaders.EXCEPTION_CAUSE_FQCN, cause.getClass().getName()));
        }

        addHeader(headers, MessagingHeaders.X_REJECT_DESCRIPTION, failureStage.rejectDescription());
    }

    private void addHeader(@NonNull Headers headers, String key, Object value) {
        if (value != null) {
            headers.add(key, String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        }
    }

    private String getStackTrace(@NonNull Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
