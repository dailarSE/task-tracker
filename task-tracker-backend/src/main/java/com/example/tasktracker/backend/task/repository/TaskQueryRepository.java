package com.example.tasktracker.backend.task.repository;

import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReport;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Репозиторий для генерации сводных отчетов по задачам пользователей.
 * <p>
 * Определяет контракт для получения агрегированной информации.
 * </p>
 */
@Repository
public interface TaskQueryRepository {

    /**
     * Формирует и возвращает отчеты по задачам для указанного списка пользователей
     * за заданный временной интервал.
     * <p>
     * Отчет для каждого пользователя содержит списки задач, релевантных для
     * данного периода, сгруппированные по их статусу. Если для пользователя
     * нет релевантных задач, отчет по нему не возвращается.
     * </p>
     *
     * @param userIds Список ID пользователей, для которых нужно сформировать отчеты. Не должен быть null.
     * @param from    Начало временного интервала (включительно). Не должен быть null.
     * @param to      Конец временного интервала (исключительно). Не должен быть null.
     * @return Неизменяемый список отчетов {@link UserTaskReport}. Никогда не возвращает null;
     *         возвращает пустой список, если {@code userIds} пуст или нет релевантных данных.
     */
    List<UserTaskReport> generateTaskReportsForUsers(
            @NonNull List<Long> userIds,
            @NonNull Instant from,
            @NonNull Instant to);
}