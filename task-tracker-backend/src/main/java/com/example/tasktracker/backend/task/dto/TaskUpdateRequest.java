package com.example.tasktracker.backend.task.dto;

import com.example.tasktracker.backend.task.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for updating an existing task.
 */
@Schema(description = "DTO для полного обновления существующей задачи")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskUpdateRequest {

    /**
     * The new title for the task. Must not be blank.
     * Max length: 255 characters.
     */
    @Schema(description = "Новый заголовок задачи.", example = "Купить молоко и хлеб")
    @NotBlank(message = "{task.validation.title.notBlank}")
    @Size(max = 255, message = "{task.validation.title.size}")
    private String title;

    /**
     * The new description for the task. Can be null.
     * Max length: 1000 characters.
     */
    @Schema(description = "Новое описание задачи.", example = "Молоко 2.5%, хлеб 'Бородинский'")
    @Size(max = 1000, message = "{task.validation.description.size}")
    private String description;

    /**
     * The new status for the task. Must not be null.
     */
    @Schema(description = "Новый статус задачи.", example = "PENDING")
    @NotNull(message = "{task.validation.status.notNull}")
    private TaskStatus status;

    /**
     * The current version of the task entity, required for optimistic locking.
     */
    @Schema(description = "Текущая версия задачи для оптимистической блокировки.", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{task.validation.version.notNull}")
    private Integer version;
}