package com.example.tasktracker.backend.security.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для ответа при успешной аутентификации или регистрации.
 * Содержит JWT Access Token и информацию о нем.
 */
@Getter
@Setter
@NoArgsConstructor
public class AuthResponse {

    /**
     * Сгенерированный JWT Access Token.
     */
    private String accessToken;

    /**
     * Тип токена (всегда "Bearer" для JWT).
     */
    private final String tokenType = "Bearer";

    /**
     * Время жизни токена в миллисекундах.
     * Предоставляется для информации клиенту.
     */
    private Long expiresIn;

    /**
     * Конструктор для удобного создания с двумя основными полями.
     * Поле tokenType будет установлено по умолчанию в "Bearer".
     *
     * @param accessToken Сгенерированный JWT.
     * @param expiresIn   Время жизни токена в миллисекундах.
     */
    public AuthResponse(String accessToken, Long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }
}