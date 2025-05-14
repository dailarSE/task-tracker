package com.example.tasktracker.backend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import lombok.Getter;
import lombok.NonNull;

import java.util.Optional;

/**
 * Представляет результат валидации JWT.
 * Может содержать либо успешно распарсенный {@link Jws<Claims>},
 * либо информацию об ошибке валидации (тип ошибки и сообщение).
 * <p>
 * Объект этого класса инкапсулирует результат
 * первой обнаруженной ошибки валидации. Валидатор JWT (например, {@link JwtValidator})
 * не проводит полный аудит всех возможных проблем токена, а прекращает проверку
 * и возвращает результат при обнаружении первой же ошибки (например, если подпись неверна,
 * проверка на срок действия может не производиться).
 * </p>
 */
@Getter // Генерируем геттеры для всех полей
public class JwtValidationResult {

    private final Jws<Claims> jwsClaims;    // null, если валидация не прошла
    private final JwtErrorType errorType;   // null, если валидация успешна
    private final String errorMessage;      // null, если валидация успешна
    private final Throwable cause;          // null, если валидация успешна

    /**
     * Приватный конструктор. Используйте статические фабричные методы {@link #success(Jws)}
     * или {@link #failure(JwtErrorType, String, Throwable)}.
     *
     * @param jwsClaims    Распарсенный JWS или null, если была ошибка.
     * @param errorType    Тип ошибки или null, если успех.
     * @param errorMessage Сообщение об ошибке или null, если успех.
     * @param cause        Исходное исключение-причина или null.
     */
    private JwtValidationResult(Jws<Claims> jwsClaims, JwtErrorType errorType, String errorMessage, Throwable cause) {
        this.jwsClaims = jwsClaims;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.cause = cause;
    }

    /**
     * Создает успешный результат валидации.
     *
     * @param jwsClaims Распарсенный и валидированный JWS. Не должен быть null.
     * @return Экземпляр {@link JwtValidationResult}, представляющий успех.
     * @throws NullPointerException если jwsClaims равен null.
     */
    public static JwtValidationResult success(@lombok.NonNull Jws<Claims> jwsClaims) {
        return new JwtValidationResult(jwsClaims, null, null,null);
    }

    /**
     * Создает результат валидации с ошибкой, включая причину.
     *
     * @param errorType    Тип ошибки {@link JwtErrorType}. Не должен быть null.
     * @param errorMessage Сообщение об ошибке. Может быть null.
     * @param cause        Исходное исключение-причина. Может быть null.
     * @return Экземпляр {@link JwtValidationResult}, представляющий ошибку.
     * @throws NullPointerException если errorType равен null.
     */
    public static JwtValidationResult failure(
            @NonNull JwtErrorType errorType, String errorMessage, Throwable cause) {
        return new JwtValidationResult(null, errorType, errorMessage, cause);
    }

    /**
     * Создает результат валидации с ошибкой без указания причины.
     *
     * @param errorType    Тип ошибки {@link JwtErrorType}. Не должен быть null.
     * @param errorMessage Сообщение об ошибке. Может быть null.
     * @return Экземпляр {@link JwtValidationResult}, представляющий ошибку.
     * @throws NullPointerException если errorType равен null.
     */
    public static JwtValidationResult failure(@lombok.NonNull JwtErrorType errorType, String errorMessage) {
        return failure(errorType, errorMessage, null);
    }

    /**
     * Проверяет, была ли валидация JWT успешной.
     *
     * @return {@code true} если валидация прошла успешно (нет типа ошибки и есть jwsClaims),
     *         иначе {@code false}.
     */
    public boolean isSuccess() {
        return this.errorType == null && this.jwsClaims != null;
    }

    /**
     * Возвращает {@link Optional} с {@link Jws<Claims>}, если валидация была успешной.
     *
     * @return {@link Optional} с {@link Jws<Claims>} или {@link Optional#empty()} если была ошибка.
     */
    public Optional<Jws<Claims>> getJwsClaimsOptional() {
        return Optional.ofNullable(this.jwsClaims);
    }
}