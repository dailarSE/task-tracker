package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureSuspendedException;
import com.example.tasktracker.emailsender.messaging.util.KafkaMetadataEnricher;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.DeadLetterPublishingRecovererFactory;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationSupport;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class PipelineRetryConfig extends RetryTopicConfigurationSupport {
    private final KafkaMetadataEnricher kafkaMetadataEnricher;

    /**
     * Данная функция намеренно мутирует объект заголовков оригинального {@code ConsumerRecord} перед отправкой.
     * <p>
     * <b>Это пока безопасно:</b>
     * 1. Основной цикл обработки письма к этому моменту уже завершен (с ошибкой).
     * 2. Удаляемые заголовки являются сугубо техническими/диагностическими и не влияют
     *    на бизнес-логику или целостность данных.
     * 3. В случае Nack и полного перечитывания, брокер выдаст "чистый" рекорд без мутаций.
     */
    @Override
    protected Consumer<DeadLetterPublishingRecovererFactory> configureDeadLetterPublishingContainerFactory() {
        return dlprf -> dlprf.setHeadersFunction((record, e) -> {
            kafkaMetadataEnricher.clearRejectionDetails(record.headers());
            Throwable thr = NestedExceptionUtils.getMostSpecificCause(e);
            if (thr instanceof RetryableProcessingException re &&
                    re.getRejectReason() != null &&
                    re.getRejectReason() != RejectReason.NONE) {
                RecordHeaders headers = new RecordHeaders();
                kafkaMetadataEnricher.injectRejectionDetails(headers,re.getRejectReason(),re.getMessage());
                return headers;
            }
            return null;
        });
    }

    @Override
    protected void configureBlockingRetries(BlockingRetriesConfigurer blockingRetries) {
        blockingRetries
                .retryOn(InfrastructureException.class, InfrastructureSuspendedException.class)
                .backOff(new FixedBackOff(30000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }

    /**
     * 2. НЕБЛОКИРУЮЩИЕ РЕТРАИ (Topic Hopping)
     * Это замена аннотации @RetryableTopic. Явное описание маршрутов.
     */
    @Bean
    public RetryTopicConfiguration emailRetryTopicConfig(
            @Qualifier("rawKafkaTemplate") KafkaTemplate<byte[], byte[]> kafkaTemplate,
            EmailSenderProperties properties) {

        return RetryTopicConfigurationBuilder
                .newInstance()
                .exponentialBackoff(60_000, 1.98, Duration.ofHours(4).toMillis())
                .maxAttempts(10)
                .includeTopic(properties.getRetryTopic())
                .retryOn(RetryableProcessingException.class)
                .listenerFactory("rawSingleRetryFactory")
                .create(kafkaTemplate);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("kafka-retry-");
        scheduler.setPoolSize(2);
        scheduler.initialize();
        return scheduler;
    }
}