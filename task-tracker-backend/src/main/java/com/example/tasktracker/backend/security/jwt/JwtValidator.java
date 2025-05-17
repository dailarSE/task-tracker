package com.example.tasktracker.backend.security.jwt;

import io.jsonwebtoken.*;
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
 * Использует секретный ключ от {@link JwtKeyService} и {@link Clock} для корректной проверки временных claims.
 * Возвращает детальный результат валидации в виде {@link JwtValidationResult}.
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
     * Парсит и валидирует предоставленную JWT строку.
     * <p>
     * Этот метод выполняет полную проверку токена, включая:
     * <ul>
     *     <li>Проверку на null или пустое значение.</li>
     *     <li>Валидность формата JWT.</li>
     *     <li>Корректность подписи с использованием сконфигурированного секретного ключа.</li>
     *     <li>Срок действия токена (не истек ли он).</li>
     *     <li>Поддержку используемых в токене алгоритмов/функций.</li>
     * </ul>
     * <p>
     * В случае ошибки валидации, соответствующая информация логируется.
     *
     * @param token Строка JWT для валидации.
     * @return {@link JwtValidationResult}, содержащий либо {@link Jws<Claims>} (включая заголовки и тело)
     *         при успехе, либо информацию об ошибке ({@link JwtErrorType}, сообщение, причина) при неудаче.
     */
    public JwtValidationResult validateAndParseToken(String token) {
        if (token == null || token.isBlank()) {
            String msg = "Token is null or blank.";
            log.debug("Token validation failed: {}", msg);
            return JwtValidationResult.failure(JwtErrorType.EMPTY_OR_ILLEGAL_ARGUMENT, msg,null);
        }
        try {
            Jws<Claims> jwsClaims = Jwts.parser()
                    .verifyWith(this.secretKey)
                    .clock(() -> Date.from(this.clock.instant()))
                    .build()
                    .parseSignedClaims(token);
            return JwtValidationResult.success(jwsClaims);
        } catch (SignatureException e) {
            String msg = "Invalid JWT signature.";
            log.warn("Token validation failed: {}. Token: [{}]", msg, truncateTokenForLogging(token), e);
            return JwtValidationResult.failure(JwtErrorType.INVALID_SIGNATURE, msg + " " + e.getMessage(),e);
        } catch (MalformedJwtException e) {
            String msg = "Invalid JWT token format.";
            log.warn("Token validation failed: {}. Token: [{}]", msg, truncateTokenForLogging(token), e);
            return JwtValidationResult.failure(JwtErrorType.MALFORMED, msg + " " + e.getMessage(),e);
        } catch (ExpiredJwtException e) {
            String msg = "JWT token is expired.";
            log.debug("Token validation failed: {}. Token: [{}]", msg, truncateTokenForLogging(token), e);
            return JwtValidationResult.failure(JwtErrorType.EXPIRED, msg + " " + e.getMessage(),e);
        } catch (UnsupportedJwtException e) {
            String msg = "JWT token is unsupported.";
            log.warn("Token validation failed: {}. Token: [{}]", msg, truncateTokenForLogging(token), e);
            return JwtValidationResult.failure(JwtErrorType.UNSUPPORTED, msg + " " + e.getMessage(),e);
        } catch (IllegalArgumentException e) {
            // Это может быть от jjwt, если claims пустые или другая проблема с аргументом внутри jjwt
            String msg = "JWT claims string is empty or token is otherwise invalid (IllegalArgumentException from jjwt).";
            log.warn("Token validation failed: {}. Token: [{}]", msg, truncateTokenForLogging(token), e);
            return JwtValidationResult.failure(JwtErrorType.EMPTY_OR_ILLEGAL_ARGUMENT,
                    msg + " " + e.getMessage(),e);
        } catch (JwtException e) { // Общий JwtException для других ошибок парсинга/валидации
            String msg = "General JWT validation error.";
            log.warn("Token validation failed: {}. Token: [{}]", msg, truncateTokenForLogging(token), e);
            return JwtValidationResult.failure(JwtErrorType.OTHER_JWT_EXCEPTION, msg + " " + e.getMessage(),e);
        }
    }

    /**
     * Проверяет, является ли предоставленный JWT валидным.
     *
     * @param token Строка JWT для валидации.
     * @return {@code true} если токен валиден, иначе {@code false}.
     */
    public boolean isValid(String token) {
        return validateAndParseToken(token).isSuccess();
    }

    /**
     * Извлекает тело {@link Claims} из JWT, если токен валиден.
     *
     * @param token Строка JWT.
     * @return {@link Optional<Claims>}, содержащий Claims если токен валиден, иначе {@link Optional#empty()}.
     */
    public Optional<Claims> extractValidClaims(String token) {
        return validateAndParseToken(token).getJwsClaimsOptional().map(Jws::getPayload);
    }

    /**
     * Вспомогательный метод для сокращения токена в логах.
     *
     * @param token JWT строка.
     * @return Сокращенная версия токена для логирования.
     */
    public static String truncateTokenForLogging(String token) {
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