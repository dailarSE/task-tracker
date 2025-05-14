package com.example.tasktracker.backend.security.filter;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Фильтр Spring Security, ответственный за обработку JWT (JSON Web Token) аутентификации.
 * <p>
 * Для каждого входящего запроса этот фильтр выполняет следующие действия:
 * <ol>
 *     <li>Проверяет, не был ли пользователь уже аутентифицирован ранее в цепочке фильтров. Если да, JWT не обрабатывается, и запрос передается дальше.</li>
 *     <li>Пытается извлечь JWT из заголовка {@code Authorization} (ожидая префикс "Bearer ").</li>
 *     <li>Если токен отсутствует, запрос передается дальше по цепочке фильтров без установления аутентификации.
 *         Если ресурс требует аутентификации, это будет обработано стандартными механизмами Spring Security (например, {@link AuthenticationEntryPoint}).</li>
 *     <li>Если токен присутствует, он валидируется с помощью {@link JwtValidator}.</li>
 *     <li>Если токен валиден:
 *         <ul>
 *             <li>Извлекаются claims.</li>
 *             <li>{@link JwtAuthenticationConverter} используется для преобразования claims в объект {@link Authentication}.</li>
 *             <li>Объект {@link Authentication} устанавливается в {@link SecurityContextHolder}, делая пользователя аутентифицированным для текущего запроса. Запрос передается дальше по цепочке фильтров.</li>
 *             <li>Если во время конвертации claims возникает ошибка (например, {@link IllegalArgumentException}),
 *                 инициируется обработка ошибки через {@link AuthenticationEntryPoint} с {@link BadJwtException},
 *                 и дальнейшая обработка запроса в цепочке фильтров для данного запроса **прерывается**.</li>
 *         </ul>
 *     </li>
 *     <li>Если токен невалиден (согласно {@link JwtValidator}):
 *         <ul>
 *             <li>Инициируется обработка ошибки через {@link AuthenticationEntryPoint} с {@link BadJwtException},
 *                 содержащим детали ошибки валидации JWT. Дальнейшая обработка запроса в цепочке фильтров
 *                 для данного запроса прерывается.</li>
 *         </ul>
 *     </li>
 * </ol>
 * В случаях, когда {@link AuthenticationEntryPoint} не вызывается (успешная аутентификация по JWT или отсутствие JWT),
 * запрос всегда передается дальше по цепочке фильтров.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    /**
     * Конструктор для {@link JwtAuthenticationFilter}.
     *
     * @param jwtValidator               Сервис для валидации JWT. Не должен быть null.
     * @param jwtAuthenticationConverter Сервис для конвертации JWT Claims в {@link Authentication}. Не должен быть null.
     * @param authenticationEntryPoint   Точка входа для обработки ошибок аутентификации. Не должна быть null.
     */
    public JwtAuthenticationFilter(
            @NonNull JwtValidator jwtValidator,
            @NonNull JwtAuthenticationConverter jwtAuthenticationConverter,
            @NonNull AuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtValidator = jwtValidator;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * Основной метод фильтра, выполняемый для каждого запроса для обработки JWT аутентификации.
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
            log.trace("SecurityContext already contains an authenticated principal for [{}]. Skipping JWT processing.",
                    request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

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
                log.debug("Successfully authenticated user [{}] from JWT for request to [{}].",
                        (authentication.getPrincipal() instanceof UserDetails ?
                                ((UserDetails) authentication.getPrincipal()).getUsername() :
                                authentication.getName()),
                        request.getRequestURI()
                );
            } catch (IllegalArgumentException e) {
                log.warn("Could not set user authentication from JWT: Claims conversion failed. Token: [{}], Error: {}",
                        JwtValidator.truncateTokenForLogging(jwt), e.getMessage(), e);
                // Ошибка конвертации claims после валидного токена - это специфичная ошибка JWT.
                this.authenticationEntryPoint.commence(request, response,
                        new BadJwtException(
                                "JWT claims conversion error: " + e.getMessage(),
                                JwtErrorType.OTHER_JWT_EXCEPTION,
                                e
                        ));
                return; // Ответ формируется EntryPoint
            }
        } else {
            // Токен был, но не прошел валидацию. JwtValidator уже залогировал причину.
            log.debug("JWT validation failed for request to [{}]. ErrorType: {}, Message: {}",
                    request.getRequestURI(), validationResult.getErrorType(), validationResult.getErrorMessage());
            this.authenticationEntryPoint.commence(request, response,
                    new BadJwtException(
                            validationResult.getErrorMessage(),
                            validationResult.getErrorType(),
                            validationResult.getCause()
                    ));
            return; // Ответ формируется EntryPoint
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Извлекает JWT из заголовка "Authorization".
     * Токен должен иметь префикс "Bearer ".
     *
     * @param request HTTP запрос.
     * @return {@link Optional<String>} содержащий токен, если он найден и корректно оформлен,
     * иначе {@link Optional#empty()}.
     */
    private Optional<String> extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return Optional.of(authHeader.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}