package com.example.tasktracker.backend.user.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * DTO (Data Transfer Object) для предоставления основной информации о пользователе.
 * <p>
 * Используется, например, для ответа эндпоинта, возвращающего данные
 * текущего аутентифицированного пользователя.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public class UserResponse {
    /**
     * Уникальный идентификатор пользователя.
     */
    private final Long id;

    /**
     * Email адрес пользователя.
     */
    private final String email;
}
