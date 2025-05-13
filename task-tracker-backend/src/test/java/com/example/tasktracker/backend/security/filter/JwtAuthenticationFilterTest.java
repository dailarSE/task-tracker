package com.example.tasktracker.backend.security.filter;

import com.example.tasktracker.backend.security.jwt.JwtAuthenticationConverter;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link JwtAuthenticationFilter}.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtValidator mockJwtValidator;
    @Mock
    private JwtAuthenticationConverter mockJwtAuthenticationConverter;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;
    @Mock
    private FilterChain mockFilterChain;
    @Mock
    private Authentication mockAuthentication;
    @Mock
    private Claims mockClaims;


    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilterInternal: Нет JWT в запросе -> Authentication не устанавливается, цепочка продолжается")
    void doFilterInternal_whenNoJwtTokenInRequest_shouldNotSetAuthenticationAndProceedChain() throws ServletException, IOException {
        // Arrange
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        verifyNoInteractions(mockJwtValidator, mockJwtAuthenticationConverter); // Убеждаемся, что JWT-компоненты не вызывались
    }

    @Test
    @DisplayName("doFilterInternal: Валидный JWT -> Authentication устанавливается, цепочка продолжается")
    void doFilterInternal_whenJwtTokenIsValid_shouldSetAuthenticationAndProceedChain() throws ServletException, IOException {
        // Arrange
        String token = "valid.jwt.token";
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.extractValidClaims(token)).thenReturn(Optional.of(mockClaims));
        when(mockJwtAuthenticationConverter.convert(mockClaims, token)).thenReturn(mockAuthentication);

        // Act
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(mockAuthentication);
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
    }

    @Test
    @DisplayName("doFilterInternal: Невалидный JWT -> Authentication не устанавливается, цепочка продолжается")
    void doFilterInternal_whenJwtTokenIsInvalid_shouldNotSetAuthenticationAndProceedChain() throws ServletException, IOException {
        // Arrange
        String token = "invalid.jwt.token";
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.extractValidClaims(token)).thenReturn(Optional.empty()); // JwtValidator говорит, что токен невалиден

        // Act
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(mockJwtAuthenticationConverter, never()).convert(any(), anyString()); // Конвертер не должен вызываться
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
    }

    @Test
    @DisplayName("doFilterInternal: Ошибка конвертации Claims -> Контекст очищается, цепочка продолжается")
    void doFilterInternal_whenClaimsConversionFails_shouldClearContextAndProceedChain() throws ServletException, IOException {
        // Arrange
        String token = "valid.token.but.bad.claims";
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.extractValidClaims(token)).thenReturn(Optional.of(mockClaims));
        // JwtAuthenticationConverter выбрасывает исключение
        when(mockJwtAuthenticationConverter.convert(mockClaims, token)).thenThrow(new IllegalArgumentException("Bad claims"));

        // Act
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(); // Контекст должен быть очищен
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
    }

    @Test
    @DisplayName("doFilterInternal: Authentication уже есть в контексте -> JWT не обрабатывается, цепочка продолжается")
    void doFilterInternal_whenAuthenticationAlreadyPresentInContext_shouldNotProcessJwtAndProceedChain() throws ServletException, IOException {
        // Arrange
        // Устанавливаем существующую аутентификацию
        when(mockAuthentication.isAuthenticated()).thenReturn(true); // Важно, чтобы она была помечена как аутентифицированная
        SecurityContextHolder.getContext().setAuthentication(mockAuthentication);

        // Act
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        // Убеждаемся, что аутентификация в контексте осталась прежней
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(mockAuthentication);
        // Убеждаемся, что JWT-компоненты не вызывались
        verifyNoInteractions(mockJwtValidator);
        verifyNoInteractions(mockJwtAuthenticationConverter);
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
    }

    @DisplayName("doFilterInternal: Заголовок Authorization без префикса 'Bearer ' -> Authentication не устанавливается")
    @ParameterizedTest(name = "Для заголовка: \"{0}\"")
    @ValueSource(strings = {"NotBearer valid.jwt.token", "Bearer", "Token only"})
    void doFilterInternal_whenAuthorizationHeaderIsMissingBearerPrefix_shouldNotSetAuthenticationAndProceedChain(String invalidAuthHeader) throws ServletException, IOException {
        // Arrange
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(invalidAuthHeader);

        // Act
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        verifyNoInteractions(mockJwtValidator, mockJwtAuthenticationConverter);
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

    @Test
    @DisplayName("doFilterInternal: HttpServletResponse null -> должен выбросить NullPointerException")
    void doFilterInternal_whenResponseIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(mockRequest, null, mockFilterChain))
                .withMessageContaining("response is marked non-null but is null");
    }

    @Test
    @DisplayName("doFilterInternal: FilterChain null -> должен выбросить NullPointerException")
    void doFilterInternal_whenFilterChainIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, null))
                .withMessageContaining("filterChain is marked non-null but is null");
    }
}