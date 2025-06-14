package com.example.tasktracker.backend.security.config;

import com.example.tasktracker.backend.security.filter.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static com.example.tasktracker.backend.web.ApiConstants.*;

/**
 * Основная конфигурация Spring Security для приложения Task Tracker.
 * Определяет цепочку фильтров безопасности, правила доступа к эндпоинтам,
 * обработчики ошибок аутентификации/авторизации и другие ключевые бины безопасности.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationEntryPoint bearerTokenProblemDetailsAuthenticationEntryPoint;
    private final AccessDeniedHandler problemDetailsAccessDeniedHandler;

    @Value("${server.error.path:${error.path:/error}}")
    private String errorPath;

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
     * Определяет и конфигурирует основную цепочку фильтров безопасности {@link SecurityFilterChain}.
     *
     * @param http Конфигуратор {@link HttpSecurity}.
     * @return Сконфигурированный {@link SecurityFilterChain}.
     * @throws Exception если возникает ошибка при конфигурации.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(bearerTokenProblemDetailsAuthenticationEntryPoint)
                        .accessDeniedHandler(problemDetailsAccessDeniedHandler)
                )
                // Определяем правила авторизации для HTTP-запросов
                .authorizeHttpRequests(authorize -> authorize
                        // Разрешаем публичный доступ к эндпоинтам регистрации и логина
                        .requestMatchers(HttpMethod.POST, REGISTER_ENDPOINT).permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN_ENDPOINT).permitAll()
                        .requestMatchers(errorPath).permitAll()
                        // TODO: Разрешить доступ к эндпоинтам Spring Boot Actuator (если они включены и нужны публично/защищенно)
                        // .requestMatchers("/actuator/**").permitAll() // или .hasRole("ADMIN") и т.д.
                        .requestMatchers(
                                "/swagger-ui.html",         // Сам HTML-файл UI
                                "/swagger-ui/**",           // Статические ресурсы UI (JS, CSS, и т.д.)
                                "/v3/api-docs/**" // Конфигурационный файл, который запрашивает UI
                        ).permitAll() // В продакшн-среде swagger отключен, поэтому можно разрешить доступ

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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