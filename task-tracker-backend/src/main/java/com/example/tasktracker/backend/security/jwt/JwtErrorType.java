package com.example.tasktracker.backend.security.jwt;

/**
 * Перечисление, представляющее типы ошибок, которые могут возникнуть
 * при валидации JWT. Используется в {@link JwtValidationResult}.
 */
public enum JwtErrorType {
    /**
     * Срок действия токена истек.
     */
    EXPIRED,

    /**
     * Подпись токена недействительна.
     */
    INVALID_SIGNATURE,

    /**
     * Токен имеет неверный формат (структурно поврежден).
     */
    MALFORMED,

    /**
     * Токен использует неподдерживаемый алгоритм или функцию.
     */
    UNSUPPORTED,

    /**
     * Токен null, пустой, или содержит недопустимые аргументы,
     * которые библиотека JJWT не смогла обработать на раннем этапе.
     * (например, ошибка в claims, которую jjwt ловит как IllegalArgumentException).
     */
    EMPTY_OR_ILLEGAL_ARGUMENT,

    /**
     * Другая, не классифицированная ошибка JWT.
     */
    OTHER_JWT_EXCEPTION
}