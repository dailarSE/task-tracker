package com.example.tasktracker.backend.task.dto;

import com.example.tasktracker.backend.task.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO (Data Transfer Object) для запроса на обновление статуса задачи.
 * Используется в операции PATCH для частичного обновления задачи,
 * затрагивая только ее статус.
 */
@Schema(description = "DTO для частичного обновления задачи (только статус)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusUpdateRequest {

    /**
     * Новый статус для задачи. Обязательное поле.
     * Сообщение об ошибке валидации извлекается из Resource Bundle.
     */
    @Schema(description = "Новый статус для задачи.", example = "COMPLETED")
    @NotNull(message = "{task.validation.status.notNull}")
    private TaskStatus status;
}