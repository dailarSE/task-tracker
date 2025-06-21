package com.example.tasktracker.backend.internal.scheduler.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO, представляющий отчет по задачам для одного пользователя,
 * сгенерированный для сервиса-планировщика.
 */
@Schema(description = "Отчет по задачам для одного пользователя")
@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
public final class UserTaskReport {

    @Schema(description = "ID пользователя", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long userId;

    @Schema(description = "Email пользователя", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @ArraySchema(schema = @Schema(implementation = TaskInfo.class,
            description = "Список недавно выполненных задач (отсортированы по времени завершения)."))
    private final List<TaskInfo> tasksCompleted = new ArrayList<>();

    @ArraySchema(schema = @Schema(implementation = TaskInfo.class,
            description = "Список самых старых невыполненных задач (не более 5, отсортированы по времени создания)."))
    private final List<TaskInfo> tasksPending = new ArrayList<>();


}