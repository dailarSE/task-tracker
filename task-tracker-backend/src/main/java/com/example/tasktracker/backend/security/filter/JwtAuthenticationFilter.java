package com.example.tasktracker.backend.security.filter;

import com.example.tasktracker.backend.common.MdcKeys;
import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.jwt.JwtAuthenticationConverter;
import com.example.tasktracker.backend.security.jwt.JwtErrorType;
import com.example.tasktracker.backend.security.jwt.JwtValidationResult;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Фильтр Spring Security для обработки JWT (JSON Web Token) аутентификации.
 * <p>
 * Для каждого входящего запроса, не предназначенного для внутреннего API,
 * этот фильтр выполняет следующие действия:
 * <ol>
 *     <li>Извлекает "Bearer" токен из заголовка {@code Authorization}.</li>
 *     <li>Если токен найден, валидирует его с помощью {@link JwtValidator}.</li>
 *     <li>При успешной валидации, преобразует claims токена в объект
 *         {@link Authentication} и устанавливает его в {@code SecurityContextHolder},
 *         а также помещает ID пользователя в MDC для логирования.</li>
 *     <li>В случае любой ошибки (невалидный токен, ошибка конвертации claims),
 *         выбрасывает {@link com.example.tasktracker.backend.security.exception.BadJwtException}.
 *         Это исключение перехватывается стандартными механизмами Spring Security
 *         для формирования ответа 401 Unauthorized. Любые другие непредвиденные
 *         RuntimeException будут проброшены дальше и обработаны глобальными
 *         механизмами Spring Boot (как правило, приводя к ответу 500 Internal Server Error).
 *     </li>
 * </ol>
 * Если токен в запросе отсутствует, фильтр передает управление дальше по цепочке.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @NonNull
    private final JwtValidator jwtValidator;
    @NonNull
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        Optional<String> jwtOptional = extractTokenFromRequest(request);

        if (jwtOptional.isEmpty()) {
            log.trace("No JWT found in request to [{}]. Proceeding with filter chain.", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = jwtOptional.get();
        log.trace("JWT found in request to [{}]. Attempting to validate.", request.getRequestURI());

        JwtValidationResult validationResult = jwtValidator.validateAndParseToken(jwt);

        if (validationResult.isSuccess()) {
            Jws<Claims> jwsClaims = validationResult.getJwsClaimsOptional().orElseThrow(
                    () -> new IllegalStateException("JwtValidationResult isSuccess but no JwsClaims present. This should not happen.")
            );
            try {
                Authentication authentication = jwtAuthenticationConverter.convert(jwsClaims.getPayload(), jwt);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                String mdcUserIdentifier = prepareMdcUserIdentifier(authentication.getPrincipal());

                log.debug("Successfully authenticated user with ID [{}] from JWT for request to [{}].",
                        mdcUserIdentifier,
                        request.getRequestURI()
                );

                try (MDC.MDCCloseable ignored = MDC.putCloseable(MdcKeys.USER_ID, mdcUserIdentifier)) {
                    log.trace("MDC set for {}: {} for request to [{}].",
                            MdcKeys.USER_ID, mdcUserIdentifier, request.getRequestURI());
                    filterChain.doFilter(request, response); // Продолжаем цепочку фильтров
                }
                log.trace("MDC for {} (value: {}) cleared after request to [{}].",
                        MdcKeys.USER_ID, mdcUserIdentifier, request.getRequestURI());

            } catch (IllegalArgumentException e) {
                log.warn("Could not set user authentication from JWT: Claims conversion failed. Token: [{}], Error: {}",
                        JwtValidator.truncateTokenForLogging(jwt), e.getMessage(), e);
                // Ошибка конвертации claims после валидного токена - это специфичная ошибка JWT.
                throw new BadJwtException("JWT claims conversion error: " + e.getMessage(),
                        JwtErrorType.OTHER_JWT_EXCEPTION, e);
            }
        } else {
            // Токен был, но не прошел валидацию. JwtValidator уже залогировал причину.
            log.debug("JWT validation failed for request to [{}]. ErrorType: {}, Message: {}",
                    request.getRequestURI(), validationResult.getErrorType(), validationResult.getErrorMessage());
            throw new BadJwtException(validationResult.getErrorMessage(), validationResult.getErrorType(),
                    validationResult.getCause());
        }
    }

    /**
     * Готовит строковый идентификатор пользователя для помещения в MDC.
     *
     * @param principal объект principal из {@link Authentication}.
     * @return Строковый идентификатор пользователя (ID или email в качестве fallback).
     */
    String prepareMdcUserIdentifier(@NonNull Object principal) {
        if (principal instanceof AppUserDetails appUserDetails) {
            if (appUserDetails.getId() != null) {
                return appUserDetails.getId().toString();
            } else {
                // Это неожиданно, AppUserDetails должен иметь ID после успешной аутентификации
                log.warn("AppUserDetails principal found for MDC, but its ID is null. " +
                        "Falling back to username for MDC. Username: {}", appUserDetails.getUsername());
                return Objects.requireNonNull(appUserDetails.getUsername()); // Фолбэк на username, если ID null
            }
        }
        throw new IllegalStateException("Principal is not AppUserDetails. Principal: "
                + principal.getClass().getName());
    }

    /**
     * Извлекает JWT из заголовка "Authorization".
     */
    private Optional<String> extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return Optional.of(authHeader.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}