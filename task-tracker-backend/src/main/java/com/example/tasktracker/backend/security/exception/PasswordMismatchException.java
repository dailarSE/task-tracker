package com.example.tasktracker.backend.security.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при регистрации, если предоставленные
 * пароль и подтверждение пароля не совпадают.
 * Приводит к HTTP-ответу 400 Bad Request, если не обработано специфичным
 * {@code @ExceptionHandler} в {@code @ControllerAdvice}.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PasswordMismatchException extends RuntimeException {

    /**
     * Конструктор с сообщением об ошибке.
     *
     * @param message Сообщение, детализирующее ошибку.
     */
    public PasswordMismatchException(String message) {
        super(message);
    }

    /**
     * Конструктор с сообщением об ошибке и причиной.
     *
     * @param message Сообщение, детализирующее ошибку.
     * @param cause   Исходная причина исключения.
     */
    public PasswordMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}