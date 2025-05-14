package com.example.tasktracker.backend.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Реализация {@link AccessDeniedHandler} для обработки ошибок авторизации
 * (когда аутентифицированный пользователь пытается получить доступ к ресурсу,
 * на который у него нет прав).
 * <p>
 * Делегирует обработку {@link AccessDeniedException} в {@link HandlerExceptionResolver},
 * чтобы {@code @ControllerAdvice} мог сформировать ответ в формате RFC 9457 Problem Details
 * (обычно с HTTP-статусом 403 Forbidden или 404 Not Found, согласно ADR-0019).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {
    @NonNull
    private final HandlerExceptionResolver handlerExceptionResolver;

    /**
     * Вызывается, когда аутентифицированный пользователь пытается получить доступ
     * к ресурсу, но его прав недостаточно.
     *
     * @param request               HTTP запрос.
     * @param response              HTTP ответ.
     * @param accessDeniedException Исключение, указывающее на отказ в доступе.
     * @throws IOException      если возникает ошибка ввода-вывода.
     * @throws ServletException если возникает ошибка сервлета.
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) {

        log.debug("ProblemDetailsAccessDeniedHandler: handling access denied for user [{}] to [{} {}]. Reason: {}",
                (request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous"),
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException.getMessage());

        this.handlerExceptionResolver.resolveException(request, response, null, accessDeniedException);
    }
}