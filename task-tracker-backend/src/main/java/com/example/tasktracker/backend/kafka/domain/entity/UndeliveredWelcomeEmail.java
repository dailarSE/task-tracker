package com.example.tasktracker.backend.kafka.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;

/**
 * Запись о неотправленном приветственном email-уведомлении.
 * <p>
 * Эта таблица служит fallback-механизмом для одного конкретного события:
 * отправки приветственного письма при регистрации. Первичным ключом
 * является ID пользователя ({@code userId}), что гарантирует уникальность
 * записи для каждого пользователя.
 * </p>
 */
@Entity
@Table(name = "undelivered_welcome_emails")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class UndeliveredWelcomeEmail {

    /**
     * Идентификатор пользователя, которому не было отправлено письмо.
     * Является первичным ключом этой таблицы.
     */
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /**
     * Email адрес получателя. Дублируется здесь для удобства,
     * чтобы не делать JOIN с таблицей users при переотправке.
     */
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    /**
     * Языковой тег локали (например, "en-US", "ru-RU"), который
     * должен использоваться для отправки письма.
     */
    @Column(name = "locale", nullable = false)
    private String locale;

    /**
     * OTel Trace ID последней неудачной попытки отправки.
     * Используется для диагностики и связи с логами/трейсами.
     */
    @Column(name = "last_attempt_trace_id")
    private String lastAttemptTraceId;

    /**
     * Время создания этой записи (первой неудачной попытки).
     * Устанавливается вручную в сервисном слое.
     */
    @Column(name = "initial_attempt_at", nullable = false, updatable = false)
    private Instant initialAttemptAt;

    /**
     * Время последней попытки переотправки.
     * При первой записи равно {@code createdAt}.
     */
    @Column(name = "last_attempt_at", nullable = false)
    private Instant lastAttemptAt;

    /**
     * Счетчик попыток переотправки.
     * Начинается с 0 для первой неудачной попытки.
     */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /**
     * Сообщение о последней ошибке.
     */
    @Column(name = "delivery_error_message", columnDefinition = "TEXT")
    private String deliveryErrorMessage;

    public UndeliveredWelcomeEmail(Long userId, String recipientEmail, String locale, Instant initialAttemptAt, Instant lastAttemptAt) {
        this.userId = userId;
        this.recipientEmail = recipientEmail;
        this.locale = locale;
        this.initialAttemptAt = initialAttemptAt;
        this.lastAttemptAt = lastAttemptAt;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        UndeliveredWelcomeEmail that = (UndeliveredWelcomeEmail) o;
        return getUserId() != null && Objects.equals(getUserId(), that.getUserId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}