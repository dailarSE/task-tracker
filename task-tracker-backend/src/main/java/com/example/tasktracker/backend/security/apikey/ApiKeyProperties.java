package com.example.tasktracker.backend.security.apikey;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/**
 * Конфигурационные свойства для аутентификации по API-ключам.
 * <p>
 * Читаются из {@code application.yml} с префиксом "app.security.api-key".
 * Валидация свойств происходит при старте приложения.
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "app.security.api-key")
@Validated
@Getter
@Setter
public class ApiKeyProperties {

    /**
     * Набор валидных API-ключей для доступа к внутренним эндпоинтам.
     * <p>
     * Ключи должны быть криптографически случайными строками, содержащими
     * только символы, безопасные для использования в HTTP-заголовках
     * (например, из набора [A-Za-z0-9_-]).
     * </p>
     * <p>
     * Это свойство является обязательным и не может быть пустым.
     * </p>
     */
    @NotEmpty(message = "{security.apiKey.validKeys.notEmpty}")
    private Set<String> validKeys;
}