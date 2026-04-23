package com.example.tasktracker.emailsender.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
@Getter
@Setter
public class AppProperties {

    /**
     * Уникальный идентификатор инстанса приложения.
     * Используется для идемпотентности и распределенных блокировок.
     */
    @NotBlank
    private String instanceId;

    private ObservationProperties observability = new ObservationProperties();

    @Getter
    @Setter
    public static class ObservationProperties {
        private boolean enabled = true;
        private boolean captureMessageSizes = false;

        private String defaultSmtpProviderName = "default-smtp-provider";
        /**
         * Список доменов, которые считаются "крупными".
         * Только они попадут в теги метрик. Остальные станут "other".
         */
        private Set<String> knownDomains = Set.of(
                "gmail.com", "yahoo.com", "outlook.com", "icloud.com", "yandex.ru", "mail.ru"
        );
    }
}
