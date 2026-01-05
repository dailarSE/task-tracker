package com.example.tasktracker.scheduler.job.dailyreport;

import com.example.tasktracker.scheduler.common.MdcKeys;
import com.example.tasktracker.scheduler.job.dailyreport.config.DailyReportJobProperties;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
public class DailyTaskReportJobTrigger {

    private final LockingTaskExecutor lockingTaskExecutor;
    private final DailyTaskReportProducerJob executionService;
    private final DailyReportJobProperties jobProperties;
    private final Clock clock;

    public DailyTaskReportJobTrigger(LockingTaskExecutor lockingTaskExecutor,
                                     DailyTaskReportProducerJob executionService,
                                     DailyReportJobProperties jobProperties,
                                     Clock clock) {
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.executionService = executionService;
        this.jobProperties = jobProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.scheduler.jobs.daily-task-reports.cron}")
    public void trigger() {
        final LocalDate reportDate = LocalDate.now(clock).minusDays(1); // Отчет всегда за вчера
        final String jobName = jobProperties.getJobName();

        try (
                MDC.MDCCloseable ignoredJobName = MDC.putCloseable(MdcKeys.JOB_NAME, jobName);
                MDC.MDCCloseable ignoredReportDate = MDC.putCloseable(MdcKeys.REPORT_DATE,
                        reportDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
        ) {
            final LockConfiguration lockConfig = new LockConfiguration(
                    clock.instant(),
                    jobName,
                    jobProperties.getShedlock().getLockAtMostFor(),
                    jobProperties.getShedlock().getLockAtLeastFor()
            );

            log.debug("Triggering execution with lock configuration: {}", lockConfig);
            lockingTaskExecutor.executeWithLock(
                    (Runnable) () -> executionService.execute(reportDate),
                    lockConfig
            );
        }
    }
}