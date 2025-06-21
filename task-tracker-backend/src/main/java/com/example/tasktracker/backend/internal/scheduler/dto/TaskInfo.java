package com.example.tasktracker.backend.internal.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Упрощенное DTO для представления информации о задаче в отчетах планировщика.
 * Является неизменяемым (immutable).
 */
@Schema(description = "Упрощенная информация о задаче для отчетов")
@Getter
@RequiredArgsConstructor
@ToString
@EqualsAndHashCode
public final class TaskInfo {

    @Schema(description = "ID задачи", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long id;

    @Schema(description = "Заголовок задачи", example = "Завершить отчет по Q2", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;
}