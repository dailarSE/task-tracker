package com.example.tasktracker.backend.security.exception;

import com.example.tasktracker.backend.security.jwt.JwtErrorType;
import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

/**
 * Исключение, выбрасываемое при обнаружении невалидного или некорректного JWT.
 * Используется для передачи специфичной информации об ошибке JWT
 * в механизмы обработки ошибок Spring Security (например, AuthenticationEntryPoint).
 * Содержит тип ошибки {@link JwtErrorType} для более детальной обработки.
 */
@Getter
public class BadJwtException extends AuthenticationException {

    private final JwtErrorType errorType;

    /**
     * Конструктор с сообщением и причиной (исходным исключением).
     *
     * @param msg       Сообщение об ошибке.
     * @param errorType Тип ошибки JWT {@link JwtErrorType}.
     * @param cause     Причина исключения (например, исходная {@link io.jsonwebtoken.JwtException}).
     */
    public BadJwtException(String msg, JwtErrorType errorType, Throwable cause) {
        super(msg, cause);
        this.errorType = errorType;
    }

    /**
     * Конструктор с сообщением.
     *
     * @param msg       Сообщение об ошибке.
     * @param errorType Тип ошибки JWT {@link JwtErrorType}.
     */
    public BadJwtException(String msg, JwtErrorType errorType) {
        super(msg);
        this.errorType = errorType;
    }
}