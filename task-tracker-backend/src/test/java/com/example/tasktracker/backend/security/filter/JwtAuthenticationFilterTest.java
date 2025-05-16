package com.example.tasktracker.backend.security.filter;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
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

    @Mock
    private JwtValidator mockJwtValidator;
    @Mock
    private JwtAuthenticationConverter mockJwtAuthenticationConverter;
    @Mock
    private AuthenticationEntryPoint mockAuthenticationEntryPoint;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;
    @Mock
    private FilterChain mockFilterChain;
    @Mock
    private Authentication mockAuthentication;
    @Mock
    private Jws<Claims> mockJwsClaims;
    @Mock
    private Claims mockClaimsBody;


    // Тестируемый объект
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        // Создаем экземпляр фильтра вручную с моками
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
                mockJwtValidator,
                mockJwtAuthenticationConverter,
                mockAuthenticationEntryPoint
        );
        // Настройка для Jws<Claims> и его payload
        when(mockJwsClaims.getPayload()).thenReturn(mockClaimsBody);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Конструктор: JwtValidator null -> должен выбросить NullPointerException")
    void constructor_whenJwtValidatorIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtAuthenticationFilter(null, mockJwtAuthenticationConverter, mockAuthenticationEntryPoint))
                .withMessageContaining("jwtValidator is marked non-null but is null");
    }

    @Test
    @DisplayName("Конструктор: JwtAuthenticationConverter null -> должен выбросить NullPointerException")
    void constructor_whenJwtAuthenticationConverterIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtAuthenticationFilter(mockJwtValidator, null, mockAuthenticationEntryPoint))
                .withMessageContaining("jwtAuthenticationConverter is marked non-null but is null");
    }

    @Test
    @DisplayName("Конструктор: AuthenticationEntryPoint null -> должен выбросить NullPointerException")
    void constructor_whenAuthenticationEntryPointIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtAuthenticationFilter(mockJwtValidator, mockJwtAuthenticationConverter, null))
                .withMessageContaining("authenticationEntryPoint is marked non-null but is null");
    }

    // Тесты на @NonNull параметров doFilterInternal (как мы обсуждали)
    @Test
    @DisplayName("doFilterInternal: HttpServletRequest null -> должен выбросить NullPointerException")
    void doFilterInternal_whenRequestIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(null, mockResponse, mockFilterChain))
                .withMessageContaining("request is marked non-null but is null");
    }
    // ... (аналогичные тесты для response и filterChain) ...

    @Test
    @DisplayName("doFilterInternal: Authentication уже есть в контексте -> JWT не обрабатывается, цепочка продолжается")
    void doFilterInternal_whenAuthenticationAlreadyPresentInContext_shouldNotProcessJwtAndProceedChain() throws ServletException, IOException {
        when(mockAuthentication.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer some.jwt.token");

        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(mockAuthentication);
        verifyNoInteractions(mockJwtValidator, mockJwtAuthenticationConverter, mockAuthenticationEntryPoint);
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
    }

    @Test
    @DisplayName("doFilterInternal: Нет JWT в запросе -> Authentication не устанавливается, цепочка продолжается")
    void doFilterInternal_whenNoJwtTokenInRequest_shouldNotSetAuthenticationAndProceedChain() throws ServletException, IOException {
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        verifyNoInteractions(mockJwtValidator, mockJwtAuthenticationConverter, mockAuthenticationEntryPoint);
    }

    @Test
    @DisplayName("doFilterInternal: Валидный JWT -> Authentication устанавливается, цепочка продолжается")
    void doFilterInternal_whenJwtTokenIsValid_shouldSetAuthenticationAndProceedChain() throws ServletException, IOException {
        String token = "valid.jwt.token";
        JwtValidationResult validResult = JwtValidationResult.success(mockJwsClaims);

        AppUserDetails mockUserDetails = mock(AppUserDetails.class);
        when(mockUserDetails.getId()).thenReturn(1L);
        when(mockUserDetails.getUsername()).thenReturn("test@example.com"); // Для лога

        // Настраиваем конвертер, чтобы он возвращал Authentication с этим UserDetails
        Authentication mockAuthWithUserDetails = mock(Authentication.class);
        when(mockAuthWithUserDetails.getPrincipal()).thenReturn(mockUserDetails);

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(validResult);
        // Используем mockClaimsBody, так как getJwsClaimsOptional().get().getPayload() вернет его
        when(mockJwtAuthenticationConverter.convert(mockClaimsBody, token)).thenReturn(mockAuthWithUserDetails);

        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(mockAuthWithUserDetails);
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        verifyNoInteractions(mockAuthenticationEntryPoint); // EntryPoint не должен вызываться при успехе
    }

    @Test
    @DisplayName("doFilterInternal: Невалидный JWT (ошибка валидатора) -> AuthenticationEntryPoint вызывается, цепочка прерывается")
    void doFilterInternal_whenJwtIsInvalidByValidator_shouldCallEntryPointAndReturn() throws ServletException, IOException {
        String token = "invalid.jwt.token";
        JwtValidationResult invalidResult = JwtValidationResult.failure(JwtErrorType.INVALID_SIGNATURE, "Bad signature");

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(invalidResult);

        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        verify(mockAuthenticationEntryPoint).commence(eq(mockRequest), eq(mockResponse), any(BadJwtException.class));
        verify(mockFilterChain, never()).doFilter(mockRequest, mockResponse); // Цепочка не должна продолжаться
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(); // Контекст не должен быть установлен
    }

    @Test
    @DisplayName("doFilterInternal: Ошибка конвертации Claims -> AuthenticationEntryPoint вызывается, цепочка прерывается")
    void doFilterInternal_whenClaimsConversionFails_shouldCallEntryPointAndReturn() throws ServletException, IOException {
        String token = "valid.token.but.bad.claims";
        // JwtValidator возвращает успех
        JwtValidationResult validResult = JwtValidationResult.success(mockJwsClaims);
        // Но JwtAuthenticationConverter выбрасывает исключение
        IllegalArgumentException conversionException = new IllegalArgumentException("Bad claims content");

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(validResult);
        when(mockJwtAuthenticationConverter.convert(mockClaimsBody, token)).thenThrow(conversionException);

        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Проверяем, что AuthenticationEntryPoint был вызван с BadJwtException, содержащим исходную причину
        ArgumentCaptor<AuthenticationException> authExCaptor = ArgumentCaptor.forClass(AuthenticationException.class);
        verify(mockAuthenticationEntryPoint).commence(eq(mockRequest), eq(mockResponse), authExCaptor.capture());

        assertThat(authExCaptor.getValue()).isInstanceOf(BadJwtException.class);
        BadJwtException badJwtEx = (BadJwtException) authExCaptor.getValue();
        assertThat(badJwtEx.getErrorType()).isEqualTo(JwtErrorType.OTHER_JWT_EXCEPTION); // Как мы определили в фильтре
        assertThat(badJwtEx.getCause()).isSameAs(conversionException);

        verify(mockFilterChain, never()).doFilter(mockRequest, mockResponse); // Цепочка не должна продолжаться
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @DisplayName("doFilterInternal: Заголовок Authorization без префикса 'Bearer ' -> Authentication не устанавливается, цепочка продолжается")
    @ParameterizedTest(name = "Для заголовка: \"{0}\"")
    @ValueSource(strings = {"NotBearer valid.jwt.token", "Bearer", "Token only"})
    void doFilterInternal_whenAuthorizationHeaderIsMissingBearerPrefix_shouldNotSetAuthenticationAndProceedChain(String invalidAuthHeader) throws ServletException, IOException {
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(invalidAuthHeader);

        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        verifyNoInteractions(mockJwtValidator, mockJwtAuthenticationConverter, mockAuthenticationEntryPoint);
    }

    @Test
    @DisplayName("doFilterInternal: Валидный JWT -> MDC должен быть установлен во время doFilter и очищен после")
    void doFilterInternal_whenJwtTokenIsValid_shouldSetAndClearMdc() throws ServletException, IOException {
        String token = "valid.jwt.token";
        Long expectedUserId = 123L;
        String expectedUserIdStr = String.valueOf(expectedUserId);

        // Мокаем AppUserDetails, который будет возвращен конвертером
        AppUserDetails mockUserDetails = mock(AppUserDetails.class);
        when(mockUserDetails.getId()).thenReturn(expectedUserId);
        when(mockUserDetails.getUsername()).thenReturn("test@example.com"); // Для лога

        // Настраиваем конвертер, чтобы он возвращал Authentication с этим UserDetails
        Authentication mockAuthWithUserDetails = mock(Authentication.class);
        when(mockAuthWithUserDetails.getPrincipal()).thenReturn(mockUserDetails);

        when(mockJwtAuthenticationConverter.convert(any(Claims.class), eq(token))).thenReturn(mockAuthWithUserDetails);

        // Настраиваем валидатор
        JwtValidationResult validResult = JwtValidationResult.success(mockJwsClaims); // mockJwsClaims из setUp
        when(mockJwsClaims.getPayload()).thenReturn(mock(Claims.class)); // Сам payload не важен для этого теста
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(validResult);
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);

        // Используем Answer для проверки MDC *внутри* вызова doFilter
        doAnswer(invocation -> {
            // В этот момент MDC должен быть установлен
            assertThat(MDC.get(JwtAuthenticationFilter.MDC_USER_ID_KEY)) // Используйте реальное имя константы
                    .isEqualTo(expectedUserIdStr);
            return null; // doFilter у FilterChain обычно void
        }).when(mockFilterChain).doFilter(mockRequest, mockResponse);

        // Act
        // Очищаем MDC перед вызовом, чтобы убедиться, что именно наш фильтр его ставит и чистит
        MDC.remove(JwtAuthenticationFilter.MDC_USER_ID_KEY);
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        // После того, как doFilterInternal отработал, MDC должен быть очищен
        assertThat(MDC.get(JwtAuthenticationFilter.MDC_USER_ID_KEY)).isNull();

        // Убедимся, что SecurityContext был установлен
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(mockAuthWithUserDetails);
        verify(mockFilterChain).doFilter(mockRequest, mockResponse); // Убедимся, что цепочка была вызвана
        verifyNoInteractions(mockAuthenticationEntryPoint);
    }

    @Test
    @DisplayName("doFilterInternal: Невалидный JWT -> MDC не должен быть установлен")
    void doFilterInternal_whenJwtIsInvalid_shouldNotSetMdc() throws ServletException, IOException {
        String token = "invalid.jwt.token";
        JwtValidationResult invalidResult = JwtValidationResult.failure(JwtErrorType.INVALID_SIGNATURE, "Bad signature");

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(mockJwtValidator.validateAndParseToken(token)).thenReturn(invalidResult);

        MDC.remove(JwtAuthenticationFilter.MDC_USER_ID_KEY); // Очищаем перед тестом
        jwtAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        assertThat(MDC.get(JwtAuthenticationFilter.MDC_USER_ID_KEY)).isNull();
        verify(mockAuthenticationEntryPoint).commence(any(), any(), any(BadJwtException.class));
        verify(mockFilterChain, never()).doFilter(any(), any());
    }
}