package com.example.tasktracker.backend.security.apikey;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * Сервис, ответственный за валидацию предоставленного API-ключа и идентификацию сервиса.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyValidator {

    private final ApiKeyProperties apiKeyProperties;

    /**
     * Проверяет, является ли предоставленный API-ключ валидным, и возвращает
     * идентификатор связанного с ним сервиса.
     *
     * @param providedKey API-ключ, полученный из запроса. Не должен быть null.
     * @return {@link Optional} с идентификатором сервиса, если ключ валиден.
     *         В противном случае, возвращает {@link Optional#empty()}.
     */
    public Optional<String> getServiceIdIfValid(@NonNull String providedKey) {
        final Map<String, String> keysToServices = apiKeyProperties.getKeysToServices();
        if (keysToServices == null || keysToServices.isEmpty()) {
            log.error("CRITICAL: Map of valid API keys is null or empty. Check configuration.");
            return Optional.empty();
        }

        byte[] providedKeyBytes = providedKey.getBytes(StandardCharsets.UTF_8);

        for (Map.Entry<String, String> entry : keysToServices.entrySet()) {
            byte[] validKeyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(providedKeyBytes, validKeyBytes)) {
                String serviceId = entry.getValue();
                log.trace("Provided API key matched. Identified service: '{}'", serviceId);
                return Optional.of(serviceId);
            }
        }

        log.warn("Provided API key did not match any of the configured valid keys.");
        return Optional.empty();
    }
}