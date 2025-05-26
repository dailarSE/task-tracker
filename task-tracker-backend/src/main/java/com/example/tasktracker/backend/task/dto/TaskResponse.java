package com.example.tasktracker.backend.task.dto;

import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import jakarta.validation.Valid;
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
     *
     * @param task Сущность задачи.
     * @return Объект {@link TaskResponse}.
     * @throws NullPointerException если {@code task} равен null.
     */
    public static TaskResponse fromEntity(@NonNull @Valid Task task) {
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