package com.example.tasktracker.backend.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * DTO для ответа при успешной аутентификации или регистрации.
 * Содержит JWT Access Token и информацию о нем.
 */
@Getter
public class AuthResponse {

    /**
     * Сгенерированный JWT Access Token.
     */
    @JsonProperty("access_token")
    private final String accessToken;

    /**
     * Тип токена (всегда "Bearer" для JWT).
     */
    @JsonProperty("token_type")
    private final String tokenType = "Bearer";

    /**
     * Время жизни токена в секундах.
     * Предоставляется для информации клиенту.
     */
    @JsonProperty("expires_in")
    private final Long expiresIn;

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