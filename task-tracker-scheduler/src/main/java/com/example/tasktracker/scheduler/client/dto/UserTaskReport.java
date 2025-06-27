package com.example.tasktracker.scheduler.client.dto;

import java.util.List;

/**
 * Представляет собой агрегированный отчет по задачам для пользователя.
 * Задачи группируются пользователя по их статусу (выполненные и невыполненные)
 */
public record UserTaskReport(Long userId, String email, List<TaskInfo> tasksCompleted, List<TaskInfo> tasksPending) {}