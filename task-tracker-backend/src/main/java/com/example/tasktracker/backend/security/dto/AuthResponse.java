package com.example.tasktracker.backend.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для ответа при успешной аутентификации или регистрации.
 * Содержит JWT Access Token и информацию о нем.
 */
@Schema(description = "DTO для ответа при успешной аутентификации или регистрации")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * Сгенерированный JWT Access Token.
     */
    @Schema(description = "Сгенерированный JWT Access Token.",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
                    "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c")
    private String accessToken;

    /**
     * Тип токена (всегда "Bearer" для JWT).
     */
    @Schema(description = "Тип токена.", example = "Bearer")
    private String tokenType = "Bearer";

    /**
     * Время жизни токена в секундах.
     * Предоставляется для информации клиенту.
     */
    @Schema(description = "Время жизни токена в секундах.", example = "3600")
    private Long expiresIn;

    /**
     * Конструктор для удобного создания с двумя основными полями.
     * Поле tokenType будет установлено по умолчанию в "Bearer".
     *
     * @param accessToken Сгенерированный JWT.
     * @param expiresIn   Время жизни токена в секундах.
     */
    public AuthResponse(String accessToken, Long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }
}