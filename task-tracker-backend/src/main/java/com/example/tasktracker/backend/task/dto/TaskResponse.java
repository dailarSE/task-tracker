package com.example.tasktracker.backend.task.dto;

import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

/**
 * DTO (Data Transfer Object) для представления задачи в ответах API.
 * Содержит полную информацию о задаче.
 */
@Getter
@RequiredArgsConstructor
public class TaskResponse {

    /**
     * Уникальный идентификатор задачи.
     */
    private final Long id;

    /**
     * Заголовок задачи.
     */
    private final String title;

    /**
     * Описание задачи (может быть null).
     */
    private final String description;

    /**
     * Статус задачи.
     */
    private final TaskStatus status;

    /**
     * Временная метка создания задачи.
     */
    private final Instant createdAt;

    /**
     * Временная метка последнего обновления задачи.
     */
    private final Instant updatedAt;

    /**
     * Временная метка завершения задачи (может быть null).
     */
    private final Instant completedAt;

    /**
     * Идентификатор пользователя, которому принадлежит задача.
     */
    private final Long userId;

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