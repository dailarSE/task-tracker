package com.example.tasktracker.scheduler.job.dto;

import lombok.NonNull;
import org.springframework.lang.Nullable;

/**
 * Обобщенное DTO для хранения состояния выполнения джобы.
 *
 * @param status       Текущий статус выполнения.
 * @param errorMessage Сообщение об ошибке, если статус FAILED.
 * @param payload      Специфичные для джобы данные (например, курсор).
 * @param schemaVersion Версия схемы этого объекта.
 */
public record JobExecutionState<T>(
        @NonNull JobStatus status,
        @Nullable String errorMessage,
        @Nullable T payload,
        int schemaVersion
) {
    /**
     * Текущая версия схемы для этого DTO.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static <T> JobExecutionState<T> inProgress(@Nullable T payload) {
        return new JobExecutionState<>(JobStatus.IN_PROGRESS, null, payload, CURRENT_SCHEMA_VERSION);
    }

    public static <T> JobExecutionState<T> published() {
        return new JobExecutionState<>(JobStatus.PUBLISHED, null, null, CURRENT_SCHEMA_VERSION);
    }

    public static <T> JobExecutionState<T> failed(@NonNull String errorMessage) {
        return new JobExecutionState<>(JobStatus.FAILED, errorMessage, null, CURRENT_SCHEMA_VERSION);
    }
}