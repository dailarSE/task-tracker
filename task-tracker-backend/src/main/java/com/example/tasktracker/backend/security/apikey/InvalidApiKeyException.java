package com.example.tasktracker.backend.security.apikey;

import org.springframework.security.core.AuthenticationException;

/**
 * Исключение, выбрасываемое при ошибке аутентификации по API-ключу.
 * Используется для разделения логики обработки ошибок M2M и пользовательской аутентификации.
 */
public class InvalidApiKeyException extends AuthenticationException {
    public InvalidApiKeyException(String msg) {
        super(msg);
    }
}