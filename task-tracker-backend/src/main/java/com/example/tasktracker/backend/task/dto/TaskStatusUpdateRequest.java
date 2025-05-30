// file: task-tracker-backend/src/main/java/com/example/tasktracker/backend/task/dto/TaskStatusUpdateRequest.java
package com.example.tasktracker.backend.task.dto;

import com.example.tasktracker.backend.task.entity.TaskStatus;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusUpdateRequest {

    /**
     * Новый статус для задачи. Обязательное поле.
     * Сообщение об ошибке валидации извлекается из Resource Bundle.
     */
    @NotNull(message = "{task.validation.status.notNull}")
    private TaskStatus status;
}