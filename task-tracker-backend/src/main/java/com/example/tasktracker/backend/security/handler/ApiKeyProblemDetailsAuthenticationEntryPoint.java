package com.example.tasktracker.backend.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Реализация {@link AuthenticationEntryPoint} для обработки ошибок аутентификации
 * по API-ключу.
 * <p>
 * Делегирует обработку исключения {@link AuthenticationException} в
 * {@link HandlerExceptionResolver}, чтобы {@code @ControllerAdvice} мог сформировать
 * стандартизированный ответ в формате RFC 9457 Problem Details.
 * </p>
 */
@Component("apiKeyAuthenticationEntryPoint")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @NonNull
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) {

        log.debug("ApiKeyProblemDetailsAuthenticationEntryPoint commencing for exception: {}", authException.getMessage());
        handlerExceptionResolver.resolveException(request, response, null, authException);
    }
}
