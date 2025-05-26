package com.example.tasktracker.backend.web.exception;

import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import com.example.tasktracker.backend.web.ApiConstants;
import jakarta.validation.ConstraintViolationException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
import java.util.*;

/**
 * Глобальный обработчик исключений для API.
 * <p>
 * Этот класс перехватывает основные типы исключений, возникающих в приложении
 * (включая исключения безопасности и кастомные бизнес-исключения, наследующие {@link org.springframework.web.ErrorResponseException}),
 * и формирует стандартизированные HTTP-ответы в формате RFC 9457 Problem Details.
 * </p>
 * <p>
 * Для формирования текстовых полей {@link ProblemDetail} (таких как {@code title} и {@code detail})
 * используется {@link MessageSource}. Предполагается, что все необходимые ключи сообщений
 * присутствуют в Resource Bundle (см. {@code messages.properties}). Если ключ не найден,
 * {@link #handleNoSuchMessageException(NoSuchMessageException, WebRequest)} формирует
 * ответ HTTP 500, сигнализируя о проблеме конфигурации локализации.
 * </p>
 * <p>
 * Кастомные бизнес-исключения, наследующие {@link org.springframework.web.ErrorResponseException}
 * (например, {@code UserAlreadyExistsException}, {@code PasswordMismatchException}), обрабатываются
 * стандартными механизмами {@link ResponseEntityExceptionHandler}, который использует методы
 * этих исключений для получения кодов сообщений и аргументов, а также для установки специфичных
 * полей {@code ProblemDetail} через его свойство {@code properties}.
 * </p>
 * <p>
 * Локаль для сообщений извлекается из текущего {@link WebRequest}.
 * Поле {@code instance} в {@link ProblemDetail} автоматически устанавливается в URI текущего запроса.
 * </p>
 *
 * @see org.springframework.http.ProblemDetail
 * @see org.springframework.web.ErrorResponseException
 * @see org.springframework.context.MessageSource
 * @see ApiConstants#PROBLEM_TYPE_BASE_URI Базовый URI для типов проблем.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageSource messageSource;

    /**
     * Обрабатывает {@link BadJwtException}, указывающее на проблемы с валидацией JWT.
     * Возвращает {@link ProblemDetail} для ответа HTTP 401 Unauthorized.
     * <p>
     * В поле {@code properties} объекта {@code ProblemDetail} добавляются:
     * <ul>
     *     <li>{@code error_type}: Тип ошибки JWT из {@link BadJwtException#getErrorType()}.</li>
     *     <li>{@code jwt_error_details}: Оригинальное сообщение из {@link BadJwtException#getMessage()}, если оно присутствует.</li>
     * </ul>
     * Поля {@code title} и {@code detail} извлекаются из {@link MessageSource} по ключам,
     * сформированным на основе {@code "jwt." + ex.getErrorType().name().toLowerCase()}.
     * Ожидается, что соответствующие ключи для {@code detail} в {@code messages.properties}
     * будут статическими (без плейсхолдеров, так как динамическая информация передается через {@code properties}).
     * </p>
     *
     * @param ex      Исключение {@link BadJwtException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключи для {@code title} или {@code detail} не найдены в {@link MessageSource}.
     */
    @ExceptionHandler(BadJwtException.class)
    public ProblemDetail handleBadJwtException(BadJwtException ex, WebRequest request) {
        String tokenSnippet = extractTokenSnippetFromRequest(request);
        log.warn("Bad JWT detected: Type={}, Message='{}', Token Snippet: {}, Cause: {}",
                ex.getErrorType(), ex.getMessage(), tokenSnippet, ex.getCause(), ex);

        String typeSuffix = "jwt." + ex.getErrorType().name().toLowerCase();

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("error_type", ex.getErrorType().name());
        if (ex.getMessage() != null) {
            properties.put("jwt_error_details", ex.getMessage());
        }

        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.UNAUTHORIZED,
                typeSuffix,
                request.getLocale(),
                properties
        );
        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Обрабатывает общие {@link AuthenticationException}, которые не были перехвачены
     * более специфичными обработчиками (например, {@link BadJwtException} или {@link BadCredentialsException}).
     * Возвращает {@link ProblemDetail} для ответа HTTP 401 Unauthorized.
     * <p>
     * Если {@link AuthenticationException#getMessage()} не пусто, оно добавляется в поле
     * {@code properties.auth_error_details} объекта {@code ProblemDetail}.
     * Поля {@code title} и {@code detail} извлекаются из {@link MessageSource} по ключу {@code "unauthorized"}.
     * </p>
     *
     * @param ex      Исключение {@link AuthenticationException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключи для {@code title} или {@code detail} не найдены.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failure (general): {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                request.getLocale(),
                ex.getMessage() == null ? null : Map.of("auth_error_details", ex.getMessage())
        );
        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Обрабатывает {@link AccessDeniedException}, возникающее при попытке доступа
     * аутентифицированного пользователя к ресурсу, на который у него нет прав.
     * Возвращает {@link ProblemDetail} для ответа HTTP 403 Forbidden.
     * <p>
     * Если {@link AccessDeniedException#getMessage()} не пусто, оно добавляется в поле
     * {@code properties.access_denied_reason} объекта {@code ProblemDetail}.
     * Поля {@code title} и {@code detail} извлекаются из {@link MessageSource} по ключу {@code "forbidden"}.
     * </p>
     *
     * @param ex      Исключение {@link AccessDeniedException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключи для {@code title} или {@code detail} не найдены.
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

        ProblemDetail problemDetail = buildProblemDetail(
                status,
                "forbidden",
                request.getLocale(),
                ex.getMessage() == null ? null : Map.of("access_denied_reason", ex.getMessage())
        );
        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Обрабатывает {@link BadCredentialsException}, возникающее при неудачной попытке входа пользователя
     * (например, через эндпоинт {@code /api/v1/auth/login}).
     * Возвращает {@link ProblemDetail} для ответа HTTP 401 Unauthorized.
     * Устанавливает заголовок {@code WWW-Authenticate: Bearer realm="task-tracker"}.
     * <p>
     * Если {@link BadCredentialsException#getMessage()} не пусто, оно добавляется в поле
     * {@code properties.login_error_details} объекта {@code ProblemDetail}.
     * Поля {@code title} и {@code detail} извлекаются из {@link MessageSource} по ключу {@code "auth.invalidCredentials"}.
     * </p>
     *
     * @param ex      Исключение {@link BadCredentialsException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключи для {@code title} или {@code detail} не найдены.
     */
    @ExceptionHandler({BadCredentialsException.class})
    public ProblemDetail handleBadCredentialsException(AuthenticationException ex, WebRequest request) {
        log.warn("Login attempt failed: {}", ex.getMessage());

        if (request instanceof ServletWebRequest servletWebRequest) {
            if (servletWebRequest.getResponse() != null) {
                servletWebRequest.getResponse().setHeader(
                        HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"task-tracker\"");
            }
        }

        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.UNAUTHORIZED,
                "auth.invalidCredentials",
                request.getLocale(),
                ex.getMessage() == null ? null : Map.of("login_error_details", ex.getMessage())
        );
        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Обрабатывает {@link ConstraintViolationException}, возникающее при нарушении
     * ограничений валидации на параметрах методов, аннотированных {@code @Validated},
     * или на полях бинов {@code @ConfigurationProperties}.
     * Возвращает {@link ProblemDetail} для ответа HTTP 400 Bad Request.
     * <p>
     * В поле {@code properties} объекта {@code ProblemDetail} добавляется:
     * <ul>
     *     <li>{@code invalid_params}: Список объектов, каждый из которых содержит {@code field} (имя поля)
     *         и {@code message} (сообщение об ошибке валидации для этого поля).</li>
     * </ul>
     * Поля {@code title} и {@code detail} извлекаются из {@link MessageSource} по ключу {@code "validation.constraintViolation"}.
     * </p>
     *
     * @param ex      Исключение {@link ConstraintViolationException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключи для {@code title} или {@code detail} не найдены.
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

        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "validation.constraintViolation",
                request.getLocale(),
                Map.of("invalid_params", errors)
        );
        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Кастомизирует обработку {@link MethodArgumentNotValidException}, которая выбрасывается,
     * когда валидация аргумента, аннотированного {@code @Valid} в {@code @RequestBody}, не проходит.
     * Переопределяет метод из {@link ResponseEntityExceptionHandler} для формирования {@link ProblemDetail}.
     * Возвращает {@link ResponseEntity} с HTTP 400 Bad Request.
     * <p>
     * В поле {@code properties} объекта {@code ProblemDetail} добавляется:
     * <ul>
     *     <li>{@code invalid_params}: Список объектов, каждый из которых содержит {@code field} (имя поля),
     *         {@code rejected_value} (отклоненное значение) и {@code message} (сообщение об ошибке валидации).</li>
     * </ul>
     * Поля {@code title} и {@code detail} извлекаются из {@link MessageSource} по ключу {@code "validation.methodArgumentNotValid"}.
     * </p>
     *
     * @param ex      Исключение {@link MethodArgumentNotValidException}.
     * @param headers Заголовки HTTP.
     * @param status  HTTP-статус (будет {@link HttpStatus#BAD_REQUEST}).
     * @param request Текущий веб-запрос.
     * @return {@link ResponseEntity} с {@link ProblemDetail} в теле.
     * @throws NoSuchMessageException если ключи для {@code title} или {@code detail} не найдены в {@link MessageSource}.
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

        ProblemDetail problemDetail = buildProblemDetail(
                (HttpStatus) status,
                typeSuffix,
                request.getLocale(),
                Map.of("invalid_params", errors) // Используем "invalid_params" как в RFC 7807 примере для ошибок валидации
        );
        setInstanceUriIfAbsent(problemDetail, request);

        return new ResponseEntity<>(problemDetail, headers, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает {@link HttpMessageConversionException}, которые могут возникать,
     * когда тело HTTP-запроса не может быть прочитано или преобразовано
     * в требуемый тип (например, из-за синтаксически некорректного JSON
     * или несоответствия типов, если это не покрывается более специфичным
     * HttpMessageNotReadableException).
     * Возвращает {@link ProblemDetail} для ответа HTTP 400 Bad Request.
     *
     * @param ex      Исключение {@link HttpMessageConversionException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     */
    @ExceptionHandler(HttpMessageConversionException.class)
    public ProblemDetail handleHttpMessageConversionException(HttpMessageConversionException ex, WebRequest request) {
        String requestUriPath = "N/A";
        if (request instanceof ServletWebRequest servletWebRequest) {
            requestUriPath = servletWebRequest.getRequest().getRequestURI();
        }
        log.warn("HTTP message conversion error: Request to [{}], Details: {}",
                requestUriPath,
                ex.getMessage());

        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "request.body.conversionError",
                request.getLocale(),
                Map.of("error_summary", "The request body could not be processed due to a conversion or " +
                        "formatting error.")
        );
        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Переопределенный метод для обработки {@link HttpMessageNotReadableException}.
     * <p>
     * Этот метод делегирует фактическую обработку и формирование {@link ProblemDetail}
     * методу {@link #handleHttpMessageConversionException(HttpMessageConversionException, WebRequest)}.
     * {@link HttpMessageNotReadableException} является подклассом
     * {@link HttpMessageConversionException}, и логика формирования ответа для них схожа.
     * </p>
     * <p>
     * Возвращает {@link ResponseEntity}, сконструированный на основе {@link ProblemDetail},
     * полученного от делегированного обработчика. HTTP-статус ответа будет определен
     * значением {@code ProblemDetail.getStatus()}.
     * </p>
     *
     * @param ex             Исключение {@link HttpMessageNotReadableException}.
     * @param ignoredHeaders Заголовки HTTP (игнорируются, так как передаются пустые значения).
     * @param ignoredStatus  HTTP-статус, предложенный Spring (игнорируется, так как статус
     *                       берется из {@code ProblemDetail}).
     * @param request        Текущий веб-запрос.
     * @return {@link ResponseEntity} с {@link ProblemDetail} в теле.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders ignoredHeaders,
            @NonNull HttpStatusCode ignoredStatus,
            @NonNull WebRequest request) {

        log.debug("Delegating HttpMessageNotReadableException to HttpMessageConversionException handler for request to [{}].",
                (request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "N/A"));

        ProblemDetail problemDetail = handleHttpMessageConversionException(ex, request);

        return ResponseEntity.of(problemDetail).build();
    }

    // --- Обработчик для отсутствующих ключей локализации ---

    /**
     * Обрабатывает {@link NoSuchMessageException}, которая выбрасывается {@link MessageSource},
     * если ключ для локализованного сообщения не найден. Это указывает на критическую проблему
     * конфигурации приложения и должно приводить к ошибке сервера HTTP 500.
     * <p>
     * В поле {@code properties} объекта {@code ProblemDetail} добавляется:
     * <ul>
     *     <li>{@code missing_resource_info}: Оригинальное сообщение из {@link NoSuchMessageException},
     *         обычно содержащее имя отсутствующего ключа.</li>
     * </ul>
     * Поля {@code title} и {@code detail} для этого ответа извлекаются из {@link MessageSource}
     * по ключу {@code "internal.missingMessageResource"}. Если и эти ключи отсутствуют,
     * используются жестко закодированные фолбэки.
     * </p>
     *
     * @param ex      Исключение {@link NoSuchMessageException}.
     * @param request Текущий веб-запрос.
     * @return {@link ProblemDetail} для ответа HTTP 500 Internal Server Error.
     */
    @ExceptionHandler(NoSuchMessageException.class)
    public ProblemDetail handleNoSuchMessageException(NoSuchMessageException ex, WebRequest request) {
        log.error("CRITICAL CONFIGURATION ERROR: Required message resource not found. " +
                        "Locale: {}. Original exception message: '{}'. Request: {}",
                request.getLocale(), ex.getMessage(), request.getDescription(false), ex);

        String initialTypeSuffix = "internal.missingMessageResource";
        URI typeUri = URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + initialTypeSuffix.replaceAll("\\.", "/"));
        String title;
        String detail;

        Map<String, Object> properties = new LinkedHashMap<>();
        if (ex.getMessage() != null) {
            properties.put("missing_resource_info", ex.getMessage()); // Оригинальная ошибка
        }
        try {
            // Попытка получить стандартные сообщения для этой ошибки
            title = messageSource.getMessage("problemDetail." + initialTypeSuffix + ".title", null, request.getLocale());
            detail = messageSource.getMessage("problemDetail." + initialTypeSuffix + ".detail",
                    null, request.getLocale()); // передаем ex.getMessage() как аргумент
        } catch (NoSuchMessageException localizationEmergency) {
            log.error("ULTRA-CRITICAL: Message resources for localization error handler itself are missing! Key: {}",
                    localizationEmergency.getMessage(), localizationEmergency);
            typeUri = URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "internal/localization-emergency");
            title = "Internal Server Error"; // Абсолютный фолбэк
            detail = "A critical internal configuration error occurred regarding localization. Please contact support. " +
                    "Details: " + ex.getMessage(); // Включаем исходную проблему
            if (localizationEmergency.getMessage() != null) {
                properties.put("secondary_missing_resource_info", localizationEmergency.getMessage()); // Ошибка загрузки сообщения об ошибке
            }
        }

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setType(typeUri);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        if (!properties.isEmpty()) {
            properties.forEach(problemDetail::setProperty);
        }
        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Обрабатывает {@link IllegalStateException}, указывающее на неожиданное
     * внутреннее состояние или ошибку программирования/конфигурации.
     * Возвращает {@link ProblemDetail} для ответа HTTP 500 Internal Server Error.
     * <p>
     * В поле {@code properties} объекта {@code ProblemDetail} добавляется:
     * <ul>
     *     <li>{@code error_ref}: Уникальный идентификатор ошибки для отслеживания.</li>
     * </ul>
     * Клиенту сообщается об общей внутренней ошибке и предоставляется этот ID.
     * Детали самого {@link IllegalStateException} не передаются клиенту, но логируются на сервере.
     * </p>
     *
     * @param ex      Исключение {@link IllegalStateException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключи для {@code title} или {@code detail} (для "internal.illegalState")
     *                                не найдены в {@link MessageSource}.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        String errorRef = UUID.randomUUID().toString();

        log.error("Illegal state encountered (Ref: {}): {}", errorRef, ex.getMessage(), ex);
        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal.illegalState", // Новый тип проблемы
                request.getLocale(),
                Map.of("error_ref", errorRef)
        );
        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }


    /**
     * Вспомогательный метод для создания и базовой настройки {@link ProblemDetail}.
     * <p>
     * Извлекает {@code title} и {@code detail} из {@link MessageSource} по ключам,
     * сформированным на основе {@code typeSuffix}. Предполагается, что ключи для {@code detail}
     * в {@code messages.properties}, используемые этим методом, **не требуют аргументов**.
     * Динамическая информация должна передаваться через {@code additionalProperties}.
     * </p>
     * <p>
     * Если ключ для {@code title} или {@code detail} не найден в {@link MessageSource},
     * будет выброшено {@link NoSuchMessageException}, которая должна быть обработана
     * {@link #handleNoSuchMessageException(NoSuchMessageException, WebRequest)}.
     * </p>
     *
     * @param status               HTTP-статус для {@link ProblemDetail}. Не должен быть null.
     * @param typeSuffix           Суффикс для формирования URI типа проблемы ({@link ProblemDetail#setType(URI)})
     *                             и ключей для {@link MessageSource}. Не должен быть null.
     * @param locale               Локаль для получения сообщений. Не должна быть null.
     * @param additionalProperties Карта с дополнительными, не стандартными свойствами для
     *                             {@link ProblemDetail} (может быть {@code null}).
     * @return Сконфигурированный объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключ для {@code title} или {@code detail} не найден.
     */
    private ProblemDetail buildProblemDetail(@NonNull HttpStatus status,
                                             @NonNull String typeSuffix,
                                             @NonNull Locale locale,
                                             @Nullable Map<String, Object> additionalProperties) {

        String titleKey = "problemDetail." + typeSuffix + ".title";
        String detailKey = "problemDetail." + typeSuffix + ".detail";
        URI typeUri = URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + typeSuffix.replaceAll("\\.", "/"));


        String title = messageSource.getMessage(titleKey, null, locale);
        String detail = messageSource.getMessage(detailKey, null, locale);

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

    /**
     * Вспомогательный метод для извлечения имени поля из полного пути свойства,
     * возвращаемого {@link jakarta.validation.ConstraintViolation#getPropertyPath()}.
     * Например, из "registerRequest.email" извлекает "email".
     *
     * @param propertyPath Полный путь к свойству.
     * @return Имя поля или исходный путь, если точка не найдена.
     */
    private String getFieldNameFromPath(String propertyPath) {
        // propertyPath может быть сложным, например, "methodName.arg0.fieldName"
        // Пытаемся извлечь последнюю часть.
        if (propertyPath.contains(".")) {
            return propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
        }
        return propertyPath;
    }

    /**
     * Устанавливает поле {@link ProblemDetail#setInstance(URI)} в {@link ProblemDetail},
     * если оно еще не установлено. В качестве значения используется URI текущего запроса.
     *
     * @param problemDetail Объект {@link ProblemDetail} для модификации.
     * @param request       Текущий веб-запрос.
     */
    private void setInstanceUriIfAbsent(ProblemDetail problemDetail, WebRequest request) {
        if (problemDetail.getInstance() == null && request instanceof ServletWebRequest swr) {
            problemDetail.setInstance(URI.create(swr.getRequest().getRequestURI()));
        }
    }
}