package com.example.tasktracker.backend.security.exception;

import com.example.tasktracker.backend.security.jwt.JwtErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты для {@link BadJwtException}.
 */
class BadJwtExceptionTest {

    private static final String TEST_MESSAGE = "Test JWT error message";
    private static final JwtErrorType TEST_ERROR_TYPE = JwtErrorType.EXPIRED;

    @Test
    @DisplayName("Конструктор (msg, errorType): должен корректно установить поля")
    void constructor_withMessageAndErrorType_shouldSetFieldsCorrectly() {
        // Act
        BadJwtException exception = new BadJwtException(TEST_MESSAGE, TEST_ERROR_TYPE);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(TEST_MESSAGE);
        assertThat(exception.getErrorType()).isEqualTo(TEST_ERROR_TYPE);
        assertThat(exception.getCause()).isNull(); // Причина не передавалась
    }

    @Test
    @DisplayName("Конструктор (msg, errorType, cause): должен корректно установить все поля")
    void constructor_withMessageErrorTypeAndCause_shouldSetAllFieldsCorrectly() {
        // Arrange
        Throwable cause = new RuntimeException("Root cause");

        // Act
        BadJwtException exception = new BadJwtException(TEST_MESSAGE, TEST_ERROR_TYPE, cause);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(TEST_MESSAGE);
        assertThat(exception.getErrorType()).isEqualTo(TEST_ERROR_TYPE);
        assertThat(exception.getCause()).isSameAs(cause); // Проверяем, что причина та же самая
    }

    @Test
    @DisplayName("getErrorType: должен возвращать установленный errorType")
    void getErrorType_shouldReturnCorrectErrorType() {
        // Arrange
        BadJwtException exception = new BadJwtException(TEST_MESSAGE, JwtErrorType.INVALID_SIGNATURE);

        // Act & Assert
        assertThat(exception.getErrorType()).isEqualTo(JwtErrorType.INVALID_SIGNATURE);
    }
}