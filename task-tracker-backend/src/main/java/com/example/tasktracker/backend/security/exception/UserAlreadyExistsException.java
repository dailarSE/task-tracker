package com.example.tasktracker.backend.security.exception;

import com.example.tasktracker.backend.web.ApiConstants;
import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.util.Locale;

/**
 * Исключение, выбрасываемое при попытке регистрации пользователя
 * с email, который уже существует в системе.
 * <p>
 * Это исключение реализует {@link ErrorResponse} через наследование
 * от {@link ErrorResponseException}, что позволяет ему напрямую предоставлять информацию
 * для формирования стандартизированного ответа об ошибке в формате RFC 9457 Problem Details.
 * </p>
 * <p>
 * HTTP-статус: 409 Conflict.
 * </p>
 * <p>
 * Коды для локализации через {@link org.springframework.context.MessageSource}:
 * <ul>
 *     <li>Type URI суффикс: {@value #PROBLEM_TYPE_SUFFIX} (конкатенируется с {@link ApiConstants#PROBLEM_TYPE_BASE_URI})</li>
 *     <li>Title Message Code: "{@value #PROBLEM_TYPE_SUFFIX}.title"</li>
 *     <li>Detail Message Code: "{@value #PROBLEM_TYPE_SUFFIX}.detail" (ожидает один аргумент: email)</li>
 * </ul>
 * </p>
 *
 * @see ErrorResponseException
 * @see com.example.tasktracker.backend.web.exception.GlobalExceptionHandler
 */
@Getter
public class UserAlreadyExistsException extends ErrorResponseException {

    private static final HttpStatus STATUS = HttpStatus.CONFLICT;

    /**
     * Суффикс для формирования URI типа проблемы ({@code ProblemDetail.type}) и
     * ключей для {@link org.springframework.context.MessageSource}.
     * Значение: "{@value}".
     */
    public static final String PROBLEM_TYPE_SUFFIX = "user.alreadyExists";

    /**
     * Сегмент пути для URI типа проблемы, добавляемый к {@link ApiConstants#PROBLEM_TYPE_BASE_URI}.
     * Использует слэши или дефисы для читаемости URI.
     * Значение: "{@value}".
     */
    public static final String PROBLEM_TYPE_URI_PATH = "user/already-exists";

    /**
     * Email адрес пользователя, который уже существует и вызвал это исключение.
     * Используется как аргумент для форматирования сообщения детализации.
     */
    private final String email;

    /**
     * Конструктор, создающий исключение для указанного email.
     *
     * @param email Email адрес, который уже существует в системе.
     */
    public UserAlreadyExistsException(String email) {
        this(email, null);
    }

    /**
     * Конструктор, создающий исключение для указанного email и с указанием причины.
     * Сообщение для детализации будет извлечено из {@link org.springframework.context.MessageSource}
     * с использованием кода, возвращаемого {@link #getDetailMessageCode()} и {@link #getTitleMessageCode()}.
     *
     * @param email Email адрес, который уже существует в системе.
     * @param cause Исходная причина исключения (может быть {@code null}).
     */
    public UserAlreadyExistsException(String email, @Nullable Throwable cause) {
        super(STATUS, cause);
        this.email = email;

        getBody().setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + PROBLEM_TYPE_URI_PATH));
        getBody().setProperty("conflicting_email", email);
    }

    /**
     * Возвращает код сообщения для поля "title" в {@link org.springframework.http.ProblemDetail},
     * который будет использоваться {@link org.springframework.context.MessageSource}.
     * <p>Ожидаемый ключ в {@code messages.properties}: {@code problemDetail.user.alreadyExists.title}</p>
     *
     * @return Код сообщения для заголовка проблемы.
     */
    @Override
    public String getTitleMessageCode() {
        return "problemDetail." + PROBLEM_TYPE_SUFFIX + ".title";
    }

    /**
     * Возвращает код сообщения для поля "detail" в {@link org.springframework.http.ProblemDetail},
     * который будет использоваться {@link org.springframework.context.MessageSource}.
     * <p>Ожидаемый ключ в {@code messages.properties}: {@code problemDetail.user.alreadyExists.detail}</p>
     * <p>Сообщение по этому ключу должно ожидать один аргумент (email пользователя),
     * например: {@code "User with email {0} already exists."}</p>
     *
     * @return Код сообщения для детализации проблемы.
     */
    @Override
    public String getDetailMessageCode() {
        return "problemDetail." + PROBLEM_TYPE_SUFFIX + ".detail";
    }

    /**
     * Возвращает {@code null}, так как URI типа проблемы устанавливается
     * напрямую в конструкторе и не должен разрешаться через MessageSource.
     * Это предотвращает перезапись явно установленного адреса при обработке в {@link ErrorResponse#updateAndGetBody(MessageSource, Locale)}.
     * @return {@code null}
     */
    @Override
    public String getTypeMessageCode() {
        return null;
    }
}