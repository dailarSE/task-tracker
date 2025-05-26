package com.example.tasktracker.backend.task.dto;

import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.user.entity.User;
import lombok.*;

import java.time.Instant;

/**
 * DTO (Data Transfer Object) для представления задачи в ответах API.
 * Содержит полную информацию о задаче.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    /**
     * Уникальный идентификатор задачи.
     */
    private Long id;

    /**
     * Заголовок задачи.
     */
    private String title;

    /**
     * Описание задачи (может быть null).
     */
    private String description;

    /**
     * Статус задачи.
     */
    private TaskStatus status;

    /**
     * Временная метка создания задачи.
     */
    private Instant createdAt;

    /**
     * Временная метка последнего обновления задачи.
     */
    private Instant updatedAt;

    /**
     * Временная метка завершения задачи (может быть null).
     */
    private Instant completedAt;

    /**
     * Идентификатор пользователя, которому принадлежит задача.
     */
    private Long userId;

    /**
     * Статический фабричный метод для преобразования сущности {@link Task} в {@link TaskResponse}.
     * <p>
     * <strong>Предполагается, что переданная сущность {@code Task} уже прошла
     * все необходимые этапы валидации (например, через аннотации Jakarta Bean Validation
     * при сохранении в базу данных или через явную валидацию в сервисном слое)
     * и является консистентной.</strong>
     * </p>
     * <p>
     * Этот метод извлекает ID пользователя из связанной сущности {@link User}.
     * Он ожидает, что {@code task.getUser()} не вернет {@code null}, и что
     * {@code task.getUser().getId()} также не будет {@code null}, так как поле {@code user}
     * в {@link Task} является обязательным и у сохраненного пользователя всегда есть ID.
     * </p>
     *
     * @param task Сущность задачи, которая уже была сохранена в базу данных или, по крайней мере,
     *             полностью и корректно сформирована. Не должна быть {@code null}.
     * @return Объект {@link TaskResponse}.
     * @throws NullPointerException если {@code task} равен {@code null}.
     * @throws IllegalStateException если {@code task.getUser()} равен {@code null} или
     *                               {@code task.getUser().getId()} равен {@code null},
     *                               что указывает на неконсистентное состояние переданной сущности {@code Task}.
     */
    public static TaskResponse fromEntity(@NonNull Task task) {
        if (task.getUser() == null || task.getUser().getId() == null) {
            throw new IllegalStateException("Task entity (ID: " + task.getId() + ") is missing a valid associated User " +
                    "or User ID for mapping to TaskResponse.");
        }
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt(),
                task.getUser().getId()
        );
    }
}