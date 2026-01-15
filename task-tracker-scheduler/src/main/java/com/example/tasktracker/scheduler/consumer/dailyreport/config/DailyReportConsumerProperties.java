package com.example.tasktracker.scheduler.consumer.dailyreport.config;

import com.example.tasktracker.scheduler.config.SchedulerAppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.scheduler.consumers.daily-report")
@Validated
@Getter
@Setter
public class DailyReportConsumerProperties {
    private boolean enabled = true;
    @NotBlank
    private String topicName;
    @NotBlank
    private String sinkTopicName;
    @NotBlank
    private String groupId;
    @Positive
    private int maxPollRecords = 500;
    @DurationMin(millis = 100)
    private Duration pollTimeout = Duration.ofMillis(3000);
    @Positive
    private int concurrency = 3;
    @Positive
    private int fetchMaxWaitMs = 500;
    @Valid
    private SchedulerAppProperties.RetryAndDltProperties retryAndDlt = new SchedulerAppProperties.RetryAndDltProperties();
}
