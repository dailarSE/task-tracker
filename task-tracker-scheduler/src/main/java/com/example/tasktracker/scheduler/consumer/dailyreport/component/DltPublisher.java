package com.example.tasktracker.scheduler.consumer.dailyreport.component;

import com.example.tasktracker.scheduler.consumer.dailyreport.config.DailyReportConsumerProperties;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.example.tasktracker.scheduler.metrics.SchedulerMetricConstants;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;


@Component
@Slf4j
public class DltPublisher {

    private final KafkaTemplate<String, UserSelectedForDailyReportEvent> kafkaTemplate;
    private final String dltTopicName;
    private final MetricsReporter metrics;

    public DltPublisher(KafkaTemplate<String, UserSelectedForDailyReportEvent> kafkaTemplate,
                        DailyReportConsumerProperties properties,
                        MetricsReporter metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.dltTopicName = properties.getTopicName() + ".DLT";
        this.metrics = metrics;
    }

    /**
     * Отправляет сообщение в Dead Letter Topic вручную.
     * Используется для обработки частичных сбоев (Partial Failures) внутри батча,
     * когда мы не хотим откатывать весь батч целиком.
     */
    public void sendToDlt(UserSelectedForDailyReportEvent event, Throwable cause) {
        try {
            ProducerRecord<String, UserSelectedForDailyReportEvent> record =
                    new ProducerRecord<>(dltTopicName, null, event);

            record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE,
                    cause.getMessage().getBytes(StandardCharsets.UTF_8));
            record.headers().add(KafkaHeaders.DLT_EXCEPTION_STACKTRACE,
                    cause.toString().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.error("CRITICAL: Failed to send message to DLT. Event lost for userId: {}", event.userId(), ex);
                } else {
                    log.warn("Sent poison pill event to DLT for userId: {}. Reason: {}", event.userId(), cause.getMessage());
                }
            });

            metrics.incrementCounter(
                    Metric.JOB_RUN_FAILURE,
                    Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND, Tag.of("reason", "sent_to_dlt"))
            );

        } catch (Exception e) {
            log.error("CRITICAL: Unexpected error while sending to DLT for userId: {}", event.userId(), e);
        }
    }
}