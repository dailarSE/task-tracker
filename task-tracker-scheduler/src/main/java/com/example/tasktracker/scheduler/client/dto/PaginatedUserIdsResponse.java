package com.example.tasktracker.scheduler.client.dto;

import java.util.List;

/**
 * Представляет одну страницу (порцию) данных в пагинированном ответе,
 * содержащем идентификаторы пользователей для последующей обработки.
 */
public record PaginatedUserIdsResponse(List<Long> userIds, PageInfo pageInfo) {}