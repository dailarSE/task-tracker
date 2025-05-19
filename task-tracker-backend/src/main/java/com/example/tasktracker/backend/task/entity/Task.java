package com.example.tasktracker.backend.task.entity;

import com.example.tasktracker.backend.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Сущность, представляющая задачу в системе "Task Tracker".
 * Каждая задача принадлежит определенному пользователю.
 * Включает аудиторские поля для отслеживания времени создания и обновления,
 * а также время завершения задачи.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"user"})
@EntityListeners(AuditingEntityListener.class)
public class Task {

    /**
     * Уникальный идентификатор задачи (primary key).
     * Генерируется с использованием sequence "tasks_id_seq" с allocationSize = 50.
     */
    @Id
    @SequenceGenerator(name = "tasks_id_gen", sequenceName = "tasks_id_seq")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tasks_id_gen")
    private Long id;

    /**
     * Заголовок задачи. Обязательное поле.
     * Максимальная длина: 255 символов.
     */
    @NotBlank(message = "{task.entity.title.notBlank}")
    @Size(min = 1, max = 255, message = "{task.entity.title.size}")
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Описание задачи. Опциональное поле.
     * Максимальная длина: 1000 символов.
     */
    @Size(max = 1000, message = "{task.entity.description.size}")
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Статус задачи (например, PENDING, COMPLETED).
     * Хранится как строка в базе данных. Обязательное поле.
     */
    @NotNull(message = "{task.entity.status.notNull}")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TaskStatus status;

    /**
     * Временная метка создания задачи.
     * Заполняется автоматически. Хранится в UTC.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Временная метка последнего обновления задачи.
     * Обновляется автоматически. Хранится в UTC.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Временная метка завершения задачи.
     * Может быть null, если задача еще не завершена. Хранится в UTC.
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Пользователь, которому принадлежит эта задача.
     * Связь {@link ManyToOne}. Владелец не может быть изменен после создания.
     */
    @NotNull(message = "{task.entity.user.notNull}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;


    /**
     * Определяет, равен ли данный объект {@code Task} другому объекту.
     * <p>
     * Две сущности {@code Task} считаются равными, если они:
     * <ul>
     *     <li>Являются одним и тем же экземпляром в памяти.</li>
     *     <li>Имеют одинаковый ненулевой идентификатор ({@code id}).</li>
     * </ul>
     * Этот метод корректно обрабатывает случаи с Hibernate-прокси,
     * сравнивая эффективные классы объектов.
     * </p>
     * <p>
     * Если {@code id} равен {@code null} (сущность еще не сохранена),
     * сравнение будет основано на идентичности объектов в памяти.
     * </p>
     *
     * @param o объект для сравнения.
     * @return {@code true}, если объекты равны, иначе {@code false}.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ?
                ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ?
                ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Task task = (Task) o;
        return getId() != null && Objects.equals(getId(), task.getId());
    }

    /**
     * Возвращает хеш-код для данной сущности {@code Task}.
     * @return хеш-код.
     */
    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ?
                ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() :
                getClass().hashCode();
    }
}