package com.example.tasktracker.backend.security.apikey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;


/**
 * Юнит-тесты для {@link ApiKeyValidator}.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyValidatorTest {

    @Mock
    private ApiKeyProperties mockApiKeyProperties;

    @InjectMocks
    private ApiKeyValidator apiKeyValidator;

    private final String validKey1 = "my-secret-key-1";
    private final String validKey2 = "my-secret-key-2";

    @BeforeEach
    void setUp() {
        // Настраиваем мок по умолчанию для большинства тестов
        lenient().when(mockApiKeyProperties.getValidKeys()).thenReturn(Set.of(validKey1, validKey2));
    }

    @Test
    @DisplayName("isValid: предоставленный ключ совпадает с одним из валидных -> должен вернуть true")
    void isValid_whenProvidedKeyMatches_shouldReturnTrue() {
        assertThat(apiKeyValidator.isValid(validKey1)).isTrue();
        assertThat(apiKeyValidator.isValid(validKey2)).isTrue();
    }

    @Test
    @DisplayName("isValid: предоставленный ключ не совпадает ни с одним из валидных -> должен вернуть false")
    void isValid_whenProvidedKeyDoesNotMatch_shouldReturnFalse() {
        assertThat(apiKeyValidator.isValid("invalid-key")).isFalse();
    }

    @Test
    @DisplayName("isValid: предоставленный ключ null -> должен выбросить NullPointerException")
    void isValid_whenProvidedKeyIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> apiKeyValidator.isValid(null))
                .withMessageContaining("providedKey");
    }

    @Test
    @DisplayName("isValid: набор валидных ключей пуст -> должен всегда возвращать false")
    void isValid_whenValidKeysSetIsEmpty_shouldAlwaysReturnFalse() {
        when(mockApiKeyProperties.getValidKeys()).thenReturn(Collections.emptySet());
        assertThat(apiKeyValidator.isValid(validKey1)).isFalse();
        assertThat(apiKeyValidator.isValid("any-key")).isFalse();
    }

    @Test
    @DisplayName("isValid: набор валидных ключей null -> должен вернуть false и залогировать ошибку")
    void isValid_whenValidKeysSetIsNull_shouldReturnFalseAndLogCriticalError() {
        // Этот тест важен для проверки защиты от неправильной конфигурации,
        // хотя @NotEmpty в ApiKeyProperties должен предотвращать это на старте.
        when(mockApiKeyProperties.getValidKeys()).thenReturn(null);
        assertThat(apiKeyValidator.isValid(validKey1)).isFalse();
    }
}