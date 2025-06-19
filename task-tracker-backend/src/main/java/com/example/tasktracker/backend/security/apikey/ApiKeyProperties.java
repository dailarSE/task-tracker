package com.example.tasktracker.backend.security.apikey;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

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
     * Карта, сопоставляющая API-ключи с идентификаторами сервисов-клиентов.
     * <p>
     * Ключ карты - это сам API-ключ.
     * Значение карты - строковый идентификатор сервиса (например, "task-tracker-scheduler").
     * </p>
     * <p>
     * Это свойство является обязательным и не может быть пустым.
     * </p>
     */
    @NotEmpty(message = "{security.apiKey.keysToServices.notEmpty}")
    private Map<String, String> keysToServices;
}