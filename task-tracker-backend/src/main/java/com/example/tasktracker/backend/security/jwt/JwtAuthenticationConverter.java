package com.example.tasktracker.backend.security.jwt;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.user.entity.User;
import io.jsonwebtoken.Claims;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Конвертер, преобразующий {@link Claims} из JWT в объект {@link Authentication} Spring Security.
 * Использует {@link JwtProperties} для получения имен кастомных claims.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationConverter {

    @NonNull
    private final JwtProperties jwtProperties;

    /**
     * Создает объект {@link Authentication} на основе claims, извлеченных из JWT.
     *
     * @param claims      Объект {@link Claims}, содержащий данные из валидного JWT. Не должен быть null.
     * @param rawJwtToken Оригинальная строка JWT, которая может быть использована как credentials
     *                    в объекте Authentication. Может быть null, если не используется.
     * @return Объект {@link Authentication} (обычно {@link UsernamePasswordAuthenticationToken})
     *         с {@link AppUserDetails} в качестве principal.
     * @throws IllegalArgumentException если обязательные claims отсутствуют или имеют неверный формат.
     */
    public Authentication convert(@NonNull Claims claims, String rawJwtToken) {
        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            log.warn("JWT 'sub' claim is not a valid Long: {}", claims.getSubject(), e);
            throw new IllegalArgumentException("Invalid 'sub' claim in JWT: not a valid user ID format.", e);
        }

        String email = claims.get(jwtProperties.getEmailClaimKey(), String.class);
        if (email == null || email.isBlank()) {
            log.warn("JWT '{}' claim is missing or blank.", jwtProperties.getEmailClaimKey());
            throw new IllegalArgumentException("Missing or blank '" +
                    jwtProperties.getEmailClaimKey() + "' claim in JWT.");
        }

        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setPassword("[N/A_JWT_AUTHENTICATED]");
        AppUserDetails userPrincipal = new AppUserDetails(user);

        return new UsernamePasswordAuthenticationToken(
                userPrincipal,
                rawJwtToken, // Можно передать сам токен как "credentials"
                userPrincipal.getAuthorities()
        );
    }
}