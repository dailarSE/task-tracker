// file: src/main/java/com/example/tasktracker/backend/internal/scheduler/dto/UserTaskReportRequest.java
package com.example.tasktracker.backend.internal.scheduler.dto;

import com.example.tasktracker.backend.internal.scheduler.validation.ValidReportInterval;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

/**
 * Запрос на формирование отчетов по задачам для списка пользователей
 * за определенный временной интервал.
 */
@Schema(description = "Запрос на формирование отчетов по задачам")
@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@ValidReportInterval
public final class UserTaskReportRequest {

    @ArraySchema(schema = @Schema(description = "Список ID пользователей для обработки.",
            type = "integer", format = "int64", example = "101"))
    @NotNull(message = "User ID list cannot be null.")
    private final List<Long> userIds;

    @Schema(description = "Начало временного интервала (включительно) для отчета.",
            example = "2025-06-21T00:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Report start time ('from') cannot be null.")
    private final Instant from;

    @Schema(description = "Конец временного интервала (исключительно) для отчета.",
            example = "2025-06-22T00:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Report end time ('to') cannot be null.")
    private final Instant to;
}