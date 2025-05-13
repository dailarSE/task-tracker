package com.example.tasktracker.backend.security.jwt;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

/**
 * Сервис для управления секретным ключом JWT.
 * Отвечает за декодирование Base64-ключа из конфигурации
 * и предоставление экземпляра {@link SecretKey}.
 * Использует встроенную в JJWT валидацию ключа (включая проверку на минимальную длину).
 * Применяет fail-fast при некорректной конфигурации ключа.
 */
@Service
@Slf4j
public class JwtKeyService {

    private final SecretKey secretKeyInstance;

    /**
     * Конструктор {@link JwtKeyService}.
     * Немедленно декодирует и валидирует секретный ключ JWT из {@link JwtProperties}.
     * Если ключ невалиден (не Base64, слишком короткий для используемого алгоритма),
     * выбрасывается соответствующее исключение (например, {@link IllegalArgumentException} от декодера
     * или {@link WeakKeyException} от {@code Keys.hmacShaKeyFor}), что приводит к падению приложения
     * при старте (fail-fast).
     *
     * @param jwtProperties Конфигурационные свойства JWT, содержащие Base64-кодированный секретный ключ.
     * @throws IllegalStateException если секретный ключ не может быть инициализирован из-за ошибок формата или требований к ключу.
     */
    public JwtKeyService(@NonNull JwtProperties jwtProperties) {
        String base64Secret = jwtProperties.getSecretKey();

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(base64Secret);
        } catch (DecodingException e) {
            log.error("Failed to decode JWT secret key from Base64. Please verify its format. " +
                            "Provided key (length: {} char(s)) was not valid Base64.",
                    base64Secret.length(), e);
            throw new IllegalStateException("JWT secret key is not a valid Base64 encoded string. Verify format.", e);
        }

        try {
            this.secretKeyInstance = Keys.hmacShaKeyFor(keyBytes);
            log.info("JWT SecretKey initialized successfully from configured Base64 key." +
                    " Decoded key length: {} bytes.", keyBytes.length);
        } catch (WeakKeyException e) {
            log.error("The decoded JWT secret key is too short for HS256 algorithm." +
                            " Decoded key length: {} bytes. Minimum required: {} bytes.", keyBytes.length, (256 / 8), e);
            throw new IllegalStateException("The decoded JWT secret key is too short for HS256 algorithm.", e);
        }
    }

    /**
     * Предоставляет инициализированный и валидированный экземпляр {@link SecretKey}.
     *
     * @return Экземпляр {@link SecretKey} для использования в операциях подписи/валидации JWT.
     */
    public SecretKey getSecretKey() {
        return secretKeyInstance;
    }
}