package com.example.tasktracker.scheduler.job;

import lombok.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Стандартная реализация {@link JobStateKeyProvider} для Redis.
 * <p>
 * Генерирует ключи в следующем формате:
 * <ul>
 *     <li>Основной ключ HASH'а: {@code job_state:{jobName}}</li>
 *     <li>Ключ поля: {@code YYYY-MM-DD}</li>
 * </ul>
 * </p>
 */
@Component
public class DefaultJobStateKeyProvider implements JobStateKeyProvider {

    static final String KEY_PREFIX = "job_state:";

    @Override
    public String getHashKey(@NonNull String jobName) {
        return KEY_PREFIX + jobName;
    }

    @Override
    public String getHashField(@NonNull LocalDate date) {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}