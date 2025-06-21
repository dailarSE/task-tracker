package com.example.tasktracker.backend.internal.scheduler.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурационные свойства для API, поддерживающего сервис-планировщик.
 */
@Component
@ConfigurationProperties(prefix = "app.internal-api.scheduler-support")
@Validated
@Getter
@Setter
public class SchedulerSupportApiProperties {

    /**
     * Конфигурация для эндпоинта, предоставляющего ID пользователей для обработки.
     */
    @Valid
    private UserProcessingIdsEndpoint userProcessingIds = new UserProcessingIdsEndpoint();

    /**
     * Конфигурация для эндпоинта, предоставляющего отчеты по задачам.
     */
    @Valid
    private UserTaskReportEndpoint userTaskReport = new UserTaskReportEndpoint();

    @Getter
    @Setter
    public static class UserProcessingIdsEndpoint {
        /**
         * Размер страницы по умолчанию для пагинации ID пользователей.
         */
        @Positive(message = "{config.validation.positive}")
        private int defaultPageSize = 1000;

        /**
         * Максимально допустимый размер страницы для пагинации ID пользователей.
         */
        @Positive(message = "{config.validation.positive}")
        private int maxPageSize = 5000;

        /**
         * Кастомный метод валидации, проверяющий, что размер по умолчанию не превышает максимальный.
         */
        @AssertTrue(message = "{config.validation.scheduler.pageSize.defaultLessThanMax}")
        @JsonIgnore
        public boolean isDefaultSizeLessThanOrEqualToMaxSize() {
            return defaultPageSize <= maxPageSize;
        }
    }

    @Getter
    @Setter
    public static class UserTaskReportEndpoint {
        /**
         * Максимальная длительность интервала отчета в днях.
         */
        @Positive(message = "{config.validation.positive}")
        private int maxIntervalDays = 3;

        /**
         * Максимальный возраст отчета в днях (насколько "в прошлое" можно запрашивать отчет).
         */
        @Positive(message = "{config.validation.positive}")
        private int maxAgeDays = 30;
    }
}