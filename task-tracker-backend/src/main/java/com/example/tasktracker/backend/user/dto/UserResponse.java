package com.example.tasktracker.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * DTO (Data Transfer Object) для предоставления основной информации о пользователе.
 * <p>
 * Используется, например, для ответа эндпоинта, возвращающего данные
 * текущего аутентифицированного пользователя.
 * </p>
 */
@Schema(description = "DTO с основной информацией о пользователе")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    /**
     * Уникальный идентификатор пользователя.
     */
    @Schema(description = "Уникальный идентификатор пользователя.", example = "1")
    private Long id;

    /**
     * Email адрес пользователя.
     */
    @Schema(description = "Email адрес пользователя.", example = "user@example.com")
    private String email;
}
