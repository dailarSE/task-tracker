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
     *                    Ожидается, что claims будут содержать как минимум subject (ID пользователя в виде строки,
     *                    преобразуемой в Long) и claim с email адресом пользователя (имя этого claim
     *                    определяется в {@link JwtProperties#getEmailClaimKey()}).
     * @param rawJwtToken Оригинальная строка JWT, которая может быть использована как credentials
     *                    в объекте Authentication. Может быть null, если не используется.
     * @return Объект {@link Authentication} (обычно {@link UsernamePasswordAuthenticationToken})
     *         с {@link AppUserDetails} в качестве principal.
     * @throws IllegalArgumentException если обязательные claims (subject, email) отсутствуют,
     *                                  имеют неверный формат или subject не может быть преобразован в Long.
     */
    public Authentication convert(@NonNull Claims claims, String rawJwtToken) {
        long userId;
        try {
            String subject = claims.getSubject();
            if (subject == null) {
                log.warn("JWT 'sub' claim is missing.");
                throw new IllegalArgumentException("Missing 'sub' claim in JWT.");
            }
            userId = Long.parseLong(subject);
        } catch (NumberFormatException e) {
            log.warn("JWT 'sub' claim is not a valid Long: {}", claims.getSubject(), e);
            throw new IllegalArgumentException("Invalid 'sub' claim in JWT: not a valid user ID.", e);
        }

        String emailClaimKey = jwtProperties.getEmailClaimKey();
        String email = claims.get(emailClaimKey, String.class);
        if (email == null || email.isBlank()) {
            log.warn("JWT '{}' claim is missing or blank.", emailClaimKey);
            throw new IllegalArgumentException("Missing or blank '" + emailClaimKey + "' claim in JWT.");
        }

        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setPassword("[N/A_JWT_AUTHENTICATED]");
        AppUserDetails userPrincipal = new AppUserDetails(user);

        return new UsernamePasswordAuthenticationToken(
                userPrincipal,
                rawJwtToken, // Токен как "credentials"
                userPrincipal.getAuthorities()
        );
    }
}