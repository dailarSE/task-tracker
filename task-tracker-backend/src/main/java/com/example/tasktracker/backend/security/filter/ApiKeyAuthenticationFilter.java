package com.example.tasktracker.backend.security.filter;

import com.example.tasktracker.backend.common.MdcKeys;
import com.example.tasktracker.backend.security.apikey.ApiKeyAuthentication;
import com.example.tasktracker.backend.security.apikey.ApiKeyValidator;
import com.example.tasktracker.backend.security.apikey.InvalidApiKeyException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Фильтр Spring Security для аутентификации по статическому API-ключу.
 * <p>
 * Извлекает ключ из заголовка "X-API-Key" и валидирует его с помощью
 * {@link ApiKeyValidator}. В случае успеха, устанавливает в SecurityContext
 * объект {@link ApiKeyAuthentication}. В случае неудачи, делегирует обработку
 * ошибки в {@link AuthenticationEntryPoint}.
 * </p>
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER_NAME = "X-API-Key";
    public static final String SERVICE_INSTANCE_ID_HEADER_NAME = "X-Service-Instance-Id";
    public static final String UNKNOWN_INSTANCE_ID = "[unknown-instance]";

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
            log.trace("SecurityContext already contains an authenticated principal for [{}].",
                    request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER_NAME);

        if (!StringUtils.hasText(providedKey)) {
            log.warn("Missing API Key in header '{}' for request to {}", API_KEY_HEADER_NAME, request.getRequestURI());
            this.authenticationEntryPoint.commence(request, response,
                    new InvalidApiKeyException("API Key is missing."));
            return;
        }

        Optional<String> serviceIdOptional = apiKeyValidator.getServiceIdIfValid(providedKey);

        if (serviceIdOptional.isPresent()) {
            String serviceId = serviceIdOptional.get();
            String instanceId = Optional.ofNullable(request.getHeader(SERVICE_INSTANCE_ID_HEADER_NAME))
                    .filter(StringUtils::hasText)
                    .orElseGet(() -> {
                        log.warn("Missing or blank '{}' header for an authenticated request from service '{}'. " +
                                "Using default placeholder.", SERVICE_INSTANCE_ID_HEADER_NAME, serviceId);
                        return UNKNOWN_INSTANCE_ID;
                    });

            try (
                    MDC.MDCCloseable ignoredSrv = MDC.putCloseable(MdcKeys.SERVICE_ID, serviceId);
                    MDC.MDCCloseable ignoredInst = MDC.putCloseable(MdcKeys.SERVICE_INSTANCE_ID, instanceId)
            ) {
                log.trace("Valid API Key. Service '{}', Instance '{}' authenticated.", serviceId, instanceId);
                Authentication authentication = new ApiKeyAuthentication(serviceId, instanceId);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                filterChain.doFilter(request, response);
            }
        } else {
            log.warn("Invalid API Key provided for request to {}", request.getRequestURI());
            this.authenticationEntryPoint.commence(request, response,
                    new InvalidApiKeyException("Invalid API Key provided."));
        }
    }
}