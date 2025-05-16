package com.example.tasktracker.backend.security.exception;

import com.example.tasktracker.backend.web.ApiConstants;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

import java.net.URI;

/**
 * Исключение, выбрасываемое при регистрации, если предоставленные
 * пароль и подтверждение пароля не совпадают.
 * <p>
 * Это исключение реализует {@link org.springframework.web.ErrorResponse} через наследование
 * от {@link ErrorResponseException}, что позволяет ему напрямую предоставлять информацию
 * для формирования стандартизированного ответа об ошибке в формате RFC 9457 Problem Details.
 * </p>
 * <p>
 * HTTP-статус: 400 Bad Request.
 * </p>
 * <p>
 * Коды для локализации через {@link org.springframework.context.MessageSource}:
 * <ul>
 *     <li>Type URI суффикс: {@value #PROBLEM_TYPE_SUFFIX} (конкатенируется с {@link ApiConstants#PROBLEM_TYPE_BASE_URI})</li>
 *     <li>Title Message Code: "{@value #PROBLEM_TYPE_SUFFIX}.title"</li>
 *     <li>Detail Message Code: "{@value #PROBLEM_TYPE_SUFFIX}.detail" (аргументы не требуются, если сообщение фиксированное)</li>
 * </ul>
 * </p>
 *
 * @see ErrorResponseException
 * @see com.example.tasktracker.backend.web.exception.GlobalExceptionHandler
 */
public class PasswordMismatchException extends ErrorResponseException {

    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    /**
     * Суффикс для формирования URI типа проблемы ({@code ProblemDetail.type}) и
     * ключей для {@link org.springframework.context.MessageSource}.
     */
    public static final String PROBLEM_TYPE_SUFFIX = "user.passwordMismatch";

    /**
     * Сегмент пути для URI типа проблемы, добавляемый к {@link ApiConstants#PROBLEM_TYPE_BASE_URI}.
     * Использует слэши или дефисы для читаемости URI.
     * Значение: "{@value}".
     */
    public static final String PROBLEM_TYPE_URI_PATH = "user/password-mismatch";

    /**
     * Конструктор, создающий исключение с сообщением по умолчанию.
     * <p>
     * Сообщение для детализации будет извлечено из {@link org.springframework.context.MessageSource}
     * с использованием кода, возвращаемого {@link #getDetailMessageCode()}.
     * </p>
     */
    public PasswordMismatchException() {
        this(null);
    }

    /**
     * Конструктор, создающий исключение с указанием причины.
     * <p>
     * Сообщение для детализации будет извлечено из {@link org.springframework.context.MessageSource}
     * с использованием кода, возвращаемого {@link #getDetailMessageCode()}.
     * </p>
     *
     * @param cause Исходная причина исключения (может быть {@code null}).
     */
    public PasswordMismatchException(@Nullable Throwable cause) {
        super(STATUS, cause);
        getBody().setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + PROBLEM_TYPE_URI_PATH));


    }

    /**
     * Возвращает код сообщения для поля "title" в {@link org.springframework.http.ProblemDetail},
     * который будет использоваться {@link org.springframework.context.MessageSource}.
     * <p>Ожидаемый ключ в {@code messages.properties}: {@code problemDetail.user.passwordMismatch.title}</p>
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
     * <p>Ожидаемый ключ в {@code messages.properties}: {@code problemDetail.user.passwordMismatch.detail}</p>
     * <p>Пример сообщения: {@code "Passwords do not match. Please ensure both passwords are identical."}</p>
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
     * Это предотвращает перезапись явно установленного {@link ProblemDetail#setType(URI)}.
     * @return {@code null}
     */
    @Override
    public String getTypeMessageCode() {
        return null;
    }
}