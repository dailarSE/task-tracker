package com.example.tasktracker.backend.internal.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO для метаданных пагинации, используемых в ответах API.
 */
@Schema(description = "Информация о пагинации")
@Getter
@AllArgsConstructor
public class PageInfo {

    @Schema(description = "Признак наличия следующей страницы данных.", example = "true")
    private boolean hasNextPage;

    @Schema(description = "Непрозрачный курсор для запроса следующей страницы. Null, если следующей страницы нет.",
            nullable = true, example = "eyJsYXN0X2lkIjo1NDMyMX0=")
    private String nextPageCursor;
}