package com.example.tasktracker.backend.kafka.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Сущность для хранения информации о командах на отправку email,
 * которые не удалось немедленно отправить в Kafka.
 * <p>
 * Эта таблица служит fallback-механизмом, позволяя сохранить команду
 * для последующих попыток отправки и избежать потери события.
 * </p>
 */
@Entity
@Table(name = "undelivered_email_commands", indexes = {
        @Index(name = "idx_undelivered_email_commands_initial_attempt_at", columnList = "initial_attempt_at")
})
@Getter
@Setter
@NoArgsConstructor
public class UndeliveredEmailCommand {

    /**
     * Уникальный идентификатор записи.
     * Используется простой IDENTITY для этой вспомогательной таблицы.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email получателя. */
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    /** Идентификатор шаблона письма. */
    @Column(name = "template_id", nullable = false)
    private String templateId;

    /**
     * Контекст шаблона, сериализованный в JSON-строку.
     * Хранится как TEXT для универсальности.
     */
    @Column(name = "template_context_json", nullable = false, columnDefinition = "TEXT")
    private String templateContextJson;

    /** Языковой тег локали. */
    @Column(name = "locale")
    private String locale;

    /** Идентификатор пользователя. */
    @Column(name = "user_id")
    private String userId;

    /**
     * Идентификатор корреляции (например, Trace ID).
     * Должен быть уникальным для предотвращения дублирования записи
     * об одном и том же неотправленном событии.
     */
    @Column(name = "correlation_id", nullable = false, unique = true)
    private String correlationId;

    /** Исходный топик Kafka, в который предназначалось сообщение. */
    @Column(name = "kafka_topic", nullable = false)
    private String kafkaTopic;

    /** Время создания этой записи (первой неудачной попытки отправить в Kafka). */
    @Column(name = "initial_attempt_at", nullable = false, updatable = false)
    private Instant initialAttemptAt;

    /** Время последней попытки переотправки из этой таблицы (если будет реализовано). */
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    /**
     * Счетчик попыток переотправки из этой таблицы.
     * Начинается с 0, инкрементируется при каждой новой попытке.
     */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /** Сообщение о последней ошибке при попытке отправки в Kafka. */
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;
}