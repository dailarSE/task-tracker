package com.example.tasktracker.backend.internal.scheduler.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Представляет одну страницу (порцию) данных в пагинированном ответе,
 * содержащем идентификаторы пользователей для последующей обработки.
 */
@Schema(description = "Постраничный ответ со списком ID пользователей")
@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public final class PaginatedUserIdsResponse {

    @ArraySchema(schema = @Schema(description = "Список ID пользователей на текущей странице.",
            type = "integer", format = "int64", example = "101"))
    private final List<Long> userIds;

    @Schema(description = "Информация о пагинации для запроса следующей страницы.")
    private final PageInfo pageInfo;
}