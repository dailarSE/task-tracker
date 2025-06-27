package com.example.tasktracker.scheduler.client.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Запрос на формирование отчетов по задачам для списка пользователей
 * за определенный временной интервал.
 */
public record UserTaskReportRequest(@NotNull() List<Long> userIds, @NotNull() Instant from, @NotNull() Instant to) {}