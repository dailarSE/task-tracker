package com.example.tasktracker.scheduler.job.dailyreport.config;

import com.example.tasktracker.scheduler.config.SchedulerAppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe конфигурационные свойства для джобы "Daily Task Reports".
 * Читаются из префикса "app.scheduler.jobs.daily-task-reports".
 */
@Configuration
@ConfigurationProperties(prefix = "app.scheduler.jobs.daily-task-reports")
@Validated
@Getter
@Setter
public class DailyReportJobProperties {

    /**
     * Включена ли джоба. Позволяет отключать джобу через конфигурацию, не меняя код.
     */
    private boolean enabled = true;

    /**
     * Уникальное, персистентное имя джобы.
     * <p>
     * <strong>ВНИМАНИЕ:</strong> Это имя используется как идентификатор в Redis и ShedLock.
     * Изменение этого значения приведет к созданию нового, чистого состояния
     * для джобы и потере истории предыдущих запусков.
     * </p>
     */
    @NotBlank
    private String jobName;

    /**
     * CRON-выражение для запуска джобы.
     */
    @NotBlank
    private String cron;

    /**
     * Размер страницы (количество ID), запрашиваемый у Backend API за один вызов.
     */
    @Positive
    private int pageSize = 1000;

    /**
     * Имя топика для публикации событий.
     */
    @NotBlank
    private String kafkaTopicName;

    /**
     * Настройки для распределенной блокировки ShedLock для этой конкретной джобы.
     */
    @Valid
    @NotNull
    private SchedulerAppProperties.ShedLockProperties shedlock;
}