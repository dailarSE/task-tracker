package com.example.tasktracker.backend.web.exception;

import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException; // Важно для Javadoc
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
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
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    // TODO: (URI для ProblemDetail) - Определить и задокументировать пространство имен для type URI.
    private static final String PROBLEM_TYPE_BASE_URI = "https://task-tracker.example.com/probs/";

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
        String userName = Optional.ofNullable(request.getUserPrincipal()).map(java.security.Principal::getName)
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
    private ProblemDetail buildProblemDetail(@lombok.NonNull Exception sourceException,
                                             @lombok.NonNull HttpStatus status,
                                             @lombok.NonNull String typeSuffix,
                                             @lombok.NonNull Locale locale,
                                             @Nullable Map<String, Object> additionalProperties) {

        String titleKey = "problemDetail." + typeSuffix + ".title";
        String detailKey = "problemDetail." + typeSuffix + ".detail";
        URI typeUri = URI.create(PROBLEM_TYPE_BASE_URI + typeSuffix);

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
}