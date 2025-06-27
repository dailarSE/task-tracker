package com.example.tasktracker.scheduler.client.dto;

/**
 * Инкапсулирует метаданные для навигации по страницам в пагинированном ответе.
 * <p>
 * Предоставляет информацию о том, есть ли следующая страница данных,
 * и значение курсора, которое необходимо использовать для ее получения.
 * </p>
 */
public record PageInfo(boolean hasNextPage, String nextPageCursor) {}