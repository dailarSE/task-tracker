package com.example.tasktracker.backend.web.exception;

import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import com.example.tasktracker.backend.web.ApiConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.ConstraintViolationException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
     * Поля {@code title} и {@code detail} извлекаются из {@link MessageSource} по ключам,
     * сформированным на основе {@code "jwt." + ex.getErrorType().name().toLowerCase()}.
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
        String title = messageSource.getMessage("problemDetail." + typeSuffix + ".title", null, request.getLocale());
        String detail = messageSource.getMessage("problemDetail." + typeSuffix + ".detail", null, request.getLocale());

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + typeSuffix.replace('.', '/')));

        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Обрабатывает общие {@link AuthenticationException}, которые не были перехвачены
     * более специфичными обработчиками (например, {@link BadJwtException} или {@link BadCredentialsException}).
     * Возвращает {@link ProblemDetail} для ответа HTTP 401 Unauthorized.
     *
     * @param ex      Исключение {@link AuthenticationException}.
     * @param request Текущий веб-запрос.
     * @return Объект {@link ProblemDetail}.
     * @throws NoSuchMessageException если ключи для {@code title} или {@code detail} не найдены.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failure (general): {}", ex.getMessage(), ex);

        String typeSuffix = "unauthorized";
        String title = messageSource.getMessage("problemDetail." + typeSuffix + ".title", null, request.getLocale());
        String detail = messageSource.getMessage("problemDetail." + typeSuffix + ".detail", null, request.getLocale());

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "unauthorized"));

        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Обрабатывает {@link AccessDeniedException}, возникающее при попытке доступа
     * аутентифицированного пользователя к ресурсу, на который у него нет прав.
     * Возвращает {@link ProblemDetail} для ответа HTTP 403 Forbidden.
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

        String typeSuffix = "forbidden";
        String title = messageSource.getMessage("problemDetail." + typeSuffix + ".title", null, request.getLocale());
        String detail = messageSource.getMessage("problemDetail." + typeSuffix + ".detail", null, request.getLocale());

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "forbidden"));

        setInstanceUriIfAbsent(problemDetail, request);
        return problemDetail;
    }

    /**
     * Обрабатывает {@link BadCredentialsException}, возникающее при неудачной попытке входа пользователя
     * (например, через эндпоинт {@code /api/v1/auth/login}).
     * Возвращает {@link ProblemDetail} для ответа HTTP 401 Unauthorized.
     * Устанавливает заголовок {@code WWW-Authenticate: Bearer realm="task-tracker"}.
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

        String typeSuffix = "auth.invalidCredentials";
        String title = messageSource.getMessage("problemDetail." + typeSuffix + ".title", null, request.getLocale());
        String detail = messageSource.getMessage("problemDetail." + typeSuffix + ".detail", null, request.getLocale());

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "auth/invalid-credentials"));

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
     *     <li>{@code invalidParams}: Список объектов, каждый из которых содержит {@code field} (имя поля)
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

        String typeSuffix = "validation.constraintViolation";
        String title = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".title", null, request.getLocale());
        String detail = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".detail", new Object[]{ex.getConstraintViolations().size()}, request.getLocale());

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "validation/constraint-violation"));
        problemDetail.setProperty("invalidParams", errors);
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
     *     <li>{@code invalidParams}: Список объектов, каждый из которых содержит {@code field} (имя поля),
     *         {@code rejectedValue} (отклоненное значение) и {@code message} (сообщение об ошибке валидации).</li>
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

        // 1. Собираем ошибки полей
        List<Map<String, Object>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.<String, Object>of(
                        "field", error.getField(),
                        "rejectedValue", Optional.ofNullable(error.getRejectedValue()).map(Object::toString).orElse("null"),
                        "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : ""
                ))
                .toList();

        // 2. Собираем глобальные ошибки
        List<Map<String, Object>> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(error -> Map.<String, Object>of(
                        // Для глобальной ошибки нет поля, используем имя объекта или специальный маркер
                        "global", error.getObjectName(),
                        "rejectedValue", "N/A", // Нет конкретного значения
                        "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : ""
                ))
                .toList();

        // 3. Объединяем их в один список
        List<Map<String, Object>> allErrors = new ArrayList<>();
        allErrors.addAll(fieldErrors);
        allErrors.addAll(globalErrors);

        String typeSuffix = "validation.methodArgumentNotValid";
        String title = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".title", null, request.getLocale());
        String detail = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".detail", new Object[]{ex.getErrorCount()}, request.getLocale());

        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "validation/method-argument-not-valid"));
        problemDetail.setProperty("invalidParams", allErrors);
        setInstanceUriIfAbsent(problemDetail, request);

        return new ResponseEntity<>(problemDetail, headers, status);
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

        String userFriendlyCause = "Invalid request format."; // Сообщение по умолчанию
        Throwable rootCause = ex.getMostSpecificCause();
        if (rootCause instanceof JsonProcessingException jsonProcessingException) {
            userFriendlyCause = jsonProcessingException.getOriginalMessage();
        }
        // другие парсеры (например XML)

        log.warn("HTTP message conversion error: Request to [{}], Root Cause: {}, Original Exception: {}",
                requestUriPath,
                rootCause.getClass().getName(),
                ex.getMessage());

        String typeSuffix = "request.body.conversionError";
        String title = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".title", null, request.getLocale());
        String detail = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".detail", new Object[]{userFriendlyCause}, request.getLocale());

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "request/body-conversion-error"));
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

    /**
     * Кастомизирует обработку {@link TypeMismatchException} (например, для ошибок
     * конвертации PathVariable или RequestParam) для формирования ответа в формате Problem Details.
     * Переопределяет стандартное поведение из {@link ResponseEntityExceptionHandler}.
     *
     * @param ex      Исключение {@link TypeMismatchException}.
     * @param headers Заголовки HTTP.
     * @param status  HTTP-статус, предложенный Spring (обычно {@link HttpStatus#BAD_REQUEST}).
     * @param request Текущий веб-запрос.
     * @return {@link ResponseEntity} с {@link ProblemDetail} в теле.
     * @throws NoSuchMessageException если ключи для {@code title} или {@code detail} не найдены.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            @NonNull TypeMismatchException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        String parameterName = ex.getPropertyName(); // Имя параметра
        Object value = ex.getValue();
        String requiredType = Optional.ofNullable(ex.getRequiredType()).map(Class::getSimpleName).orElse("N/A");

        // MethodArgumentTypeMismatchException дает более точное имя параметра из аннотации
        if (ex instanceof MethodArgumentTypeMismatchException matme) {
            parameterName = matme.getName();
        }

        log.warn("Request parameter type mismatch: Parameter '{}' with value '{}' could not be converted to type '{}'. {}",
                parameterName, value, requiredType, ex.getMessage());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("field", parameterName);
        properties.put("rejectedValue", value != null ? value.toString() : "null");
        properties.put("expectedType", requiredType);

        String typeSuffix = "request.parameter.typeMismatch";
        String title = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".title", null, request.getLocale());
        String detail = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".detail",
                new Object[]{parameterName,
                        value != null ? value.toString() : "null",
                        requiredType},
                request.getLocale());

        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "request/parameter-type-mismatch"));
        problemDetail.setProperties(properties);
        setInstanceUriIfAbsent(problemDetail, request);

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    // --- Обработчик для отсутствующих ключей локализации ---

    /**
     * Обрабатывает {@link NoSuchMessageException}, которая выбрасывается {@link MessageSource},
     * если ключ для локализованного сообщения не найден. Это указывает на критическую проблему
     * конфигурации приложения и должно приводить к ошибке сервера HTTP 500.
     * <p>
     * В поле {@code properties} объекта {@code ProblemDetail} добавляются:
     * <ul>
     *     <li>{@code errorRef}: Уникальный идентификатор для отслеживания ошибки.</li>
     *     <li>{@code missingResourceInfo}: Оригинальное сообщение из {@link NoSuchMessageException},
     *         содержащее имя отсутствующего ключа.</li>
     * </ul>
     * Поля {@code title} и {@code detail} для этого ответа извлекаются из {@link MessageSource}.
     * Поле {@code detail} включает в себя {@code errorRef}. Если и эти ключи отсутствуют,
     * используются жестко закодированные фолбэки, также включающие {@code errorRef}.
     * </p>
     *
     * @param ex      Исключение {@link NoSuchMessageException}.
     * @param request Текущий веб-запрос.
     * @return {@link ProblemDetail} для ответа HTTP 500 Internal Server Error.
     */
    @ExceptionHandler(NoSuchMessageException.class)
    public ProblemDetail handleNoSuchMessageException(NoSuchMessageException ex, WebRequest request) {
        // 1. Генерируем уникальный ID ошибки в самом начале
        String errorRef = UUID.randomUUID().toString();
        String originalMissingKeyInfo = ex.getMessage();

        // 2. Логируем полную информацию для себя
        log.error("CRITICAL CONFIGURATION ERROR (Ref: {}): Required message resource not found. " +
                        "Locale: {}. Original exception message: '{}'. Request: {}",
                errorRef, request.getLocale(), originalMissingKeyInfo, request.getDescription(false), ex);

        // 3. Готовим переменные для ответа
        String typeSuffix = "internal.missingMessageResource";
        URI typeUri = URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + typeSuffix.replaceAll("\\.", "/"));
        String title;
        String detail;

        // 4. Готовим `properties` для ответа
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("errorRef", errorRef);
        if (originalMissingKeyInfo != null) {
            properties.put("missingResourceInfo", originalMissingKeyInfo);
        }

        // 5. Пытаемся получить сообщения, обрабатывая "аварийный" случай
        try {
            title = messageSource.getMessage("problemDetail." + typeSuffix + ".title", null, request.getLocale());
            detail = messageSource.getMessage(
                    "problemDetail." + typeSuffix + ".detail", new Object[]{errorRef}, request.getLocale());
        } catch (NoSuchMessageException localizationEmergency) {
            // Аварийный случай: даже сообщения для этой ошибки отсутствуют
            log.error("ULTRA-CRITICAL (Ref: {}): Message resources for localization error handler itself are missing! Key: {}",
                    errorRef, localizationEmergency.getMessage(), localizationEmergency);

            typeUri = URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "internal/localization-emergency");
            title = "Internal Server Error"; // Абсолютный фолбэк
            detail = "A critical internal configuration error occurred regarding localization. " +
                    "Please contact support and provide the error reference ID: " + errorRef;

            if (localizationEmergency.getMessage() != null) {
                properties.put("secondaryMissingResourceInfo", localizationEmergency.getMessage());
            }
        }

        // 6. Собираем финальный ProblemDetail
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setType(typeUri);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setProperties(properties);

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
     *     <li>{@code errorRef}: Уникальный идентификатор ошибки для отслеживания.</li>
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
        return handleInternalServerError(ex, request, "internal.illegalState", null);
    }

    /**
     * Централизованно обрабатывает непредвиденные исключения, приводящие к HTTP 500.
     * Генерирует уникальный errorRef, логирует его вместе с полным исключением
     * и возвращает клиенту стандартизированный ProblemDetail.
     *
     * @param ex             Исключение, вызвавшее ошибку.
     * @param request        Текущий веб-запрос.
     * @param typeSuffix     Суффикс для ключей MessageSource и URI типа проблемы.
     * @param additionalProps Дополнительные properties (кроме errorRef).
     * @return Сконфигурированный ProblemDetail.
     */
    private ProblemDetail handleInternalServerError(Exception ex, WebRequest request,
                                                    String typeSuffix, Map<String, Object> additionalProps) {
        String errorRef = UUID.randomUUID().toString();
        log.error("Internal Server Error (Ref: {}): {}", errorRef, ex.getMessage(), ex);

        // Формируем `properties` для ответа, всегда включая errorRef
        Map<String, Object> properties = new LinkedHashMap<>();
        if (additionalProps != null) {
            properties.putAll(additionalProps);
        }
        properties.put("errorRef", errorRef);

        // Получаем title и detail из MessageSource
        String title = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".title", null, request.getLocale());

        // Передаем errorRef как аргумент для detail
        String detail = messageSource.getMessage(
                "problemDetail." + typeSuffix + ".detail", new Object[]{errorRef}, request.getLocale());

        // Создаем ProblemDetail
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + typeSuffix.replace('.', '/')));
        problemDetail.setProperties(properties);

        setInstanceUriIfAbsent(problemDetail, request);
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
     String getFieldNameFromPath(String propertyPath) {
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
     void setInstanceUriIfAbsent(ProblemDetail problemDetail, WebRequest request) {
        if (problemDetail.getInstance() == null && request instanceof ServletWebRequest swr) {
            problemDetail.setInstance(URI.create(swr.getRequest().getRequestURI()));
        }
    }
}