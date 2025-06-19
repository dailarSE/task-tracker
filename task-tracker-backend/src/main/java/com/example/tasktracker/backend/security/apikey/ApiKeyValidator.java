package com.example.tasktracker.backend.security.apikey;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Сервис, ответственный за валидацию предоставленного API-ключа.
 * <p>
 * Сравнивает полученный ключ с набором валидных ключей, определенных
 * в {@link ApiKeyProperties}, используя метод, устойчивый к атакам по времени.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyValidator {

    private final ApiKeyProperties apiKeyProperties;

    /**
     * Проверяет, является ли предоставленный API-ключ валидным.
     * <p>
     * Сравнение производится с каждым ключом из сконфигурированного набора
     * с использованием {@link MessageDigest#isEqual(byte[], byte[])}, чтобы
     * предотвратить "timing attacks".
     * </p>
     *
     * @param providedKey API-ключ, полученный из запроса. Не должен быть null.
     * @throws NullPointerException если {@code providedKey} равен null.
     * @return {@code true}, если ключ найден в наборе валидных ключей, иначе {@code false}.
     */
    public boolean isValid(@NonNull String providedKey) {
        if (apiKeyProperties.getValidKeys() == null) {
            log.error("CRITICAL: Set of valid API keys is null. Check configuration 'app.security.api-key.valid-keys'.");
            return false;
        }

        byte[] providedKeyBytes = providedKey.getBytes(StandardCharsets.UTF_8);

        for (String validKey : apiKeyProperties.getValidKeys()) {
            byte[] validKeyBytes = validKey.getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(providedKeyBytes, validKeyBytes)) {
                log.trace("Provided API key matched a configured valid key.");
                return true;
            }
        }

        log.warn("Provided API key did not match any of the configured valid keys.");
        return false;
    }
}