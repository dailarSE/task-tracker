package com.example.tasktracker.backend.internal.scheduler.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Представляет собой агрегированный отчет по задачам для пользователя.
 * Задачи группируются пользователя по их статусу (выполненные и невыполненные)
 */
@Schema(description = "Отчет по задачам для одного пользователя")
@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public final class UserTaskReport {

    @Schema(description = "ID пользователя", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long userId;

    @Schema(description = "Email пользователя", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @ArraySchema(schema = @Schema(implementation = TaskInfo.class,
            description = "Список выполненных задач, релевантных для отчета."))
    private final List<TaskInfo> tasksCompleted;

    @ArraySchema(schema = @Schema(implementation = TaskInfo.class,
            description = "Список невыполненных задач, релевантных для отчета."))
    private final List<TaskInfo> tasksPending;


}