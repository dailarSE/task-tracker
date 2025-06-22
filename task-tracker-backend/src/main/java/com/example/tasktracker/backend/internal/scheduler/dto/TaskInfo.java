package com.example.tasktracker.backend.internal.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Содержит минимальный набор данных, достаточный для ссылки на задачу или ее отображения в списках,
 * отчетах и других агрегированных представлениях, где полная детализация не требуется.
 */
@Schema(description = "Упрощенная информация о задаче для отчетов")
@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public final class TaskInfo {

    @Schema(description = "ID задачи", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long id;

    @Schema(description = "Заголовок задачи", example = "Завершить отчет по Q2", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;
}