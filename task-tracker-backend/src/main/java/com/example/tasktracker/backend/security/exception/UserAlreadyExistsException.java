package com.example.tasktracker.backend.security.exception;

import com.example.tasktracker.backend.web.ApiConstants;
import com.example.tasktracker.backend.web.exception.GlobalExceptionHandler;
import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.util.Locale;

/**
 * Исключение, выбрасываемое при попытке регистрации пользователя с email,
 * который уже существует в системе.
 * <p>
 * Это исключение реализует {@link ErrorResponse}, что позволяет Spring MVC
 * автоматически формировать стандартизированный ответ RFC 9457 Problem Details.
 * <p>
 * Оно устанавливает HTTP-статус 409 Conflict и предоставляет
 * локализуемые сообщения для полей 'title' и 'detail' ответа.
 * Кроме того, в 'properties' ответа добавляется машиночитаемое поле
 * {@value #CONFLICTING_EMAIL_PROPERTY}, содержащее email, вызвавший конфликт.
 *
 * @see ErrorResponseException
 * @see GlobalExceptionHandler
 */
@Getter
public class UserAlreadyExistsException extends ErrorResponseException {

    private static final HttpStatus STATUS = HttpStatus.CONFLICT;

    /**
     * Ключ для поля в 'properties' объекта ProblemDetail, содержащего email, вызвавший конфликт.
     * Значение: {@value}.
     */
    public static final String CONFLICTING_EMAIL_PROPERTY = "conflictingEmail";

    /**
     * Суффикс для формирования URI типа проблемы ({@code ProblemDetail.type}) и
     * ключей для {@link org.springframework.context.MessageSource}.
     * Значение: "{@value}".
     */
    public static final String PROBLEM_TYPE_SUFFIX = "user.alreadyExists";

    /**
     * Сегмент пути для URI типа проблемы, добавляемый к {@link ApiConstants#PROBLEM_TYPE_BASE_URI}.
     * Значение: "{@value}".
     */
    public static final String PROBLEM_TYPE_URI_PATH = "user/already-exists";

    /**
     * Email адрес пользователя, который уже существует и вызвал это исключение.
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
     *
     * @param email Email адрес, который уже существует в системе.
     * @param cause Исходная причина исключения (может быть {@code null}).
     */
    public UserAlreadyExistsException(String email, @Nullable Throwable cause) {
        super(STATUS, cause);
        this.email = email;

        getBody().setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + PROBLEM_TYPE_URI_PATH));
        getBody().setProperty(CONFLICTING_EMAIL_PROPERTY, email);
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
     * Возвращает аргументы для форматирования сообщения из поля "detail".
     * В данном случае, это email пользователя, вызвавший конфликт.
     *
     * @return Массив объектов, содержащий email пользователя.
     */
    @Override
    public Object[] getDetailMessageArguments() {
        return new Object[]{this.email};
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