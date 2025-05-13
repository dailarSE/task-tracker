package com.example.tasktracker.backend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;

/**
 * Сервис, ответственный за валидацию JWT Access Tokens и извлечение из них Claims.
 * Использует секретный ключ от {@link JwtKeyService} и {@link Clock} для корректной
 * проверки временных claims.
 * Логирует различные типы ошибок валидации токена.
 */
@Service
@Slf4j
public class JwtValidator {

    private final Clock clock;
    private final SecretKey secretKey;

    /**
     * Конструктор для {@link JwtValidator}.
     *
     * @param jwtKeyService Сервис для получения предварительно инициализированного и валидированного секретного ключа.
     *                      Не должен быть null.
     * @param clock         Часы для получения текущего времени, используемые при валидации. Не должен быть null.
     */
    public JwtValidator(@NonNull JwtKeyService jwtKeyService, @NonNull Clock clock) {
        this.secretKey = jwtKeyService.getSecretKey();
        this.clock = clock;
    }

    /**
     * Приватный метод для парсинга и валидации токена.
     * Является стандартным способом проверки токена: если парсинг прошел успешно, токен валиден.
     *
     * @param token Строка JWT для парсинга.
     * @return {@link java.util.Optional} содержащий {@link Jws Jws&lt;Claims&gt;}
     *         (распарсенный JWS, включая заголовки и тело/claims), если токен валиден;
     *         иначе {@link java.util.Optional#empty()}.
     */
    private Optional<Jws<Claims>> parseAndValidateToken(String token) {
        if (token == null || token.isBlank()) {
            log.debug("Token validation failed: token is null or blank.");
            return Optional.empty();
        }
        try {
            Jws<Claims> jwsClaims = Jwts.parser()
                    .verifyWith(this.secretKey)
                    .clock(() -> Date.from(this.clock.instant()))
                    .build()
                    .parseSignedClaims(token); // parseSignedClaims возвращает Jws<Claims>
            return Optional.of(jwsClaims);
        } catch (SignatureException e) {
            log.warn("Token validation failed: Invalid JWT signature. Token: [{}]", truncateTokenForLogging(token), e);
        } catch (MalformedJwtException e) {
            log.warn("Token validation failed: Invalid JWT token format. Token: [{}]", truncateTokenForLogging(token), e);
        } catch (ExpiredJwtException e) {
            log.debug("Token validation failed: JWT token is expired. Token: [{}]", truncateTokenForLogging(token), e);
        } catch (UnsupportedJwtException e) {
            log.warn("Token validation failed: JWT token is unsupported. Token: [{}]", truncateTokenForLogging(token), e);
        } catch (IllegalArgumentException e) {
            log.warn("Token validation failed: JWT claims string is empty or token is otherwise invalid. Token: [{}]",
                    truncateTokenForLogging(token), e);
        } catch (JwtException e) { // Общий JwtException для других ошибок парсинга/валидации
            log.warn("Token validation failed: General JWT error. Token: [{}]", truncateTokenForLogging(token), e);
        }
        return Optional.empty();
    }

    /**
     * Проверяет, является ли предоставленный JWT валидным.
     *
     * @param token Строка JWT для валидации.
     * @return {@code true} если токен валиден, иначе {@code false}.
     */
    public boolean isValid(String token) {
        return parseAndValidateToken(token).isPresent();
    }

    /**
     * Извлекает тело {@link Claims} из JWT, если токен валиден.
     *
     * @param token Строка JWT.
     * @return {@link Optional<Claims>}, содержащий Claims если токен валиден, иначе {@link Optional#empty()}.
     */
    public Optional<Claims> extractValidClaims(String token) {
        return parseAndValidateToken(token).map(Jws::getPayload);
    }

    /**
     * Вспомогательный метод для сокращения токена в логах.
     *
     * @param token JWT строка.
     * @return Сокращенная версия токена для логирования.
     */
    public String truncateTokenForLogging(String token) {
        if (token == null) {
            return "[NULL_TOKEN]";
        }
        if (token.isBlank()) {
            return "[BLANK_TOKEN]";
        }

        int tokenLength = token.length();
        int prefixSuffixLength = 8;
        String ellipsis = "...";
        int minLengthForFullTruncation = prefixSuffixLength * 2 + ellipsis.length();

        if (tokenLength > minLengthForFullTruncation) {
            return token.substring(0, prefixSuffixLength) + ellipsis + token.substring(tokenLength - prefixSuffixLength);
        } else {
            // Для токенов, которые слишком коротки для отображения начала и конца, возвращаем маску с указанием длины.
            return "[SHORT_TOKEN_LEN:" + tokenLength + "]";
        }
    }
}