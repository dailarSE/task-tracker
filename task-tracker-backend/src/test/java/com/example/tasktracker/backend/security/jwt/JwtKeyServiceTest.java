package com.example.tasktracker.backend.security.jwt;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты для {@link JwtKeyService}.
 * Проверяют логику декодирования Base64 ключа, валидацию длины (через JJWT)
 * и обработку ошибок при инициализации ключа.
 */
@ExtendWith(MockitoExtension.class)
class JwtKeyServiceTest {

    @Mock
    private JwtProperties mockJwtProperties;

    // Валидный Base64 ключ достаточной длины (32 байта)
    // Оригинал: "myVerySecretKeyForTaskTrackerApp" (32 символа)
    private static final String VALID_KEY_BASE64_32_BYTES = "bXlWZXJ5U2VjcmV0S2V5Rm9yVGFza1RyYWNrZXJBcHA=";

    // Валидный Base64 ключ, но слишком короткий (например, "short")
    // Оригинал: "short" (5 символов)
    private static final String SHORT_KEY_BASE64 = "c2hvcnQ=";


    @Test
    @DisplayName("Конструктор: JwtProperties null -> должен выбросить NullPointerException")
    void constructor_whenJwtPropertiesIsNull_shouldThrowNullPointerException() {
        // Act & Assert
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtKeyService(null))
                .withMessageContaining("jwtProperties is marked non-null but is null");
    }

    @Test
    @DisplayName("Конструктор: Ключ валидный Base64 и достаточной длины -> успешная инициализация")
    void constructor_whenKeyIsValidBase64AndSufficientLength_shouldInitializeSuccessfully() {
        // Arrange
        when(mockJwtProperties.getSecretKey()).thenReturn(VALID_KEY_BASE64_32_BYTES);

        // Act & Assert
        JwtKeyService jwtKeyService = null;
        try {
            jwtKeyService = new JwtKeyService(mockJwtProperties);
        } catch (Exception e) {
            fail("Initialization should succeed with a valid key, but threw: " + e.getMessage(), e);
        }

        assertThat(jwtKeyService).isNotNull();
        SecretKey secretKey = jwtKeyService.getSecretKey();
        assertThat(secretKey).isNotNull();
    }

    @DisplayName("Конструктор: Ключ невалидный Base64 -> должен выбросить DecodingException")
    @ParameterizedTest(name = "Для невалидного Base64 ключа: \"{0}\"")
    @ValueSource(strings = {
            "this-is-not-base64!",   // Содержит не-Base64 символы
            "shortButValidLooking=", // Невалидный padding или длина для Base64
            "Invalid Base64 Chars $$##" // Содержит не-Base64 символы
    })
    void constructor_whenSecretKeyIsNotValidBase64_shouldThrowIllegalStateException(String invalidBase64Key) {
        // Arrange
        when(mockJwtProperties.getSecretKey()).thenReturn(invalidBase64Key);

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new JwtKeyService(mockJwtProperties));
    }

    @Test
    @DisplayName("Конструктор: Декодированный ключ слишком короткий ->" +
            " должен выбросить IllegalStateException (через WeakKeyException)")
    void constructor_whenDecodedKeyIsTooShort_shouldThrowIllegalStateException() {
        // Arrange
        when(mockJwtProperties.getSecretKey()).thenReturn(SHORT_KEY_BASE64); // "short" -> 5 байт

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new JwtKeyService(mockJwtProperties))
                .withMessage("The decoded JWT secret key is too short for HS256 algorithm.")
                .withCauseInstanceOf(WeakKeyException.class); // От Keys.hmacShaKeyFor
    }

    @Test
    @DisplayName("getSecretKey: После успешной инициализации -> должен возвращать тот же экземпляр ключа")
    void getSecretKey_afterSuccessfulInitialization_shouldReturnSameKeyInstance() {
        // Arrange
        when(mockJwtProperties.getSecretKey()).thenReturn(VALID_KEY_BASE64_32_BYTES);
        JwtKeyService jwtKeyService = new JwtKeyService(mockJwtProperties);

        // Act
        SecretKey key1 = jwtKeyService.getSecretKey();
        SecretKey key2 = jwtKeyService.getSecretKey();

        // Assert
        assertThat(key1).isNotNull();
        assertThat(key2).isNotNull();
        assertThat(key1).isSameAs(key2); // Проверка на тот же самый объект
    }
}