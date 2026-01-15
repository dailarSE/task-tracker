package com.example.tasktracker.scheduler.consumer.dailyreport.component;

import com.example.tasktracker.scheduler.consumer.dailyreport.config.DailyReportConsumerProperties;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.example.tasktracker.scheduler.metrics.SchedulerMetricConstants;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;


@Slf4j
public class DltPublisher {

    private final DeadLetterPublishingRecoverer recoverer;
    private final boolean dltEnabled;
    private final MetricsReporter metrics;

    public DltPublisher(DeadLetterPublishingRecoverer recoverer,
                        DailyReportConsumerProperties properties,
                        MetricsReporter metrics) {
        this.recoverer = recoverer;
        this.dltEnabled = properties.getRetryAndDlt().getDlt().isEnabled();
        this.metrics = metrics;
    }

    /**
     * Отправляет сообщение в Dead Letter Topic.
     * Используется для обработки частичных сбоев (Partial Failures) внутри батча,
     * когда мы не хотим откатывать весь батч целиком.
     */
    public void sendToDlt(ConsumerRecord<String, UserSelectedForDailyReportEvent> record, Exception cause) {
        if (dltEnabled) {
            try {
                recoverer.accept(record, cause);

                log.warn("Sent poison pill event to DLT for userId: {}. Reason: {}",
                        record.value().userId(), cause.getMessage());

                metrics.incrementCounter(
                        Metric.JOB_RUN_FAILURE,
                        Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND, Tag.of("reason", "sent_to_dlt"))
                );
            } catch (Exception e) {
                log.error("CRITICAL: Failed to delegate to Recoverer for userId: {}", record.value().userId(), e);
            }
        } else {
            log.error("DLT is disabled. Dropping poison pill event for userId: {}. Reason: {}",
                    record.value().userId(), cause.getMessage());

            metrics.incrementCounter(
                    Metric.JOB_RUN_FAILURE,
                    Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND, Tag.of("reason", "dropped_no_dlt"))
            );
        }
    }
}