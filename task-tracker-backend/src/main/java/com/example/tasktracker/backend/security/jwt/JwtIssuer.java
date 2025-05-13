package com.example.tasktracker.backend.security.jwt;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import io.jsonwebtoken.Jwts;
import lombok.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Сервис, ответственный за генерацию (выпуск) JWT Access Tokens.
 * Использует конфигурационные свойства из {@link JwtProperties},
 * секретный ключ от {@link JwtKeyService} и {@link Clock} для управления временем.
 */
@Service
public class JwtIssuer {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    private final Clock clock;

    /**
     * Конструктор для {@link JwtIssuer}.
     *
     * @param jwtProperties Конфигурационные свойства JWT. Не должен быть null.
     * @param jwtKeyService Сервис для получения предварительно инициализированного и валидированного секретного ключа. Не должен быть null.
     * @param clock         Часы для получения текущего времени. Не должен быть null.
     */
    public JwtIssuer(@NonNull JwtProperties jwtProperties,
                     @NonNull JwtKeyService jwtKeyService,
                     @NonNull Clock clock) {
        this.jwtProperties = jwtProperties;
        this.secretKey = jwtKeyService.getSecretKey();
        this.clock = clock;
    }

    /**
     * Генерирует JWT Access Token на основе данных аутентифицированного пользователя.
     *
     * @param authentication Объект {@link Authentication}, содержащий principal {@link AppUserDetails}.
     *                       Не должен быть null, и principal должен быть {@link AppUserDetails}.
     * @return Сгенерированный JWT Access Token в виде строки.
     * @throws IllegalArgumentException если authentication null или principal не является AppUserDetails.
     */
    public String generateToken(@NonNull Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AppUserDetails userPrincipal)) {
            throw new IllegalArgumentException(
                    "Principal in Authentication object must be an instance of AppUserDetails to generate a token. " +
                            "Found: " + (principal == null ? "null" : principal.getClass().getName())
            );
        }

        Instant now = Instant.now(clock);
        Date issuedAt = Date.from(now);
        Date expiration = Date.from(now.plusMillis(jwtProperties.getExpirationMs()));

        String authorities = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(userPrincipal.getId().toString())
                .claim(jwtProperties.getEmailClaimKey(), userPrincipal.getUsername()) // кастомный claim "email"
                .claim(jwtProperties.getAuthoritiesClaimKey(), authorities)        // кастомный claim "authorities"
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(this.secretKey, Jwts.SIG.HS256) // Алгоритм HS256
                .compact();
    }
}