package com.example.tasktracker.scheduler.job.dailyreport;

import com.example.tasktracker.scheduler.common.MdcKeys;
import com.example.tasktracker.scheduler.job.JobStateRepository;
import com.example.tasktracker.scheduler.job.dailyreport.client.UserIdsFetcherClient;
import com.example.tasktracker.scheduler.job.dailyreport.client.dto.PaginatedUserIdsResponse;
import com.example.tasktracker.scheduler.job.dailyreport.config.DailyReportJobProperties;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.job.dto.CursorPayload;
import com.example.tasktracker.scheduler.job.dto.JobExecutionState;
import com.example.tasktracker.scheduler.job.dto.JobStatus;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.fasterxml.jackson.core.type.TypeReference;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockExtender;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис, инкапсулирующий основную бизнес-логику джобы-продюсера
 * для ежедневных отчетов по задачам.
 * <p>
 * Эта джоба отвечает за итеративную выборку ID пользователей из
 * backend-сервиса и публикацию событий в Kafka для их последующей
 * асинхронной обработки.
 * </p>
 * <p>
 * Использует Redis для хранения состояния (курсора) для обеспечения
 * возобновляемости и идемпотентности.
 * </p>
 */
@Slf4j
public class DailyTaskReportProducerJob {

    private final JobStateRepository jobStateRepository;
    private final UserIdsFetcherClient userIdsFetcherClient;
    private final KafkaTemplate<String, UserSelectedForDailyReportEvent> kafkaTemplate;
    private final Executor kafkaCallbackExecutor;
    private final DailyReportJobProperties jobProperties;
    private final String producerTopicName;
    private final MetricsReporter metrics;
    private final TypeReference<JobExecutionState<CursorPayload>> stateTypeReference = new TypeReference<>() {
    };

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";


    public DailyTaskReportProducerJob(JobStateRepository jobStateRepository,
                                      UserIdsFetcherClient userIdsFetcherClient,
                                      KafkaTemplate<String, UserSelectedForDailyReportEvent> kafkaTemplate,
                                      Executor kafkaCallbackExecutor,
                                      DailyReportJobProperties jobProperties,
                                      MetricsReporter metrics) {
        this.jobStateRepository = jobStateRepository;
        this.userIdsFetcherClient = userIdsFetcherClient;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaCallbackExecutor = kafkaCallbackExecutor;
        this.jobProperties = jobProperties;
        this.producerTopicName = jobProperties.getKafkaTopicName();
        this.metrics = metrics;
    }

    /**
     * Основной метод выполнения джобы.
     *
     * @param reportDate Дата, за которую формируется отчет.
     */
    public void execute(@NonNull LocalDate reportDate) {
        final String jobName = jobProperties.getJobName();
        final String jobRunId = UUID.randomUUID().toString();

        Timer.Sample sample = Timer.start();
        String finalStatus = STATUS_SUCCESS;

        try (
                MDC.MDCCloseable ignoredRunId = MDC.putCloseable(MdcKeys.JOB_RUN_ID, jobRunId);
                MDC.MDCCloseable ignoredJobName = MDC.putCloseable(MdcKeys.JOB_NAME, jobName);
                MDC.MDCCloseable ignoredReportDate = MDC.putCloseable(MdcKeys.REPORT_DATE,
                        reportDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
        ) {
            log.info("Producer execution starting for job '{}', report date: {}.", jobName, reportDate);

            try {
                Optional<JobExecutionState<CursorPayload>> currentState = jobStateRepository
                        .findState(jobName, reportDate, stateTypeReference);

                if (isJobTerminated(currentState.orElse(null), reportDate)) {
                    return;
                }

                String cursor = currentState.map(JobExecutionState::payload).map(CursorPayload::lastCursor).orElse(null);
                boolean hasNextPage;

                do {
                    extendLockOrThrow(jobName);

                    PaginatedUserIdsResponse response = userIdsFetcherClient.fetchUserIds(cursor, jobProperties.getPageSize());
                    List<Long> userIds = response.userIds();

                    long successfullySentCount = 0;
                    if (!userIds.isEmpty()) {
                        successfullySentCount = sendUserIdsToKafka(userIds, reportDate, jobRunId, jobName);
                    }

                    hasNextPage = response.pageInfo().hasNextPage();
                    cursor = response.pageInfo().nextPageCursor();

                    jobStateRepository.saveState(jobName, reportDate, JobExecutionState.inProgress(new CursorPayload(cursor)));

                    if (successfullySentCount > 0) {
                        metrics.incrementCounter(Metric.JOB_EVENTS_PUBLISHED, successfullySentCount, Tags.of("job_name", jobName));
                    }

                } while (hasNextPage);

                jobStateRepository.saveState(jobName, reportDate, JobExecutionState.published());
                log.info("Job '{}' for report date {} finished successfully.", jobName, reportDate);

            } catch (Exception e) {
                finalStatus = STATUS_FAILED;
                metrics.incrementCounter(Metric.JOB_RUN_FAILURE, Tags.of("job_name", jobName));
                log.error("Job '{}' for report date {} failed critically. It will be retried on the next schedule.",
                        jobName, reportDate, e);
                // НЕ сохраняем JobStatus.FAILED, чтобы разрешить автоматический ретрай при следующем запуске по cron.
            }
            finally {
                Tags finalTags = Tags.of("job_name", jobName, "status", finalStatus);
                sample.stop(metrics.getTimer(Metric.JOB_RUN_DURATION, finalTags));
            }
        }
    }

    private boolean isJobTerminated(@Nullable JobExecutionState<CursorPayload> currentState, LocalDate reportDate) {
        if (currentState != null) {
            JobStatus status = currentState.status();
            if (status == JobStatus.PUBLISHED) {
                log.info("Job '{}' for report date {} is already PUBLISHED. Skipping.",
                        jobProperties.getJobName(), reportDate);
                return true;
            }
            if (status == JobStatus.FAILED) {
                log.warn("Job '{}' for report date {} has FAILED previously. Manual intervention required. Skipping.",
                        jobProperties.getJobName(), reportDate);
                return true;
            }
        }
        return false;
    }

    private void extendLockOrThrow(String jobName) {
        try {
            Duration extension = jobProperties.getShedlock().getLockAtMostFor().dividedBy(2);
            LockExtender.extendActiveLock(extension, jobProperties.getShedlock().getLockAtLeastFor());
        } catch (LockExtender.NoActiveLockException | LockExtender.LockCanNotBeExtendedException e) {
            log.error("Failed to extend lock for job '{}'. It might have been already released. Aborting.", jobName, e);
            throw e;
        }
    }

    private long sendUserIdsToKafka(List<Long> userIds, LocalDate reportDate, String jobRunId, String jobName) {
        AtomicBoolean hasFailures = new AtomicBoolean(false);
        AtomicLong successCount = new AtomicLong(0);

        List<CompletableFuture<Void>> futures = userIds.stream()
                .map(userId -> {
                    UserSelectedForDailyReportEvent event =
                            new UserSelectedForDailyReportEvent(userId, jobRunId, reportDate);
                    return kafkaTemplate.send(producerTopicName, event)
                            .whenCompleteAsync((result, ex) -> {
                                if (ex != null) {
                                    hasFailures.set(true);
                                    metrics.incrementCounter(Metric.JOB_KAFKA_SEND_FAILURE, Tags.of("job_name", jobName));
                                    log.error("Job '{}': Failed to send event for userId: {}. Cause: {}",
                                            jobName, userId, ex.getMessage());
                                } else {
                                    successCount.incrementAndGet();
                                }
                            }, kafkaCallbackExecutor);
                })
                .map(f -> f.<Void>thenApply(res -> null))
                .toList();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.warn("Job '{}': One or more Kafka send futures completed exceptionally. Check previous logs.", jobName, e);
        }

        if (hasFailures.get()) {
            throw new RuntimeException("Critical failure: unable to send all user ID events to Kafka. " +
                    "Aborting batch to prevent data loss and ensure retry.");
        }

        return successCount.get();
    }
}