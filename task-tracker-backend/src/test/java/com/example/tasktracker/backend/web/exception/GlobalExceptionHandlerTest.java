package com.example.tasktracker.backend.web.exception;

import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.jwt.JwtErrorType;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.security.Principal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты для {@link GlobalExceptionHandler}.
 * Проверяют корректность формирования {@link ProblemDetail} для различных исключений,
 * предполагая, что все необходимые ключи существуют в {@link MessageSource}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Используем LENIENT, т.к. не все моки MessageSource могут быть вызваны в каждом тесте
class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource mockMessageSource;

    @Mock
    private WebRequest mockWebRequest; // Используется для getLocale

    @Mock
    private ServletWebRequest mockServletWebRequest; // Используется для getLocale и извлечения токена

    @Mock
    private MockHttpServletRequest mockHttpServletRequest; // Для ServletWebRequest

    @Mock
    private Principal mockUserPrincipal;

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    private static final String BASE_PROBLEM_URI = "https://task-tracker.example.com/probs/";
    private static final Locale TEST_LOCALE = Locale.ENGLISH;

    @BeforeEach
    void setUp() {
        when(mockWebRequest.getLocale()).thenReturn(TEST_LOCALE);
        when(mockServletWebRequest.getLocale()).thenReturn(TEST_LOCALE);
        when(mockServletWebRequest.getRequest()).thenReturn(mockHttpServletRequest);
    }

    // Хелпер для настройки MessageSource для одного набора ключей
    private void setupMessageSourceForSuffix(String typeSuffix, String expectedTitle, String expectedDetail, Object[] detailArgs) {
        String titleKey = "problemDetail." + typeSuffix + ".title";
        String detailKey = "problemDetail." + typeSuffix + ".detail";

        when(mockMessageSource.getMessage(eq(titleKey), isNull(), eq(TEST_LOCALE)))
                .thenReturn(expectedTitle);
        when(mockMessageSource.getMessage(eq(detailKey), eq(detailArgs), eq(TEST_LOCALE)))
                .thenReturn(expectedDetail);
    }

    @ParameterizedTest
    @EnumSource(JwtErrorType.class)
    @DisplayName("handleBadJwtException: для каждого JwtErrorType -> должен вернуть корректный 401 ProblemDetail")
    void handleBadJwtException_forEachJwtErrorType_shouldReturnCorrectUnauthorizedProblemDetail(JwtErrorType errorType) {
        // Arrange
        String originalErrorMessage = "JWT Error details for " + errorType.name();
        BadJwtException exception = new BadJwtException(originalErrorMessage, errorType, new RuntimeException("Root cause"));

        String typeSuffix = "jwt." + errorType.name().toLowerCase();
        String expectedTitle = "Title for " + errorType.name();
        String expectedDetail = "Detail for " + errorType.name() + ": " + originalErrorMessage;
        Object[] detailArgs = new Object[]{originalErrorMessage};

        setupMessageSourceForSuffix(typeSuffix, expectedTitle, expectedDetail, detailArgs);
        when(mockServletWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer short"); // Для extractTokenSnippet

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleBadJwtException(exception, mockServletWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
        assertThat(problemDetail.getProperties()).containsEntry("error_type", errorType.name());
    }

    @Test
    @DisplayName("handleAuthenticationException: для общего AuthenticationException -> должен вернуть корректный 401 ProblemDetail")
    void handleAuthenticationException_whenGenericAuthError_shouldReturnCorrectUnauthorizedProblemDetail() {
        // Arrange
        String originalErrorMessage = "Generic authentication error";
        AuthenticationException exception = new BadCredentialsException(originalErrorMessage);
        String typeSuffix = "unauthorized";
        String expectedTitle = "Authentication Required Title";
        String expectedDetail = "Authentication Detail: " + originalErrorMessage;
        Object[] detailArgs = new Object[]{originalErrorMessage};

        setupMessageSourceForSuffix(typeSuffix, expectedTitle, expectedDetail, detailArgs);

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleAuthenticationException(exception, mockWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
    }

    @Test
    @DisplayName("handleAccessDeniedException: должен вернуть корректный 403 ProblemDetail")
    void handleAccessDeniedException_shouldReturnCorrectForbiddenProblemDetail() {
        // Arrange
        String originalErrorMessage = "Access is denied to this very secret resource";
        AccessDeniedException exception = new AccessDeniedException(originalErrorMessage);
        String typeSuffix = "forbidden";
        String expectedTitle = "Access Denied Title";
        String expectedDetail = "Access Denied Detail: " + originalErrorMessage;
        Object[] detailArgs = new Object[]{originalErrorMessage};

        setupMessageSourceForSuffix(typeSuffix, expectedTitle, expectedDetail, detailArgs);
        when(mockServletWebRequest.getUserPrincipal()).thenReturn(mockUserPrincipal);
        when(mockUserPrincipal.getName()).thenReturn("testuser");
        when(mockHttpServletRequest.getMethod()).thenReturn("GET");
        when(mockHttpServletRequest.getRequestURI()).thenReturn("/api/v1/secret");

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleAccessDeniedException(exception, mockServletWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
    }

    // --- Тесты для вспомогательного метода extractTokenSnippetFromRequest ---
    // (Остаются такими же, как мы согласовали ранее, так как он package-private и не изменился)
    @Test
    @DisplayName("extractTokenSnippetFromRequest: Bearer токен присутствует -> должен вернуть сокращенный токен")
    void extractTokenSnippetFromRequest_whenBearerTokenPresent_shouldReturnTruncatedToken() {
        String fullToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTYxNjAwMDAwMCwiZXhwIjoxNjE2MDAzNjAwfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String expectedSnippet = JwtValidator.truncateTokenForLogging(fullToken);
        when(mockHttpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + fullToken);

        String snippet = globalExceptionHandler.extractTokenSnippetFromRequest(mockServletWebRequest);
        assertThat(snippet).isEqualTo(expectedSnippet);
    }

    @Test
    @DisplayName("extractTokenSnippetFromRequest: Токен отсутствует или не Bearer -> должен вернуть плейсхолдер")
    void extractTokenSnippetFromRequest_whenNoBearerToken_shouldReturnPlaceholder() {
        when(mockHttpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        String snippet1 = globalExceptionHandler.extractTokenSnippetFromRequest(mockServletWebRequest);
        assertThat(snippet1).isEqualTo("[token not present or not bearer]");

        when(mockHttpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNzd29yZA==");
        String snippet2 = globalExceptionHandler.extractTokenSnippetFromRequest(mockServletWebRequest);
        assertThat(snippet2).isEqualTo("[token not present or not bearer]");
    }

    @Test
    @DisplayName("extractTokenSnippetFromRequest: WebRequest не ServletWebRequest -> должен вернуть плейсхолдер")
    void extractTokenSnippetFromRequest_whenNotServletWebRequest_shouldReturnPlaceholder() {
        String snippet = globalExceptionHandler.extractTokenSnippetFromRequest(mockWebRequest);
        assertThat(snippet).isEqualTo("[non-http request]");
    }
}