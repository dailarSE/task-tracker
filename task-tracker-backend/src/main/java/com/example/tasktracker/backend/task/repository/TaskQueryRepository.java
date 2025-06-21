package com.example.tasktracker.backend.task.repository;

import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReport;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TaskQueryRepository {

    /**
     * Находит и агрегирует отчеты по задачам для указанного списка пользователей
     * за заданный временной интервал.
     *
     * @param userIds Список ID пользователей.
     * @param from    Начало временного интервала (включительно).
     * @param to      Конец временного интервала (исключительно).
     * @return Список отчетов {@link UserTaskReport}.
     */
    List<UserTaskReport> findTaskReportsForUsers(List<Long> userIds, Instant from, Instant to);
}