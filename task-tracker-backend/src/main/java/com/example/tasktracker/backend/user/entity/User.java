package com.example.tasktracker.backend.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.NaturalId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Сущность, представляющая пользователя системы "Task Tracker".
 * <p>
 * Хранит основную информацию о пользователе, включая его идентификационные данные,
 * креды для аутентификации (хешированный пароль) и аудиторские временные метки.
 * Уникальность пользователя определяется по его email адресу, который также является
 * натуральным идентификатором.
 * </p>
 * <p>
 * Для автоматического заполнения полей {@code createdAt} и {@code updatedAt}
 * используется Spring Data JPA Auditing (см. {@link AuditingEntityListener} и
 * конфигурацию в {@code com.example.tasktracker.backend.config.JpaAuditingConfig}).
 * </p>
 * <p>
 * Реализация методов {@code equals()} и {@code hashCode()} основана на поле {@code email},
 * которое помечено как {@link NaturalId}. Это обеспечивает корректное сравнение
 * и использование сущностей в коллекциях до присвоения сгенерированного {@code id}.
 * </p>
 * <p>
 * Пароль пользователя хранится в хешированном виде.
 * </p>
 *
 * @see com.example.tasktracker.backend.security.details.AppUserDetails UserDetails
 * @see com.example.tasktracker.backend.config.AppConfig Конфигурация аудита
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    /**
     * Уникальный идентификатор пользователя (primary key).
     * Генерируется базой данных с использованием стратегии IDENTITY.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email адрес пользователя.
     * Используется как логин (username) для входа в систему.
     * Является натуральным идентификатором {@link NaturalId} и должен быть уникальным.
     * Не может быть пустым и должен соответствовать формату email.
     * Максимальная длина: 255 символов.
     */
    @NaturalId
    @NotEmpty(message = "{user.entity.email.notEmpty}")
    @Email(message = "{user.entity.email.invalidFormat}")
    @Size(max = 255, message = "{user.entity.email.size}")
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * Хешированный пароль пользователя.
     * Не может быть пустым.
     * Максимальная длина строки в БД: 255 символов (должно быть достаточно для BCrypt хешей).
     * Оригинальный пароль перед хешированием валидируется на уровне DTO ({@code RegisterRequest}).
     */
    @NotEmpty(message = "{user.entity.password.notEmpty}")
    @Size(max = 255, message = "{user.entity.password.size}")
    @Column(name = "password", nullable = false)
    private String password;

    /**
     * Временная метка создания записи пользователя.
     * Заполняется автоматически при первом сохранении сущности благодаря Spring Data JPA Auditing.
     * Не может быть null и не обновляется.
     * Хранится в UTC (тип {@link Instant}).
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Временная метка последнего обновления записи пользователя.
     * Заполняется/обновляется автоматически при каждом сохранении сущности благодаря Spring Data JPA Auditing.
     * Не может быть null.
     * Хранится в UTC (тип {@link Instant}).
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Сравнивает этого пользователя с другим объектом.
     * Два пользователя считаются равными, если их email адреса совпадают (при условии, что email не null).
     * Сравнение основано на натуральном идентификаторе (email).
     *
     * @param o Объект для сравнения.
     * @return {@code true}, если объекты равны, иначе {@code false}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(email, user.email);

    }

    /**
     * Возвращает хеш-код для этого пользователя.
     * Хеш-код основан на email адресе пользователя.
     *
     * @return Хеш-код.
     */
    @Override
    public int hashCode() {
        return Objects.hash(email);
    }

    /**
     * Возвращает строковое представление пользователя (для отладки и логирования).
     * Не включает пароль из соображений безопасности.
     *
     * @return Строковое представление пользователя.
     */
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}