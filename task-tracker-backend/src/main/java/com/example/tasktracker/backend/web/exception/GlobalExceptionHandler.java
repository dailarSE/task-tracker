package com.example.tasktracker.backend.web.exception;

import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.exception.PasswordMismatchException;
import com.example.tasktracker.backend.security.exception.UserAlreadyExistsException;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import com.example.tasktracker.backend.web.ApiConstants;
import jakarta.validation.ConstraintViolationException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Глобальный обработчик исключений для API.
 * <p>
 * Этот класс перехватывает основные типы исключений, возникающих в приложении
 * (включая исключения безопасности и кастомные бизнес-исключения),
 * и формирует стандартизированные HTTP-ответы в формате RFC 9457 Problem Details.
 * <p>
 * Для формирования текстовых полей {@link ProblemDetail} (таких как {@code title} и {@code detail})
 * используется {@link MessageSource}. Предполагается, что все необходимые ключи сообщений
 * присутствуют в Resource Bundle. Если ключ не найден, {@link MessageSource#getMessage(String, Object[], Locale)}
 * выбросит {@link NoSuchMessageException}, что приведет к стандартной ошибке сервера (HTTP 500),
 * сигнализируя о проблеме конфигурации локализации.
 * <p>
 * Локаль для сообщений извлекается из текущего {@link WebRequest}.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageSource messageSource;

    /**
     * Обрабатывает {@link BadJwtException}, указывающее на проблемы с валидацией JWT.
     * Возвращает HTTP 401 Unauthorized с {@link ProblemDetail}, содержащим специфичную
     * информацию об ошибке JWT, включая {@code error_type} из {@link BadJwtException}.
     *
     * @param ex      Исключение {@link BadJwtException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail} для ответа клиенту.
     * @throws NoSuchMessageException если необходимые ключи для title/detail не найдены в {@link MessageSource}.
     */
    @ExceptionHandler(BadJwtException.class)
    public ProblemDetail handleBadJwtException(BadJwtException ex, WebRequest request) {
        String tokenSnippet = extractTokenSnippetFromRequest(request);
        log.warn("Bad JWT detected: Type={}, Message='{}', Token Snippet: {}, Cause: {}",
                ex.getErrorType(), ex.getMessage(), tokenSnippet, ex.getCause(), ex);

        String typeSuffix = "jwt." + ex.getErrorType().name().toLowerCase();
        return buildProblemDetail(
                ex,
                HttpStatus.UNAUTHORIZED,
                typeSuffix,
                request.getLocale(),
                Map.of("error_type", ex.getErrorType().name())
        );
    }

    /**
     * Обрабатывает общие {@link AuthenticationException} (если они не были перехвачены
     * более специфичными обработчиками, такими как {@link #handleBadJwtException}).
     * Возвращает HTTP 401 Unauthorized с {@link ProblemDetail}.
     *
     * @param ex      Исключение {@link AuthenticationException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail} для ответа клиенту.
     * @throws NoSuchMessageException если необходимые ключи для title/detail не найдены в {@link MessageSource}.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failure (general): {}", ex.getMessage(), ex);
        return buildProblemDetail(
                ex,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                request.getLocale(),
                null
        );
    }

    /**
     * Обрабатывает {@link AccessDeniedException}, возникающее при попытке доступа
     * аутентифицированного пользователя к ресурсу, на который у него нет прав.
     * Возвращает HTTP 403 Forbidden (или 404 Not Found)
     * с {@link ProblemDetail}.
     *
     * @param ex      Исключение {@link AccessDeniedException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail} для ответа клиенту.
     * @throws NoSuchMessageException если необходимые ключи для title/detail не найдены в {@link MessageSource}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        String userName = Optional.ofNullable(request.getUserPrincipal()).map(Principal::getName)
                .orElse("anonymous");
        String requestUri = "";
        if (request instanceof ServletWebRequest servletWebRequest) {
            requestUri = servletWebRequest.getRequest().getMethod() + " " + servletWebRequest.getRequest().getRequestURI();
        }
        log.warn("Access denied: User [{}] attempted to access [{}]. Reason: {}",
                userName, requestUri, ex.getMessage(), ex);

        // TODO: (Backlog - Low Priority) Реализовать логику выбора между 403 и 404 (ADR-0019). Пока всегда 403.
        HttpStatus status = HttpStatus.FORBIDDEN;
        String typeSuffix = "forbidden";

        return buildProblemDetail(
                ex,
                status,
                typeSuffix,
                request.getLocale(),
                null
        );
    }

    /**
     * Обрабатывает {@link UserAlreadyExistsException}, возникающее при попытке
     * регистрации пользователя с уже существующим email.
     * Возвращает HTTP 409 Conflict с {@link ProblemDetail}.
     *
     * @param ex      Исключение {@link UserAlreadyExistsException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключ для title/detail не найден.
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExistsException(UserAlreadyExistsException ex, WebRequest request) {
        log.warn("Registration conflict: {}", ex.getMessage()); // ex.getMessage() содержит email
        return buildProblemDetail(ex, HttpStatus.CONFLICT, "user.alreadyExists", request.getLocale(), null);
    }

    /**
     * Обрабатывает {@link PasswordMismatchException}, возникающее при попытке
     * регистрации пользователя, если пароли не совпадают.
     * Возвращает HTTP 400 Bad Request с {@link ProblemDetail}.
     *
     * @param ex      Исключение {@link PasswordMismatchException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключ для title/detail не найден.
     */
    @ExceptionHandler(PasswordMismatchException.class)
    public ProblemDetail handlePasswordMismatchException(PasswordMismatchException ex, WebRequest request) {
        log.warn("Registration bad request: {}", ex.getMessage());
        return buildProblemDetail(ex, HttpStatus.BAD_REQUEST, "user.passwordMismatch", request.getLocale(), null);
    }

    /**
     * Обрабатывает {@link BadCredentialsException} (на этапе логина).
     * Возвращает HTTP 401 Unauthorized с {@link ProblemDetail} и заголовком WWW-Authenticate.
     *
     * @param ex      Исключение {@link BadCredentialsException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     */
    @ExceptionHandler({BadCredentialsException.class})
    public ProblemDetail handleBadCredentialsException(AuthenticationException ex, WebRequest request) {
        log.warn("Login attempt failed: {}", ex.getMessage());

        // Устанавливаем заголовок WWW-Authenticate
        if (request instanceof ServletWebRequest servletWebRequest) {
            if (servletWebRequest.getResponse() != null) {
                servletWebRequest.getResponse().setHeader(
                        HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"task-tracker\"");
            }
        }

        return buildProblemDetail(
                ex,
                HttpStatus.UNAUTHORIZED,
                "auth.invalidCredentials",
                request.getLocale(),
                null
        );
    }

    /**
     * Обрабатывает {@link ConstraintViolationException}, возникающее при нарушении
     * ограничений валидации на параметрах методов, помеченных {@code @Validated},
     * или на полях бинов {@code @ConfigurationProperties}.
     * Возвращает HTTP 400 Bad Request с {@link ProblemDetail}, содержащим список ошибок.
     *
     * @param ex      Исключение {@link ConstraintViolationException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключ для title/detail не найден.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
        log.warn("Constraint violation(s) occurred: {}", ex.getMessage());
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", getFieldNameFromPath(violation.getPropertyPath().toString()),
                        "message", violation.getMessage()
                ))
                .toList();

        return buildProblemDetail(
                ex, // Используем ConstraintViolationException для общего сообщения, если нужно
                HttpStatus.BAD_REQUEST,
                "validation.constraintViolation", // Общий тип для таких ошибок
                request.getLocale(),
                Map.of("invalid_params", errors) // Добавляем список ошибок по RFC 7807 (или "errors" как более общее)
        );
    }

        /**
     * Кастомизирует обработку {@link MethodArgumentNotValidException}, которая выбрасывается,
     * когда валидация аргумента, помеченного {@code @Valid} в {@code @RequestBody}, не проходит.
     * Переопределяет метод из {@link ResponseEntityExceptionHandler} для возврата {@link ProblemDetail}
     * в теле {@link ResponseEntity}.
     * Возвращает HTTP 400 Bad Request.
     *
     * @param ex      Исключение {@link MethodArgumentNotValidException}.
     * @param headers Заголовки HTTP.
     * @param status  HTTP-статус (будет BAD_REQUEST).
     * @param request Текущий веб-запрос.
     * @return {@link ResponseEntity} с {@link ProblemDetail} в теле.
     * @throws NoSuchMessageException если ключ для title или detail не найден в {@link MessageSource}.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        log.warn("Method argument not valid due to validation errors. Number of errors: {}", ex.getErrorCount(), ex);

        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.<String, Object>of(
                        "field", fieldError.getField(),
                        "rejected_value", Optional.ofNullable(fieldError.getRejectedValue()).map(Object::toString).orElse("null"),
                        "message", fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "" // Это уже локализованное сообщение из ResourceBundle
                ))
                .toList();
        // Также можно обработать ex.getBindingResult().getGlobalErrors() если они есть

        String typeSuffix = "validation.methodArgumentNotValid";
        // Создаем ProblemDetail
        //detail отличается от стандартного, создаем его вручную
        //TODO validation.methodArgumentNotValid не принимает аргументы, но мы их зачем то передаем
        //TODO а вот constraintViolation принимает, хоть и с мутным результатом
        String detail = messageSource.getMessage("problemDetail." + typeSuffix + ".detail", new Object[]{ex.getErrorCount()}, request.getLocale());
        ProblemDetail problemDetail = buildProblemDetail(
                ex, // Передаем оригинальное исключение
                HttpStatus.BAD_REQUEST, // Явно используем HttpStatus
                typeSuffix,
                request.getLocale(),
                Map.of("invalid_params", errors) // Добавляем специфичные для этой ошибки properties
        );// Используем "invalid_params" как в RFC 7807 примере для ошибок валидации
        problemDetail.setDetail(detail);
        // Возвращаем ResponseEntity<Object>, где Object - это наш ProblemDetail
        return new ResponseEntity<>(problemDetail, headers, HttpStatus.BAD_REQUEST);
    }


    /**
     * Централизованный метод для построения объекта {@link ProblemDetail} на основе исключения.
     * <p>
     * Извлекает {@code title} и {@code detail} из {@link MessageSource} по ключам,
     * сформированным на основе {@code typeSuffix} (например, "problemDetail.suffix.title",
     * "problemDetail.suffix.detail"). Сообщение из {@code sourceException} используется
     * как аргумент для форматирования {@code detail}.
     * <p>
     * Если ключ для {@code title} или {@code detail} не найден в {@link MessageSource},
     * будет выброшено {@link NoSuchMessageException}, что приведет к стандартной ошибке сервера.
     * Это соответствует политике "fail-fast" для отсутствующих ресурсов локализации.
     *
     * @param sourceException      Оригинальное исключение, для которого формируется {@link ProblemDetail}.
     *                             Его сообщение используется как аргумент для форматирования поля {@code detail}.
     *                             Не должно быть null.
     * @param status               HTTP-статус для {@link ProblemDetail}. Не должен быть null.
     * @param typeSuffix           Суффикс для формирования URI типа проблемы ({@code ProblemDetail.type})
     *                             и ключей для {@link MessageSource}. Не должен быть null.
     * @param locale               Локаль для получения сообщений. Не должна быть null.
     * @param additionalProperties Карта с дополнительными, не стандартными свойствами для
     *                             {@link ProblemDetail} (может быть null).
     * @return Сконфигурированный объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключ для title или detail не найден в {@link MessageSource}.
     */
    private ProblemDetail buildProblemDetail(@NonNull Exception sourceException,
                                             @NonNull HttpStatus status,
                                             @NonNull String typeSuffix,
                                             @NonNull Locale locale,
                                             @Nullable Map<String, Object> additionalProperties) {

        String titleKey = "problemDetail." + typeSuffix + ".title";
        String detailKey = "problemDetail." + typeSuffix + ".detail";
        URI typeUri = URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + typeSuffix);

        Object[] detailArgs = new Object[]{sourceException.getMessage()};

        String title = messageSource.getMessage(titleKey, null, locale);
        String detail = messageSource.getMessage(detailKey, detailArgs, locale);

        ProblemDetail problemDetail = ProblemDetail.forStatus(status.value());
        problemDetail.setType(typeUri);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);

        if (additionalProperties != null) {
            additionalProperties.forEach(problemDetail::setProperty);
        }
        return problemDetail;
    }

    /**
     * Извлекает токен из заголовка Authorization запроса и возвращает его сокращенную версию
     * для безопасного логирования. Сделан package-private для тестируемости.
     *
     * @param webRequest Текущий веб-запрос.
     * @return Сокращенная строка токена или плейсхолдер, если токен отсутствует или не Bearer.
     */
    String extractTokenSnippetFromRequest(WebRequest webRequest) {
        if (webRequest instanceof ServletWebRequest servletWebRequest) {
            return Optional.ofNullable(servletWebRequest.getRequest().getHeader(HttpHeaders.AUTHORIZATION))
                    .filter(h -> h.toLowerCase().startsWith("bearer "))
                    .map(h -> JwtValidator.truncateTokenForLogging(h.substring("bearer ".length())))
                    .orElse("[token not present or not bearer]");
        }
        return "[non-http request]";
    }

    private String getFieldNameFromPath(String propertyPath) {
        // propertyPath может быть сложным, например, "methodName.arg0.fieldName"
        // Пытаемся извлечь последнюю часть.
        if (propertyPath.contains(".")) {
            return propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
        }
        return propertyPath;
    }
}