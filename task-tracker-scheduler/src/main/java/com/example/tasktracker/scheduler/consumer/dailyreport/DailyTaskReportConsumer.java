package com.example.tasktracker.scheduler.consumer.dailyreport;

import com.example.tasktracker.scheduler.consumer.dailyreport.component.DailyReportBatchProcessor;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.example.tasktracker.scheduler.metrics.SchedulerMetricConstants;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kafka Consumer, слушающий события `UserSelectedForDailyReportEvent`.
 * Работает в режиме Batch Listener для эффективной групповой обработки.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyTaskReportConsumer {

    private final DailyReportBatchProcessor batchProcessor;
    private final MetricsReporter metrics;

    /**
     * Ключ для группировки событий внутри батча.
     * Необходим, так как в один poll Kafka могут попасть события от разных запусков джобы
     * (например, хвост вчерашнего и начало сегодняшнего).
     */
    private record JobRunKey(String jobRunId, LocalDate reportDate) {}

    @KafkaListener(
            topics = "${app.scheduler.consumers.daily-report.topic-name}",
            containerFactory = "dailyReportBatchContainerFactory"
    )
    public void consume(List<UserSelectedForDailyReportEvent> events) {
        if (CollectionUtils.isEmpty(events)) {
            return;
        }

        metrics.recordDistribution(
                Metric.JOB_BATCH_SIZE,
                events.size(),
                Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND)
        );

        Map<JobRunKey, List<UserSelectedForDailyReportEvent>> eventsByJobRun = events.stream()
                .collect(Collectors.groupingBy(
                        e -> new JobRunKey(e.jobRunId(), e.reportDate())
                ));

        log.debug("Consumed batch of {} events. Processing {} groups.", events.size(), eventsByJobRun.size());

        eventsByJobRun.forEach((key, eventList) ->
                batchProcessor.process(key.jobRunId(), key.reportDate(), eventList)
        );
    }
}