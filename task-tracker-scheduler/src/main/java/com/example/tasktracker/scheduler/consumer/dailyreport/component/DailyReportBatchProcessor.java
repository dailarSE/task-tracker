package com.example.tasktracker.scheduler.consumer.dailyreport.component;

import com.example.tasktracker.scheduler.common.MdcKeys;
import com.example.tasktracker.scheduler.consumer.dailyreport.client.TaskReportFetcherClient;
import com.example.tasktracker.scheduler.consumer.dailyreport.client.dto.UserTaskReport;
import com.example.tasktracker.scheduler.consumer.dailyreport.messaging.dto.EmailTriggerCommand;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.example.tasktracker.scheduler.metrics.SchedulerMetricConstants;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyReportBatchProcessor {

    private final TaskReportFetcherClient fetcherClient;
    private final DailyReportMapper mapper;
    private final EmailCommandPublisher publisher;
    private final DltPublisher dltPublisher;
    private final MetricsReporter metrics;

    public void process(String jobRunId, LocalDate reportDate, List<UserSelectedForDailyReportEvent> events) {
        Timer.Sample sample = Timer.start();

        try (MDC.MDCCloseable ignored = MDC.putCloseable(MdcKeys.JOB_RUN_ID, jobRunId)) {
            List<Long> userIds = events.stream().map(UserSelectedForDailyReportEvent::userId).toList();
            log.info("Processing batch for {} users. Report Date: {}", userIds.size(), reportDate);

            Instant from = reportDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to = reportDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            Map<Long, UserSelectedForDailyReportEvent> eventMap = events.stream()
                    .collect(Collectors.toMap(UserSelectedForDailyReportEvent::userId,
                            Function.identity(),
                            (e1, e2) -> e1));

            List<UserTaskReport> reports = fetcherClient.fetchTaskReports(userIds, from, to);

            int skippedCount = userIds.size() - reports.size();
            if (skippedCount > 0) {
                log.debug("Skipped {} users (no data from backend).", skippedCount);
                metrics.incrementCounter(Metric.JOB_ITEM_SKIPPED,
                        skippedCount,
                        Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND, Tag.of("reason", "no_data")));
            }

            List<? extends CompletableFuture<?>> futures = reports.stream()
                    .map(report -> processSingleReport(report, reportDate, eventMap.get(report.userId())))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } finally {
            sample.stop(metrics.getTimer(Metric.JOB_RUN_DURATION,
                    Tags.of(SchedulerMetricConstants.TAG_EMAIL_COMMAND, Tag.of("phase", "enrichment_and_send"))));
        }
    }

    private CompletableFuture<?> processSingleReport(UserTaskReport report,
                                                     LocalDate reportDate,
                                                     UserSelectedForDailyReportEvent originalEvent) {
        try {
            EmailTriggerCommand command = mapper.toCommand(report, reportDate);
            return publisher.publish(command);
        } catch (Exception e) {
            log.error("Partial failure: Could not map/process report for userId: {}.", report.userId(), e);

            if (originalEvent != null) {
                dltPublisher.sendToDlt(originalEvent, e);
            }

            return CompletableFuture.completedFuture(null);
        }
    }
}