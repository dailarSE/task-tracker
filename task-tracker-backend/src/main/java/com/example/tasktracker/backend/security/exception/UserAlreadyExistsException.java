package com.example.tasktracker.backend.security.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при попытке регистрации пользователя
 * с email, который уже существует в системе.
 * Приводит к HTTP-ответу 409 Conflict, если не обработано специфичным
 * {@code @ExceptionHandler} в {@code @ControllerAdvice}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class UserAlreadyExistsException extends RuntimeException {

    /**
     * Конструктор с сообщением об ошибке.
     *
     * @param message Сообщение, детализирующее ошибку.
     */
    public UserAlreadyExistsException(String message) {
        super(message);
    }

    /**
     * Конструктор с сообщением об ошибке и причиной.
     *
     * @param message Сообщение, детализирующее ошибку.
     * @param cause   Исходная причина исключения.
     */
    public UserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}