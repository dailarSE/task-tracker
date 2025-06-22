package com.example.tasktracker.backend.internal.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Инкапсулирует метаданные для навигации по страницам в пагинированном ответе.
 * <p>
 * Предоставляет информацию о том, есть ли следующая страница данных,
 * и значение курсора, которое необходимо использовать для ее получения.
 * </p>
 */
@Schema(description = "Метаданные для навигации по страницам.")
@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public final class PageInfo {

    @Schema(description = "Указывает, существует ли следующая страница данных.", example = "true")
    private final boolean hasNextPage;

    @Schema(description = "Значение для запроса следующей страницы. Равно null, если следующей страницы нет.",
            nullable = true, example = "eyJsYXN0X2lkIjo1NDMyMX0=")
    private final String nextPageCursor;
}