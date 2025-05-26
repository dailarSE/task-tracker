package com.example.tasktracker.backend.user.dto;

import lombok.*;

/**
 * DTO (Data Transfer Object) для предоставления основной информации о пользователе.
 * <p>
 * Используется, например, для ответа эндпоинта, возвращающего данные
 * текущего аутентифицированного пользователя.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    /**
     * Уникальный идентификатор пользователя.
     */
    private Long id;

    /**
     * Email адрес пользователя.
     */
    private String email;
}
