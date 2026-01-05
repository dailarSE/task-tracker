package com.example.tasktracker.scheduler.job.dailyreport.config;

import com.example.tasktracker.scheduler.config.AppConfig;
import com.example.tasktracker.scheduler.job.JobStateRepository;
import com.example.tasktracker.scheduler.job.dailyreport.DailyTaskReportJobTrigger;
import com.example.tasktracker.scheduler.job.dailyreport.DailyTaskReportProducerJob;
import com.example.tasktracker.scheduler.job.dailyreport.client.UserIdsFetcherClient;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.util.concurrent.Executor;

@Configuration
@ConditionalOnProperty(name = "app.scheduler.jobs.daily-task-reports.enabled", havingValue = "true", matchIfMissing = true)
public class DailyReportJobConfiguration {

    @Bean
    public DailyTaskReportProducerJob dailyTaskReportProducerJob(
            JobStateRepository jobStateRepository,
            UserIdsFetcherClient userIdsFetcherClient,
            KafkaTemplate<String, UserSelectedForDailyReportEvent> kafkaTemplate,
            @Qualifier(AppConfig.KAFKA_CALLBACK_EXECUTOR) Executor kafkaCallbackExecutor,
            DailyReportJobProperties jobProperties,
            MetricsReporter metrics) {
        return new DailyTaskReportProducerJob(jobStateRepository,
                userIdsFetcherClient,
                kafkaTemplate,
                kafkaCallbackExecutor,
                jobProperties,
                metrics);
    }

    @Bean
    public DailyTaskReportJobTrigger dailyTaskReportJobTrigger(
            LockingTaskExecutor lockingTaskExecutor,
            DailyTaskReportProducerJob producerJob,
            DailyReportJobProperties jobProperties,
            Clock clock) {
        return new DailyTaskReportJobTrigger(lockingTaskExecutor, producerJob, jobProperties, clock);
    }
}