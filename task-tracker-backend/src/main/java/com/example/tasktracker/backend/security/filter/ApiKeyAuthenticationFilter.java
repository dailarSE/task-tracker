package com.example.tasktracker.backend.security.filter;

import com.example.tasktracker.backend.security.apikey.ApiKeyAuthentication;
import com.example.tasktracker.backend.security.apikey.ApiKeyValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Фильтр Spring Security для аутентификации по статическому API-ключу.
 * <p>
 * Извлекает ключ из заголовка "X-API-Key" и валидирует его с помощью
 * {@link ApiKeyValidator}. В случае успеха, устанавливает в SecurityContext
 * объект {@link ApiKeyAuthentication}. В случае неудачи, делегирует обработку
 * ошибки в {@link AuthenticationEntryPoint}.
 * </p>
 */
@Component
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER_NAME = "X-API-Key";

    private final ApiKeyValidator apiKeyValidator;

    private final AuthenticationEntryPoint authenticationEntryPoint;

    public ApiKeyAuthenticationFilter(
            ApiKeyValidator apiKeyValidator,
            @Qualifier("apiKeyAuthenticationEntryPoint") AuthenticationEntryPoint authenticationEntryPoint) {
        this.apiKeyValidator = apiKeyValidator;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            log.trace("SecurityContext already contains an authenticated principal for [{}]. Skipping JWT processing.",
                    request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER_NAME);

        if (!StringUtils.hasText(providedKey)) {
            log.warn("Missing API Key in header '{}' for request to {}", API_KEY_HEADER_NAME, request.getRequestURI());
            this.authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("API Key is missing."));
            return;
        }

        if (apiKeyValidator.isValid(providedKey)) {
            log.trace("Valid API Key found. Setting Authentication in SecurityContext.");
            Authentication authentication = ApiKeyAuthentication.authenticated("internal-service");
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } else {
            log.warn("Invalid API Key provided for request to {}", request.getRequestURI());
            this.authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Invalid API Key provided."));
        }
    }
}