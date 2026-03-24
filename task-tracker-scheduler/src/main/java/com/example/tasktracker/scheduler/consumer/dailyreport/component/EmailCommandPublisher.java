package com.example.tasktracker.scheduler.consumer.dailyreport.component;

import com.example.tasktracker.scheduler.config.EmailPublishingProperties;
import com.example.tasktracker.scheduler.consumer.dailyreport.config.DailyReportConsumerProperties;
import com.example.tasktracker.scheduler.consumer.dailyreport.messaging.api.EmailTriggerCommand;
import com.example.tasktracker.scheduler.consumer.dailyreport.messaging.api.MessagingHeaders;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.example.tasktracker.scheduler.metrics.SchedulerMetricConstants;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Компонент, ответственный за публикацию команд на отправку email в Kafka.
 * Инкапсулирует логику отправки и сбор базовых метрик успеха/неудачи.
 */

@Slf4j
public class EmailCommandPublisher {

    private final KafkaTemplate<String, EmailTriggerCommand> kafkaTemplate;
    private final Clock clock;
    private final String sinkTopicName;
    private final EmailPublishingProperties publishingProperties;
    private final MetricsReporter metrics;


    public EmailCommandPublisher(KafkaTemplate<String, EmailTriggerCommand> kafkaTemplate,
                                 Clock clock,
                                 DailyReportConsumerProperties consumerProperties,
                                 EmailPublishingProperties publishingProperties,
                                 MetricsReporter metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.sinkTopicName = consumerProperties.getSinkTopicName();
        this.publishingProperties = publishingProperties;
        this.metrics = metrics;
    }

    /**
     * Асинхронно публикует команду в топик уведомлений.
     *
     * @param command Команда на отправку email.
     * @return CompletableFuture с результатом отправки.
     */
    public CompletableFuture<SendResult<String, EmailTriggerCommand>> publish(EmailTriggerCommand command) {
        ProducerRecord<String, EmailTriggerCommand> record = buildRecord(command);

        return kafkaTemplate.send(record)
                .whenComplete((result, ex) -> reportMetrics(command, ex));
    }

    private ProducerRecord<String, EmailTriggerCommand> buildRecord(EmailTriggerCommand command) {
        String key = String.valueOf(command.userId());
        ProducerRecord<String, EmailTriggerCommand> record =
                new ProducerRecord<>(sinkTopicName, key, command);

        var headers = record.headers();
        headers.add(new RecordHeader(MessagingHeaders.X_TEMPLATE_ID,
                command.templateId().name().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(MessagingHeaders.X_CORRELATION_ID,
                command.correlationId().getBytes(StandardCharsets.UTF_8)));

        Instant validUntil = Instant.now(clock)
                .plus(publishingProperties.getValidity().getDurationFor(command.templateId()));

        headers.add(new RecordHeader(MessagingHeaders.X_VALID_UNTIL,
                validUntil.toString().getBytes(StandardCharsets.UTF_8)));

        return record;
    }

    private void reportMetrics(EmailTriggerCommand command, Throwable ex) {
        if (ex == null) {
            metrics.incrementCounter(Metric.JOB_EVENTS_PUBLISHED, Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND));
            log.debug("Successfully sent email command for userId: {}", command.userId());
        } else {
            log.error("Failed to send email command to topic '{}' for userId: {}", sinkTopicName, command.userId(), ex);
            metrics.incrementCounter(Metric.JOB_KAFKA_SEND_FAILURE, Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND));
        }
    }
}