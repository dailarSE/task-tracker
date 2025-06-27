package com.example.tasktracker.scheduler.client.dto;

/**
 * Содержит минимальный набор данных, достаточный для ссылки на задачу или ее отображения в списках,
 * отчетах и других агрегированных представлениях, где полная детализация не требуется.
 */
public record TaskInfo(Long id, String title) {}