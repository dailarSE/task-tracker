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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Фильтр Spring Security для аутентификации M2M (machine-to-machine) запросов
 * по статическому API-ключу.
 * <p>
 * Для каждого входящего запроса, предназначенного для внутреннего API,
 * этот фильтр выполняет следующие действия:
 * <ol>
 *     <li>Извлекает API-ключ из заголовка {@value #API_KEY_HEADER_NAME}.</li>
 *     <li>Извлекает идентификатор экземпляра сервиса из заголовка {@value #SERVICE_INSTANCE_ID_HEADER_NAME}.</li>
 *     <li>Если ключ найден, валидирует его с помощью {@link ApiKeyValidator} и получает ID сервиса.</li>
 *     <li>При успехе, создает объект {@link ApiKeyAuthentication},
 *         устанавливает его в {@code SecurityContextHolder} и помещает {@code serviceId} и
 *         {@code instanceId} в MDC для логирования.</li>
 *     <li>В случае ошибки (отсутствующий или невалидный ключ), выбрасывает
 *         {@link InvalidApiKeyException},
 *         которая обрабатывается глобально для формирования ответа 401 Unauthorized.</li>
 * </ol>
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER_NAME = "X-API-Key";
    public static final String SERVICE_INSTANCE_ID_HEADER_NAME = "X-Service-Instance-Id";
    public static final String UNKNOWN_INSTANCE_ID = "[unknown-instance]";
    @NonNull
    private final ApiKeyValidator apiKeyValidator;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String providedKey = request.getHeader(API_KEY_HEADER_NAME);

        if (!StringUtils.hasText(providedKey)) {
            log.warn("Missing API Key in header '{}' for request to {}", API_KEY_HEADER_NAME, request.getRequestURI());
            throw new InvalidApiKeyException("API Key is missing.");
        }

        Optional<String> serviceIdOptional = apiKeyValidator.getServiceIdIfValid(providedKey);

        if (serviceIdOptional.isPresent()) {
            String serviceId = serviceIdOptional.get();
            String instanceId = extractInstanceId(request, serviceId);

            try (
                    MDC.MDCCloseable ignoredSrv = MDC.putCloseable(MdcKeys.SERVICE_ID, serviceId);
                    MDC.MDCCloseable ignoredInst = MDC.putCloseable(MdcKeys.SERVICE_INSTANCE_ID, instanceId)
            ) {
                Authentication authentication = new ApiKeyAuthentication(serviceId, instanceId);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Valid API Key. Service '{}', Instance '{}' authenticated.", serviceId, instanceId);

                filterChain.doFilter(request, response);
            }
        } else {
            log.warn("Invalid API Key provided for request to {}", request.getRequestURI());
            throw new InvalidApiKeyException("Invalid API Key provided.");
        }
    }

    private static String extractInstanceId(HttpServletRequest request, String serviceId) {
        return Optional.ofNullable(request.getHeader(SERVICE_INSTANCE_ID_HEADER_NAME))
                .filter(StringUtils::hasText)
                .orElseGet(() -> {
                    log.warn("Missing or blank '{}' header for an authenticated request from service '{}'. " +
                            "Using default placeholder.", SERVICE_INSTANCE_ID_HEADER_NAME, serviceId);
                    return UNKNOWN_INSTANCE_ID;
                });
    }
}