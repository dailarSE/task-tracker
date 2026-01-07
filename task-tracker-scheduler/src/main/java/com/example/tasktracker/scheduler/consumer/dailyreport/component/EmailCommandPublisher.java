package com.example.tasktracker.scheduler.consumer.dailyreport.component;

import com.example.tasktracker.scheduler.config.SchedulerAppProperties;
import com.example.tasktracker.scheduler.consumer.dailyreport.messaging.dto.EmailTriggerCommand;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.example.tasktracker.scheduler.metrics.SchedulerMetricConstants;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Компонент, ответственный за публикацию команд на отправку email в Kafka.
 * Инкапсулирует логику отправки и сбор базовых метрик успеха/неудачи.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailCommandPublisher {

    private final KafkaTemplate<String, EmailTriggerCommand> kafkaTemplate;
    private final SchedulerAppProperties appProperties;
    private final MetricsReporter metrics;

    /**
     * Асинхронно публикует команду в топик уведомлений.
     *
     * @param command Команда на отправку email.
     * @return CompletableFuture с результатом отправки.
     */
    public CompletableFuture<SendResult<String, EmailTriggerCommand>> publish(EmailTriggerCommand command) {
        String topic = appProperties.getKafka().getReportsProducer().getTopicName();
        String key = String.valueOf(command.userId());

        return kafkaTemplate.send(topic, key, command)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        metrics.incrementCounter(
                                Metric.JOB_EVENTS_PUBLISHED,
                                Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND)
                        );
                        log.debug("Successfully sent email command for userId: {}", command.userId());
                    } else {
                        log.error("Failed to send email command to topic '{}' for userId: {}", topic, command.userId(), ex);
                        metrics.incrementCounter(
                                Metric.JOB_KAFKA_SEND_FAILURE,
                                Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND)
                        );
                    }
                });
    }
}