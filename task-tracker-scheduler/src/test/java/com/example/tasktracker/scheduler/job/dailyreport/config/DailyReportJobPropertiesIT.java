package com.example.tasktracker.scheduler.job.dailyreport.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Изолированный тест для проверки корректности загрузки и биндинга
 * свойств в класс {@link DailyReportJobProperties}.
 * Использует ApplicationContextRunner для легковесной загрузки контекста.
 */
public class DailyReportJobPropertiesIT {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    @DisplayName("Контекст с DailyReportJobProperties должен успешно загружаться и биндить свойства")
    void shouldLoadAndBindJobProperties() {
        // Arrange & Act
        this.contextRunner
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(DailyReportJobProperties.class)
                .withPropertyValues(
                        "app.scheduler.jobs.daily-task-reports.enabled=true",
                        "app.scheduler.jobs.daily-task-reports.job-name=test-job-name-from-properties",
                        "app.scheduler.jobs.daily-task-reports.cron=0 0 1 * * *",
                        "app.scheduler.jobs.daily-task-reports.page-size=500",
                        "app.scheduler.jobs.daily-task-reports.kafka-topic-name=test.topic.name",
                        "app.scheduler.jobs.daily-task-reports.shedlock.lock-at-most-for=10m",
                        "app.scheduler.jobs.daily-task-reports.shedlock.lock-at-least-for=1m"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DailyReportJobProperties.class);
                    DailyReportJobProperties props = context.getBean(DailyReportJobProperties.class);

                    assertThat(props.isEnabled()).isTrue();
                    assertThat(props.getJobName()).isEqualTo("test-job-name-from-properties");
                    assertThat(props.getCron()).isEqualTo("0 0 1 * * *");
                    assertThat(props.getPageSize()).isEqualTo(500);
                    assertThat(props.getKafkaTopicName()).isEqualTo("test.topic.name");
                    assertThat(props.getShedlock().getLockAtMostFor().toMinutes()).isEqualTo(10);
                    assertThat(props.getShedlock().getLockAtLeastFor().toMinutes()).isEqualTo(1);
                });
    }
}