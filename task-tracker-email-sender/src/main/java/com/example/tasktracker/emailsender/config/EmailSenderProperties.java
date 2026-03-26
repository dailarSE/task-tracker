package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    private EmailSenderProperties.MessageValidityProperties messageValidity = new MessageValidityProperties();

    @Valid
    private IdempotencyProperties idempotency = new IdempotencyProperties();

    @Valid
    private RateLimitProperties rateLimit = new RateLimitProperties();

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

    @Getter
    @Setter
    public static class IdempotencyProperties {
        /**
         * TTL для короткой блокировки на время обработки сообщения (статус PROCESSING).
         */
        @NotNull
        private Duration processingLockDuration = Duration.ofMinutes(5);

        /**
         * Карта настроек TTL для хранения статуса успешной обработки (SENT) или фатальной ошибки (FAILED)
         * для каждого типа шаблона.
         * <p>
         * Ключ: {@link TemplateType}
         * Значение: {@link Duration} (сколько времени хранить информацию о том, что письмо уже отправлено).
         * </p>
         */
        private Map<TemplateType, Duration> retentionPolicies = new EnumMap<>(TemplateType.class);

        /**
         * Возвращает настроенный TTL для указанного типа шаблона.
         * Если настройка отсутствует, возвращает дефолтное значение.
         *
         * @param type тип шаблона.
         * @return Duration TTL.
         */
        public Duration getTtlFor(TemplateType type) {
            return retentionPolicies.getOrDefault(type, Duration.ofDays(7));
        }
    }

    @Getter
    @Setter
    public static class RateLimitProperties {
        /**
         * Глобальный лимит (запросов в секунду) на весь кластер.
         */
        @Positive
        private long globalRps = 200;

        /**
         * Размер пачки токенов, запрашиваемой за один раз.
         * Влияет на плавность (чем меньше, тем плавнее).
         */
        @Positive
        private int tokenChunkSize = 20;

        /**
         * Максимальное время ожидания квоты.
         */
        @NotNull
        private Duration acquisitionTimeout = Duration.ofSeconds(30);

        /**
         * Hard limit на обработку одного батча
         */

        @NotNull
        private Duration batchProcessingTimeout = Duration.ofSeconds(60);
    }
}