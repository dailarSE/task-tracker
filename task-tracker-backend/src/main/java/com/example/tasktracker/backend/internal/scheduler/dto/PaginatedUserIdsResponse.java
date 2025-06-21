package com.example.tasktracker.backend.internal.scheduler.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * DTO для ответа API, содержащего пагинированный список ID пользователей.
 */
@Schema(description = "Пагинированный ответ со списком ID пользователей")
@Getter
@RequiredArgsConstructor
@ToString
@EqualsAndHashCode
public class PaginatedUserIdsResponse {

    @ArraySchema(schema = @Schema(description = "Список ID пользователей на текущей странице.",
            type = "integer", format = "int64", example = "101"))
    private final List<Long> data;

    @Schema(description = "Информация о пагинации для запроса следующей страницы.")
    private final PageInfo pageInfo;
}