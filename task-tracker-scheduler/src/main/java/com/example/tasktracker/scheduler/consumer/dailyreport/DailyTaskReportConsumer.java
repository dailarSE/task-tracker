package com.example.tasktracker.scheduler.consumer.dailyreport;

import com.example.tasktracker.scheduler.consumer.dailyreport.component.DailyReportBatchProcessor;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.example.tasktracker.scheduler.metrics.SchedulerMetricConstants;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kafka Consumer, слушающий события `UserSelectedForDailyReportEvent`.
 * Работает в режиме Batch Listener для эффективной групповой обработки.
 */
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
            id = "daily-report-consumer",
            topics = "${app.scheduler.consumers.daily-report.topic-name}",
            containerFactory = "dailyReportBatchContainerFactory"
    )
    public void consume(List<ConsumerRecord<String, UserSelectedForDailyReportEvent>> records) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }

        metrics.recordDistribution(
                Metric.JOB_BATCH_SIZE,
                records.size(),
                Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND)
        );

        Map<JobRunKey, List<ConsumerRecord<String, UserSelectedForDailyReportEvent>>> recordsByJob = records.stream()
                .collect(Collectors.groupingBy(
                        r -> new JobRunKey(r.value().jobRunId(), r.value().reportDate())
                ));

        log.debug("Consumed batch of {} records. Processing {} groups.", records.size(), recordsByJob.size());

        recordsByJob.forEach((key, jobRecords) ->
                batchProcessor.process(key.jobRunId(), key.reportDate(), jobRecords)
        );
    }
}