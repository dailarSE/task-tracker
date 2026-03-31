package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "app.email")
@Validated
@Getter
@Setter
public class EmailSenderProperties {

    /**
     * Адрес, от имени которого будут отправляться письма (поле From).
     */
    @NotBlank
    @Email
    private String senderAddress;

    @NotBlank
    private String kafkaTopic;

    @NotBlank
    private String retryTopic;

    @NotBlank
    private String dltTopic;

    @NotNull
    private MessageValidityProperties messageValidity = new MessageValidityProperties();

    @Getter
    @Setter
    public static class MessageValidityProperties {
        /**
         * TTL для каждого типа шаблона.
         * Если шаблон не найден, используется TTL по умолчанию.
         */
        private Map<TemplateType, Duration> policies = new EnumMap<>(TemplateType.class);

        /**
         * TTL по умолчанию для шаблонов, не указанных в мапе.
         */
        private Duration defaultDuration = Duration.ofDays(7);

        public Duration getDurationFor(TemplateType type) {
            return policies.getOrDefault(type, defaultDuration);
        }
    }
}