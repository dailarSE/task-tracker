package com.example.tasktracker.backend.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Реализация {@link AuthenticationEntryPoint} для обработки ошибок аутентификации
 * при доступе к защищенным ресурсам без валидной аутентификации (например, отсутствует токен,
 * или токен невалиден и {@link com.example.tasktracker.backend.security.filter.JwtAuthenticationFilter}
 * инициировал ошибку через этот EntryPoint).
 * <p>
 * Устанавливает заголовок {@code WWW-Authenticate: Bearer realm="task-tracker"}
 * и делегирует обработку {@link AuthenticationException} (которое может быть
 * {@link com.example.tasktracker.backend.security.exception.BadJwtException})
 * в {@link HandlerExceptionResolver}, чтобы {@code @ControllerAdvice}
 * мог сформировать ответ в формате RFC 9457 Problem Details.
 */
@Component("bearerTokenProblemDetailsAuthenticationEntryPoint")
@Slf4j
@RequiredArgsConstructor
public class BearerTokenProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    //ADR-0018
    private static final String WWW_AUTHENTICATE_HEADER_VALUE = "Bearer realm=\"task-tracker\"";

    /**
     * Стандартный HandlerExceptionResolver Spring MVC, который будет делегировать
     * исключение в @ControllerAdvice (GlobalExceptionHandler).
     */
    @NonNull
    private final HandlerExceptionResolver handlerExceptionResolver;


    /**
     * Вызывается, когда неаутентифицированный пользователь пытается получить доступ
     * к защищенному ресурсу.
     *
     * @param request       HTTP запрос.
     * @param response      HTTP ответ.
     * @param authException Исключение, вызвавшее ошибку аутентификации.
     *                      Может быть {@link com.example.tasktracker.backend.security.exception.BadJwtException}
     *                      или другим подклассом {@link AuthenticationException}.
     * @throws IOException      если возникает ошибка ввода-вывода при обработке ответа.
     * @throws ServletException если возникает ошибка сервлета.
     */
    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authException) {

        log.debug("BearerTokenProblemDetailsAuthenticationEntryPoint: commencing due to: {}", authException.getMessage());

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_HEADER_VALUE);

        this.handlerExceptionResolver.resolveException(request, response, null, authException);
    }
}