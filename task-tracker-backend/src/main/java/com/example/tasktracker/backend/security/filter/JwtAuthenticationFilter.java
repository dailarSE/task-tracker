package com.example.tasktracker.backend.security.filter;

import com.example.tasktracker.backend.security.jwt.JwtAuthenticationConverter;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils; // Для проверки строки токена
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Фильтр для аутентификации по JWT.
 * Извлекает JWT из заголовка Authorization, валидирует его и, в случае успеха,
 * устанавливает объект Authentication в SecurityContext.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @NonNull
    private final JwtValidator jwtValidator;
    @NonNull
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    /**
     * Основной метод фильтра, выполняемый для каждого запроса.
     *
     * @param request     HTTP запрос.
     * @param response    HTTP ответ.
     * @param filterChain Цепочка фильтров.
     * @throws ServletException если происходит ошибка сервлета.
     * @throws IOException      если происходит ошибка ввода-вывода.
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            log.trace("SecurityContext already contains an authentication object for [{}]. " +
                            "Proceeding without JWT processing.",
                    request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Пытаемся извлечь токен из запроса
        Optional<String> jwtOptional = extractTokenFromRequest(request);

        if (jwtOptional.isEmpty()) {
            log.trace("No JWT found in request to [{}]. Proceeding with filter chain.", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = jwtOptional.get();
        log.trace("JWT found in request to [{}]. Attempting to validate.", request.getRequestURI());

        // 2. Валидируем токен и извлекаем Claims
        Optional<Claims> claimsOptional = jwtValidator.extractValidClaims(jwt);

        if (claimsOptional.isPresent()) {
            // 3. Если токен валиден и Claims извлечены, создаем Authentication
            Claims claims = claimsOptional.get();
            try {
                Authentication authentication = jwtAuthenticationConverter.convert(claims, jwt);

                // 4. Устанавливаем Authentication в SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Successfully authenticated user [{}] from JWT for request to [{}].",
                        authentication.getName(), request.getRequestURI());
            } catch (IllegalArgumentException e) {
                // Если JwtAuthenticationConverter не смог создать Authentication из-за проблем с claims
                // (например, отсутствует обязательный claim, хотя токен структурно валиден)
                log.warn("Could not set user authentication in security context from JWT:" +
                                " Claims conversion failed. Token: [{}], Error: {}",
                        jwtValidator.truncateTokenForLogging(jwt), e.getMessage()); // Используем truncateTokenForLogging из JwtValidator
                // Очищаем контекст на всякий случай, если что-то там было установлено ранее (маловероятно)
                SecurityContextHolder.clearContext();
            }
        } else {
            // Если токен был, но не прошел валидацию в jwtValidator.extractValidClaims
            // (JwtValidator уже залогировал причину невалидности)
            log.debug("JWT validation failed for token from request to [{}].", request.getRequestURI());
        }

        // 5. Передаем управление дальше по цепочке фильтров
        filterChain.doFilter(request, response);
    }

    /**
     * Извлекает JWT из заголовка "Authorization".
     * Токен должен иметь префикс "Bearer ".
     *
     * @param request HTTP запрос.
     * @return {@link Optional<String>} содержащий токен, если он найден и корректно оформлен,
     *         иначе {@link Optional#empty()}.
     */
    private Optional<String> extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return Optional.of(authHeader.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}