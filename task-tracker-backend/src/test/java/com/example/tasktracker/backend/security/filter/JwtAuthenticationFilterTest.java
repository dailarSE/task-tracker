package com.example.tasktracker.backend.security.filter;

import com.example.tasktracker.backend.common.MdcKeys;
import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.jwt.JwtAuthenticationConverter;
import com.example.tasktracker.backend.security.jwt.JwtErrorType;
import com.example.tasktracker.backend.security.jwt.JwtValidationResult;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link JwtAuthenticationFilter}.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock private JwtValidator mockJwtValidator;
    @Mock private JwtAuthenticationConverter mockJwtAuthenticationConverter;
    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private FilterChain mockFilterChain;
    @Mock private Jws<Claims> mockJwsClaims;
    @Mock private Claims mockClaimsBody;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(mockJwtValidator, mockJwtAuthenticationConverter);
        when(mockJwsClaims.getPayload()).thenReturn(mockClaimsBody);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilterInternal: Нет JWT в запросе -> Authentication не устанавливается, цепочка продолжается")
    void doFilterInternal_whenNoJwtTokenInRequest_shouldNotSetAuthenticationAndProceedChain() throws ServletException, IOException {
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        verifyNoInteractions(mockJwtValidator, mockJwtAuthenticationConverter);
    }

    @Test
    @DisplayName("doFilterInternal: Валидный JWT -> Authentication устанавливается, цепочка продолжается")
    void doFilterInternal_whenJwtTokenIsValid_shouldSetAuthenticationAndProceedChain() throws ServletException, IOException {
        // Arrange
        String token = "valid.jwt.token";
        JwtValidationResult validResult = JwtValidationResult.success(mockJwsClaims);

        AppUserDetails mockUserDetails = mock(AppUserDetails.class);
        when(mockUserDetails.getId()).thenReturn(1L);
        when(mockUserDetails.getUsername()).thenReturn("test@example.com");

        Authentication mockAuthWithUserDetails = mock(Authentication.class);
        when(mockAuthWithUserDetails.getPrincipal()).thenReturn(mockUserDetails);

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(validResult);
        when(mockJwtAuthenticationConverter.convert(mockClaimsBody, token)).thenReturn(mockAuthWithUserDetails);

        // Act
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(mockAuthWithUserDetails);
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
    }

    @Test
    @DisplayName("doFilterInternal: Невалидный JWT (ошибка валидатора) -> должен выбросить BadJwtException")
    void doFilterInternal_whenJwtIsInvalidByValidator_shouldThrowBadJwtException() {
        // Arrange
        String token = "invalid.jwt.token";
        JwtValidationResult invalidResult = JwtValidationResult.failure(JwtErrorType.INVALID_SIGNATURE, "Bad signature");

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(invalidResult);

        // Act & Assert
        assertThatExceptionOfType(BadJwtException.class)
                .isThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain))
                .satisfies(ex -> {
                    assertThat(ex.getErrorType()).isEqualTo(JwtErrorType.INVALID_SIGNATURE);
                    assertThat(ex.getMessage()).isEqualTo("Bad signature");
                });

        verifyNoInteractions(mockFilterChain); // Цепочка не должна продолжаться
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("doFilterInternal: Ошибка конвертации Claims -> должен выбросить BadJwtException")
    void doFilterInternal_whenClaimsConversionFails_shouldThrowBadJwtException() {
        // Arrange
        String token = "valid.token.but.bad.claims";
        JwtValidationResult validResult = JwtValidationResult.success(mockJwsClaims);
        IllegalArgumentException conversionException = new IllegalArgumentException("Bad claims content");

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(validResult);
        when(mockJwtAuthenticationConverter.convert(mockClaimsBody, token)).thenThrow(conversionException);

        // Act & Assert
        assertThatExceptionOfType(BadJwtException.class)
                .isThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain))
                .satisfies(ex -> {
                    assertThat(ex.getErrorType()).isEqualTo(JwtErrorType.OTHER_JWT_EXCEPTION);
                    assertThat(ex.getCause()).isSameAs(conversionException);
                });

        verifyNoInteractions(mockFilterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest(name = "Для заголовка: \"{0}\"")
    @ValueSource(strings = {"NotBearer valid.jwt.token", "Bearer", "Token only"})
    @DisplayName("doFilterInternal: Заголовок Authorization без префикса 'Bearer ' -> цепочка продолжается")
    void doFilterInternal_whenAuthorizationHeaderIsMissingBearerPrefix_shouldProceedChain(String invalidAuthHeader) throws ServletException, IOException {
        // Arrange
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(invalidAuthHeader);

        // Act
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        verifyNoInteractions(mockJwtValidator);
    }

    @Test
    @DisplayName("doFilterInternal: Валидный JWT -> MDC должен быть установлен и очищен")
    void doFilterInternal_whenJwtTokenIsValid_shouldSetAndClearMdc() throws ServletException, IOException {
        // Arrange
        String token = "valid.jwt.token";
        Long expectedUserId = 123L;
        String expectedUserIdStr = String.valueOf(expectedUserId);

        AppUserDetails mockUserDetails = mock(AppUserDetails.class);
        when(mockUserDetails.getId()).thenReturn(expectedUserId);

        Authentication mockAuthWithUserDetails = mock(Authentication.class);
        when(mockAuthWithUserDetails.getPrincipal()).thenReturn(mockUserDetails);

        when(mockJwtAuthenticationConverter.convert(any(Claims.class), eq(token))).thenReturn(mockAuthWithUserDetails);

        JwtValidationResult validResult = JwtValidationResult.success(mockJwsClaims);
        when(mockJwsClaims.getPayload()).thenReturn(mock(Claims.class));
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(validResult);
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);

        doAnswer(invocation -> {
            assertThat(MDC.get(MdcKeys.USER_ID)).isEqualTo(expectedUserIdStr);
            return null;
        }).when(mockFilterChain).doFilter(mockRequest, mockResponse);

        // Act
        MDC.clear();
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
    }

    @Test
    @DisplayName("doFilterInternal: Непредвиденный RuntimeException из зависимостей -> должен быть проброшен дальше")
    void doFilterInternal_whenDependencyThrowsRuntimeException_shouldPropagateException() {
        // Arrange
        String token = "valid.jwt.token";
        JwtValidationResult validResult = JwtValidationResult.success(mockJwsClaims);
        IllegalStateException unexpectedException = new IllegalStateException("Unexpected internal error!");

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(validResult);
        // Симулируем, что конвертер выбрасывает непредвиденную ошибку
        when(mockJwtAuthenticationConverter.convert(mockClaimsBody, token)).thenThrow(unexpectedException);

        // Act & Assert
        // Проверяем, что именно это исключение "вылетает" из фильтра
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain))
                .isSameAs(unexpectedException);

        // Убеждаемся, что цепочка не продолжилась и контекст не был установлен
        verifyNoInteractions(mockFilterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("prepareMdcUserIdentifier: principal является AppUserDetails с валидным ID -> должен вернуть ID как строку")
    void prepareMdcUserIdentifier_whenPrincipalIsAppUserDetailsWithValidId_shouldReturnIdAsString() {
        // Arrange
        AppUserDetails mockUserDetails = mock(AppUserDetails.class);
        when(mockUserDetails.getId()).thenReturn(123L);
        when(mockUserDetails.getUsername()).thenReturn("user@example.com");

        // Act
        String result = jwtAuthenticationFilter.prepareMdcUserIdentifier(mockUserDetails);

        // Assert
        assertThat(result).isEqualTo("123");
    }

    @Test
    @DisplayName("prepareMdcUserIdentifier: principal является AppUserDetails, но ID null -> должен вернуть username и залогировать WARN")
    void prepareMdcUserIdentifier_whenPrincipalIsAppUserDetailsButIdIsNull_shouldReturnUsernameAndLogWarn() {
        // Arrange
        AppUserDetails mockUserDetails = mock(AppUserDetails.class);
        when(mockUserDetails.getId()).thenReturn(null); // Симулируем null ID
        when(mockUserDetails.getUsername()).thenReturn("user@example.com");

        // Act
        String result = jwtAuthenticationFilter.prepareMdcUserIdentifier(mockUserDetails);

        // Assert
        assertThat(result).isEqualTo("user@example.com");
        // Проверка логирования WARN здесь не делается напрямую через AssertJ, но мы знаем, что эта ветка выполняется.
        // Для реальной проверки логирования можно использовать библиотеки типа LogCaptor.
    }

    @Test
    @DisplayName("prepareMdcUserIdentifier: principal не AppUserDetails -> должен выбросить IllegalStateException")
    void prepareMdcUserIdentifier_whenPrincipalIsNotAppUserDetails_shouldThrowIllegalStateException() {
        // Arrange
        Object nonAppUserDetailsPrincipal = new Object(); // Любой объект, не являющийся AppUserDetails

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> jwtAuthenticationFilter.prepareMdcUserIdentifier(nonAppUserDetailsPrincipal))
                .withMessageStartingWith("Principal is not AppUserDetails. Principal:");
    }

    @Test
    @DisplayName("prepareMdcUserIdentifier: principal null -> должен выбросить NullPointerException (Lombok @NonNull)")
    void prepareMdcUserIdentifier_whenPrincipalIsNull_shouldThrowNullPointerException() {
        // Act & Assert
        assertThatNullPointerException()
                .isThrownBy(() -> jwtAuthenticationFilter.prepareMdcUserIdentifier(null))
                .withMessageContaining("principal is marked non-null but is null");
    }

    @Test
    @DisplayName("Конструктор: JwtValidator null -> должен выбросить NullPointerException")
    void constructor_whenJwtValidatorIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtAuthenticationFilter(null, mockJwtAuthenticationConverter))
                .withMessageContaining("jwtValidator is marked non-null but is null");
    }

    @Test
    @DisplayName("Конструктор: JwtAuthenticationConverter null -> должен выбросить NullPointerException")
    void constructor_whenJwtAuthenticationConverterIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtAuthenticationFilter(mockJwtValidator, null))
                .withMessageContaining("jwtAuthenticationConverter is marked non-null but is null");
    }

    @Test
    @DisplayName("doFilterInternal: HttpServletRequest null -> должен выбросить NullPointerException")
    void doFilterInternal_whenRequestIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(null, mockResponse, mockFilterChain))
                .withMessageContaining("request is marked non-null but is null");
    }
}