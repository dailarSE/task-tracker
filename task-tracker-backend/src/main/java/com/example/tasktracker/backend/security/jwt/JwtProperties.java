package com.example.tasktracker.backend.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурационные свойства для JWT.
 * Читаются из application.yml с префиксом "app.security.jwt".
 * Валидация свойств происходит при старте приложения.
 */
@Component
@ConfigurationProperties(prefix = "app.security.jwt")
@Validated
@RequiredArgsConstructor
@Getter
@Setter
public class JwtProperties {

    /**
     * Секретный ключ для подписи и валидации JWT.
     * ДОЛЖЕН быть предоставлен в формате Base64.
     * Обязательное свойство.
     */
    @NotBlank(message = "{security.jwt.secretKey.notBlank}")
    private String secretKey;

    /**
     * Время жизни Access Token в миллисекундах.
     * Обязательное свойство, должно быть положительным.
     */
    @Positive(message = "{security.jwt.expirationMs.positive}")
    private long expirationMs;
}