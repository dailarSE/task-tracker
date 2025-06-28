package com.example.tasktracker.scheduler.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.scheduler")
@Validated
@Getter
@Setter
public class SchedulerAppProperties {

    /** Настройки для HTTP-клиента, подключающегося к backend. */
    @Valid
    private BackendClientProperties backendClient = new BackendClientProperties();

    /** Настройки для основной задачи, запускаемой по расписанию. */
    @Valid
    private ScheduledJobProperties scheduledJob = new ScheduledJobProperties();

    /** Настройки для ShedLock. */
    @Valid
    private ShedLockProperties shedlock = new ShedLockProperties();

    /** Все настройки, связанные с Kafka. */
    @Valid
    private KafkaProperties kafka = new KafkaProperties();

    // ================== Вложенные классы для группировки ==================

    @Getter @Setter
    public static class BackendClientProperties {
        @NotBlank
        private String url;
        @NotBlank
        private String apiKey;
        @DurationMin(nanos = 1)
        private Duration connectTimeout = Duration.ofSeconds(5);
        @DurationMin(nanos = 1)
        private Duration readTimeout = Duration.ofSeconds(30);
        /** Настройки повторных попыток для HTTP-клиента. */
        @Valid
        private RetryProperties retry = new RetryProperties();
    }

    @Getter @Setter
    public static class ScheduledJobProperties {
        @NotBlank
        private String cron;
        @Positive
        private int pageSize = 1000;
    }

    @Getter @Setter
    public static class ShedLockProperties {
        @NotBlank
        private String lockName = "task_report_producer_job";
        @DurationMin(nanos = 1)
        private Duration lockAtMostFor = Duration.ofMinutes(30);
        @DurationMin(nanos = 1)
        private Duration lockAtLeastFor = Duration.ofMinutes(1);
    }

    @Getter @Setter
    public static class KafkaProperties {
        @Valid
        private InternalProducerProperties internalProducer = new InternalProducerProperties();
        @Valid
        private InternalConsumerProperties internalConsumer = new InternalConsumerProperties();
        @Valid
        private ReportsProducerProperties reportsProducer = new ReportsProducerProperties();

        @Getter @Setter
        public static class InternalProducerProperties {
            @NotBlank
            private String topicName;
        }

        @Getter @Setter
        public static class ReportsProducerProperties {
            @NotBlank
            private String topicName;
        }

        @Getter @Setter
        public static class InternalConsumerProperties {
            @NotBlank
            private String topicName;
            @NotBlank
            private String groupId;
            @Positive
            private int maxPollRecords = 100;
            @DurationMin(nanos = 1)
            private Duration pollTimeout = Duration.ofMillis(3000);
            @Positive
            private int concurrency = 3;
            /** Настройки повторных попыток и отправки в DLT для Kafka-консьюмера. */
            @Valid
            private RetryAndDltProperties retryAndDlt = new RetryAndDltProperties();
        }
    }

    /** Общий класс для настройки ретраев с экспоненциальной задержкой. */
    @Getter @Setter
    public static class RetryProperties {
        /** Включить механизм повторных попыток. */
        private boolean enabled = true;
        /** Максимальное количество попыток (включая первую). */
        @Positive
        private int maxAttempts = 3;
        /** Начальный интервал между попытками. */
        @Positive
        private long initialIntervalMs = 2000L;
        /** Множитель для экспоненциальной задержки. */
        @Positive
        private double multiplier = 2.0;
        /** Максимальный интервал между попытками. */
        @Positive
        private long maxIntervalMs = 10000L;
    }

    /** Класс для настройки ретраев и DLT Kafka-консьюмера. */
    @Getter @Setter
    public static class RetryAndDltProperties extends RetryProperties {
        /** Настройки для Dead Letter Topic. */
        @Valid
        private DltProperties dlt = new DltProperties();
    }

    @Getter @Setter
    public static class DltProperties {
        /** Включить отправку в Dead Letter Topic после исчерпания всех попыток. */
        private boolean enabled = true;
    }
}