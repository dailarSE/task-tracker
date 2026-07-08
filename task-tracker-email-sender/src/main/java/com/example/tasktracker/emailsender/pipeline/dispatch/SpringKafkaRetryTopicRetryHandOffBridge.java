package com.example.tasktracker.emailsender.pipeline.dispatch;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.messaging.util.KafkaMetadataEnricher;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.DestinationTopic;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class SpringKafkaRetryTopicRetryHandOffBridge implements RetryHandOffBridge {
    private final RetryTopicConfiguration emailRetryTopicConfig;
    private final KafkaTemplate<byte[], byte[]> kafkaTemplate;
    private final KafkaMetadataEnricher metadataEnricher;
    private final EmailSenderProperties emailSenderProperties;
    private final Clock clock;

    public SpringKafkaRetryTopicRetryHandOffBridge(RetryTopicConfiguration emailRetryTopicConfig,
                                                   @Qualifier("rawKafkaTemplate") KafkaTemplate<byte[], byte[]> kafkaTemplate,
                                                   KafkaMetadataEnricher metadataEnricher,
                                                   EmailSenderProperties emailSenderProperties,
                                                   Clock clock) {
        this.emailRetryTopicConfig = emailRetryTopicConfig;
        this.kafkaTemplate = kafkaTemplate;
        this.metadataEnricher = metadataEnricher;
        this.emailSenderProperties = emailSenderProperties;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<?> handOff(PipelineItem item, PipelineItem.ExecutionStage failedStage) {
        ConsumerRecord<byte[], byte[]> orig = item.getOriginalRecord();
        String entryRetryTopic = emailSenderProperties.getRetryTopic();

        DestinationTopic.Properties firstRetryProps = emailRetryTopicConfig.getDestinationTopicProperties()
                .stream()
                .filter(p -> !p.isDltTopic() && p.delay() > 0)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Bridge: No delayed retry topic properties configured in configuration bean."));

        String targetTopic = entryRetryTopic + firstRetryProps.suffix();
        long delayMs = firstRetryProps.delay();

        log.debug("Hand-off item [{}] to topic [{}] with delay {}ms", item.getCoordinates(), targetTopic, delayMs);

        RecordHeaders headers = new RecordHeaders(orig.headers());
        metadataEnricher.enrichWithFailureMetadata(headers, orig, failedStage);

        byte[] originalTimestampHeader = BigInteger.valueOf(orig.timestamp()).toByteArray();
        byte[] attemptsHeaderBytes = ByteBuffer.allocate(Integer.BYTES).putInt(1).array();

        long nextExecutionTimestamp = clock.millis() + delayMs;
        byte[] backoffTimestampBytes = BigInteger.valueOf(nextExecutionTimestamp).toByteArray();

        headers.add(RetryTopicHeaders.DEFAULT_HEADER_ORIGINAL_TIMESTAMP, originalTimestampHeader);
        headers.add(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS, attemptsHeaderBytes);
        headers.add(RetryTopicHeaders.DEFAULT_HEADER_BACKOFF_TIMESTAMP, backoffTimestampBytes);

        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(
                targetTopic,
                null,
                orig.key(),
                orig.value(),
                headers
        );

        return kafkaTemplate.send(record);
    }
}
