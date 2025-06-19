package com.example.tasktracker.backend.security.config;

import com.example.tasktracker.backend.security.apikey.ApiKeyValidator;
import com.example.tasktracker.backend.security.filter.ApiKeyAuthenticationFilter;
import com.example.tasktracker.backend.security.filter.JwtAuthenticationFilter;

import com.example.tasktracker.backend.security.jwt.JwtAuthenticationConverter;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static com.example.tasktracker.backend.web.ApiConstants.*;

/**
 * Основная конфигурация Spring Security для приложения Task Tracker.
 * <p>
 * Определяет несколько бинов {@link SecurityFilterChain}, каждый из которых
 * отвечает за свой набор путей (URL-паттернов). Порядок их применения
 * определяется аннотацией {@link Order}.
 * </p>
 * <p>
 * 1. {@code internalApiSecurityFilterChain} (@Order(1)): Защищает внутренние
 * M2M (machine-to-machine) эндпоинты по пути {@code /api/v1/internal/**}.
 * Использует аутентификацию по API-ключу.
 * </p>
 * <p>
 * 2. {@code publicApiSecurityFilterChain} (@Order(2)): Защищает публичные и
 * пользовательские API. Использует JWT-аутентификацию для защищенных
 * ресурсов и разрешает анонимный доступ к публичным эндпоинтам.
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtValidator jwtValidator;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ApiKeyValidator apiKeyValidator;
    private final AuthenticationEntryPoint bearerTokenAuthenticationEntryPoint;
    private final AuthenticationEntryPoint apiKeyAuthenticationEntryPoint;
    private final AccessDeniedHandler problemDetailsAccessDeniedHandler;
    private final String errorPath;

    public SecurityConfig(
            JwtValidator jwtValidator,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ApiKeyValidator apiKeyValidator,
            @Qualifier("bearerTokenProblemDetailsAuthenticationEntryPoint") AuthenticationEntryPoint
                    bearerTokenAuthenticationEntryPoint,
            @Qualifier("apiKeyAuthenticationEntryPoint") AuthenticationEntryPoint apiKeyAuthenticationEntryPoint,
            AccessDeniedHandler problemDetailsAccessDeniedHandler,
            @Value("${server.error.path:${error.path:/error}}") String errorPath) {
        this.jwtValidator = jwtValidator;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.apiKeyValidator = apiKeyValidator;
        this.bearerTokenAuthenticationEntryPoint = bearerTokenAuthenticationEntryPoint;
        this.apiKeyAuthenticationEntryPoint = apiKeyAuthenticationEntryPoint;
        this.problemDetailsAccessDeniedHandler = problemDetailsAccessDeniedHandler;
        this.errorPath = errorPath;
    }

    /**
     * Матчер для внутренних M2M API эндпоинтов.
     */
    private final RequestMatcher internalApiMatcher = new AntPathRequestMatcher("/api/v1/internal/**");

    /**
     * Матчер для всех путей, КРОМЕ внутренних API.
     * Используется NegatedRequestMatcher для обеспечения 100% изоляции от internalApiMatcher.
     */
    private final RequestMatcher publicAndUserApiMatcher = new NegatedRequestMatcher(internalApiMatcher);

    /**
     * Матчер для публичных эндпоинтов, не требующих аутентификации.
     */
    private final RequestMatcher publicEndpointsMatcher = new OrRequestMatcher(
            new AntPathRequestMatcher(REGISTER_ENDPOINT, HttpMethod.POST.name()),
            new AntPathRequestMatcher(LOGIN_ENDPOINT, HttpMethod.POST.name()),
            new AntPathRequestMatcher("/swagger-ui.html"),
            new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/v3/api-docs/**")
    );


    /**
     * Определяет бин {@link PasswordEncoder} для хеширования паролей.
     * Используется BCrypt.
     *
     * @return Реализация {@link PasswordEncoder}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Предоставляет бин {@link AuthenticationManager}, необходимый для явной аутентификации
     * (например, в {@code AuthService} при обработке логина).
     *
     * @param authenticationConfiguration Конфигурация аутентификации Spring.
     * @return {@link AuthenticationManager}.
     * @throws Exception если не удается получить AuthenticationManager.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Основная цепочка фильтров для публичных и пользовательских API.
     * Защищает все пути, кроме внутренних API, используя JWT-аутентификацию.
     *
     * @param http Конфигуратор HttpSecurity.
     * @return Сконфигурированный SecurityFilterChain.
     * @throws Exception при ошибке конфигурации.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtValidator, jwtAuthenticationConverter,bearerTokenAuthenticationEntryPoint);
        http
                .securityMatcher(publicAndUserApiMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(bearerTokenAuthenticationEntryPoint)
                        .accessDeniedHandler(problemDetailsAccessDeniedHandler)
                )
                // Определяем правила авторизации для HTTP-запросов
                .authorizeHttpRequests(authorize -> authorize
                        // Разрешаем публичный доступ к эндпоинтам регистрации и логина
                        .requestMatchers(publicEndpointsMatcher).permitAll()
                        .requestMatchers(errorPath).permitAll()
                        // TODO: Разрешить доступ к эндпоинтам Spring Boot Actuator (если они включены и нужны публично/защищенно)
                        // .requestMatchers("/actuator/**").permitAll() // или .hasRole("ADMIN") и т.д.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Цепочка фильтров для внутренних M2M API, защищенных API-ключом.
     * Применяется к путям, начинающимся с {@code /api/v1/internal/**}.
     *
     * @param http Конфигуратор HttpSecurity.
     * @return Сконфигурированный SecurityFilterChain.
     * @throws Exception при ошибке конфигурации.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain internalApiSecurityFilterChain(HttpSecurity http) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter =
                new ApiKeyAuthenticationFilter(apiKeyValidator, apiKeyAuthenticationEntryPoint);
        return http
                .securityMatcher(internalApiMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize ->
                        authorize.anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(apiKeyAuthenticationEntryPoint)
                        .accessDeniedHandler(problemDetailsAccessDeniedHandler)
                )
                .build();
    }

    /**
     * Конфигурация CORS.
     * Разрешает запросы с любых источников, основные HTTP-методы и заголовки,
     * включая Authorization для JWT. Это базовая конфигурация для разработки,
     * для production может потребоваться более строгая настройка.
     *
     * @return Источник конфигурации CORS.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        //TODO При использовании production-среды следует использовать конкретные домены
        configuration.setAllowedOriginPatterns(List.of("*")); // Разрешаем все origin-паттерны (для разработки)
        // В production лучше указать конкретные домены: List.of("http://localhost:3000", "https.yourdomain.com")
        configuration.setAllowedMethods(Arrays.asList(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(Arrays.asList(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "X-Requested-With" // Часто используется AJAX-запросами
        ));
        configuration.setExposedHeaders(List.of(X_ACCESS_TOKEN_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Применяем эту конфигурацию ко всем путям
        return source;
    }
}