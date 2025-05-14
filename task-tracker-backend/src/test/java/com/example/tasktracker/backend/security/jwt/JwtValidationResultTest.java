package com.example.tasktracker.backend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

/**
 * Юнит-тесты для {@link JwtValidationResult}.
 */
@ExtendWith(MockitoExtension.class)
class JwtValidationResultTest {

    @Mock
    private Jws<Claims> mockJwsClaims; // Мок Jws<Claims> для передачи в фабричный метод

    @Mock
    private Throwable mockCause; // Мок для Throwable

    @Test
    @DisplayName("success(): должен создать успешный результат с JwsClaims")
    void success_shouldCreateSuccessfulResultWithJwsClaims() {
        // Act
        JwtValidationResult result = JwtValidationResult.success(mockJwsClaims);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJwsClaims()).isSameAs(mockJwsClaims);
        assertThat(result.getJwsClaimsOptional()).isPresent().containsSame(mockJwsClaims);
        assertThat(result.getErrorType()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getCause()).isNull(); // Причина должна быть null для успешного результата
    }

    @Test
    @DisplayName("success(): передача null JwsClaims -> должен выбросить NullPointerException")
    void success_whenJwsClaimsIsNull_shouldThrowNullPointerException() {
        // Act & Assert
        assertThatNullPointerException()
                .isThrownBy(() -> JwtValidationResult.success(null))
                .withMessageContaining("jwsClaims is marked non-null but is null");
    }

    @Test
    @DisplayName("failure(errorType, message, cause): должен создать неуспешный результат со всеми полями ошибки")
    void failure_withAllErrorFields_shouldCreateFailedResult() {
        // Arrange
        JwtErrorType expectedErrorType = JwtErrorType.EXPIRED;
        String expectedErrorMessage = "Token has expired";

        // Act
        JwtValidationResult result = JwtValidationResult.failure(expectedErrorType, expectedErrorMessage, mockCause);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getJwsClaimsOptional()).isEmpty();
        assertThat(result.getErrorType()).isEqualTo(expectedErrorType);
        assertThat(result.getErrorMessage()).isEqualTo(expectedErrorMessage);
        assertThat(result.getCause()).isSameAs(mockCause);
    }

    @Test
    @DisplayName("failure(errorType, message): должен создать неуспешный результат с null cause")
    void failure_withMessageAndErrorType_shouldCreateFailedResultWithNullCause() {
        // Arrange
        JwtErrorType expectedErrorType = JwtErrorType.INVALID_SIGNATURE;
        String expectedErrorMessage = "Signature is invalid";

        // Act
        JwtValidationResult result = JwtValidationResult.failure(expectedErrorType, expectedErrorMessage); // Используем перегруженный метод

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getJwsClaimsOptional()).isEmpty();
        assertThat(result.getErrorType()).isEqualTo(expectedErrorType);
        assertThat(result.getErrorMessage()).isEqualTo(expectedErrorMessage);
        assertThat(result.getCause()).isNull(); // Причина должна быть null
    }


    @Test
    @DisplayName("failure(): сообщение об ошибке null (с причиной) -> должен создать результат с null сообщением")
    void failure_whenErrorMessageIsNullAndCauseIsPresent_shouldCreateResultWithNullMessage() {
        // Arrange
        JwtErrorType expectedErrorType = JwtErrorType.INVALID_SIGNATURE;

        // Act
        JwtValidationResult result = JwtValidationResult.failure(expectedErrorType, null, mockCause);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getJwsClaimsOptional()).isEmpty();
        assertThat(result.getErrorType()).isEqualTo(expectedErrorType);
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getCause()).isSameAs(mockCause);
    }

    @Test
    @DisplayName("failure(): ErrorType null -> должен выбросить NullPointerException (для любого failure метода)")
    void failure_whenErrorTypeIsNull_shouldThrowNullPointerException() {
        // Тестируем оба фабричных метода failure
        assertThatNullPointerException()
                .isThrownBy(() -> JwtValidationResult.failure(null, "Some error"))
                .withMessageContaining("errorType is marked non-null but is null");

        assertThatNullPointerException()
                .isThrownBy(() -> JwtValidationResult.failure(null, "Some error", mockCause))
                .withMessageContaining("errorType is marked non-null but is null");
    }

    // Тесты для isSuccess() и getJwsClaimsOptional() остаются такими же,
    // так как их логика не зависит от поля cause напрямую, а только от errorType и jwsClaims.
    // Я их оставлю для полноты, если вы не против, но они уже были в вашем варианте.

    @Test
    @DisplayName("isSuccess(): для успешного результата -> должен вернуть true")
    void isSuccess_forSuccessfulResult_shouldReturnTrue() {
        JwtValidationResult result = JwtValidationResult.success(mockJwsClaims);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("isSuccess(): для неуспешного результата -> должен вернуть false")
    void isSuccess_forFailedResult_shouldReturnFalse() {
        JwtValidationResult result = JwtValidationResult.failure(JwtErrorType.MALFORMED, "Malformed token");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("getJwsClaimsOptional(): для успешного результата -> должен вернуть Optional с JwsClaims")
    void getJwsClaimsOptional_forSuccessfulResult_shouldReturnOptionalWithJwsClaims() {
        JwtValidationResult result = JwtValidationResult.success(mockJwsClaims);
        assertThat(result.getJwsClaimsOptional()).isPresent().containsSame(mockJwsClaims);
    }

    @Test
    @DisplayName("getJwsClaimsOptional(): для неуспешного результата -> должен вернуть пустой Optional")
    void getJwsClaimsOptional_forFailedResult_shouldReturnEmptyOptional() {
        JwtValidationResult result = JwtValidationResult.failure(JwtErrorType.UNSUPPORTED, "Unsupported token");
        assertThat(result.getJwsClaimsOptional()).isEmpty();
    }
}