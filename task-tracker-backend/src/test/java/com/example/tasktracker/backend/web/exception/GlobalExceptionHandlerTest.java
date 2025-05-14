package com.example.tasktracker.backend.web.exception;

import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.exception.PasswordMismatchException;
import com.example.tasktracker.backend.security.exception.UserAlreadyExistsException;
import com.example.tasktracker.backend.security.jwt.JwtErrorType;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
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
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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

    @Test
    @DisplayName("handleUserAlreadyExistsException: должен вернуть корректный 409 Conflict ProblemDetail")
    void handleUserAlreadyExistsException_shouldReturnCorrectConflictProblemDetail() {
        // Arrange
        String errorMessage = "User with email test@example.com already exists.";
        UserAlreadyExistsException exception = new UserAlreadyExistsException(errorMessage);
        String typeSuffix = "user.alreadyExists";
        String expectedTitle = "User Exists Title";
        String expectedDetail = "User Exists Detail: " + errorMessage;
        Object[] detailArgs = new Object[]{errorMessage};

        setupMessageSourceForSuffix(typeSuffix, expectedTitle, expectedDetail, detailArgs);

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleUserAlreadyExistsException(exception, mockWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
    }

    @Test
    @DisplayName("handlePasswordMismatchException: должен вернуть корректный 400 Bad Request ProblemDetail")
    void handlePasswordMismatchException_shouldReturnCorrectBadRequestProblemDetail() {
        // Arrange
        String errorMessage = "Passwords do not match.";
        PasswordMismatchException exception = new PasswordMismatchException(errorMessage);
        String typeSuffix = "user.passwordMismatch";
        String expectedTitle = "Password Mismatch Title";
        String expectedDetail = "Password Mismatch Detail: " + errorMessage;
        Object[] detailArgs = new Object[]{errorMessage};

        setupMessageSourceForSuffix(typeSuffix, expectedTitle, expectedDetail, detailArgs);

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handlePasswordMismatchException(exception, mockWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
    }

    @Test
    @DisplayName("handleConstraintViolationException: должен вернуть 400 Bad Request ProblemDetail с invalid_params")
    void handleConstraintViolationException_shouldReturnCorrectBadRequestProblemDetailWithInvalidParams() {
        // Arrange
        ConstraintViolation<?> mockViolation1 = mock(ConstraintViolation.class);
        Path mockPath1 = mock(Path.class);
        when(mockPath1.toString()).thenReturn("registerRequest.email");
        when(mockViolation1.getPropertyPath()).thenReturn(mockPath1);
        when(mockViolation1.getMessage()).thenReturn("Email must be valid");

        ConstraintViolation<?> mockViolation2 = mock(ConstraintViolation.class);
        Path mockPath2 = mock(Path.class);
        when(mockPath2.toString()).thenReturn("registerRequest.password");
        when(mockViolation2.getPropertyPath()).thenReturn(mockPath2);
        when(mockViolation2.getMessage()).thenReturn("Password cannot be blank");

        Set<ConstraintViolation<?>> violations = Set.of(mockViolation1, mockViolation2);
        ConstraintViolationException exception = new ConstraintViolationException("Validation failed", violations);

        String typeSuffix = "validation.constraintViolation";
        String expectedTitle = "Constraint Violation Title";
        // Детальное сообщение для ConstraintViolationException может быть общим,
        // т.к. конкретика в invalid_params
        String expectedDetail = "Constraint Violation Detail: " + exception.getMessage();
        Object[] detailArgs = new Object[]{exception.getMessage()};
        setupMessageSourceForSuffix(typeSuffix, expectedTitle, expectedDetail, detailArgs);

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleConstraintViolationException(exception, mockWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> invalidParams = (List<Map<String, String>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).isNotNull().hasSize(2);
        assertThat(invalidParams).extracting("field")
                .containsExactlyInAnyOrder("email", "password"); // Извлекаем из "registerRequest.email"
        assertThat(invalidParams).extracting("message")
                .containsExactlyInAnyOrder("Email must be valid", "Password cannot be blank");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: должен вернуть 400 Bad Request ResponseEntity с ProblemDetail и invalid_params")
    void handleMethodArgumentNotValid_shouldReturnCorrectBadRequestProblemDetailWithInvalidParams() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult mockBindingResult = mock(BindingResult.class);
        FieldError mockFieldError1 = new FieldError("objectName", "email", "rejectedValueForEmail", false, null, null, "Email error message");
        FieldError mockFieldError2 = new FieldError("objectName", "password", null, false, null, null, "Password error message");
        List<FieldError> fieldErrors = List.of(mockFieldError1, mockFieldError2);

        when(exception.getBindingResult()).thenReturn(mockBindingResult);
        when(mockBindingResult.getFieldErrors()).thenReturn(fieldErrors);
        when(exception.getErrorCount()).thenReturn(fieldErrors.size()); // Для аргумента сообщения
        when(exception.getMessage()).thenReturn("Validation failed for object='objectName'. Error count: 2"); // Пример сообщения

        String typeSuffix = "validation.methodArgumentNotValid";
        String expectedTitle = "Method Argument Not Valid Title";
        // {0} будет заменено на ex.getErrorCount()
        String expectedDetailMessageFromBundle = "Method Argument Not Valid Detail for " + fieldErrors.size() + " errors.";
        Object[] detailArgs = new Object[]{fieldErrors.size()};

        setupMessageSourceForSuffix(typeSuffix, expectedTitle, expectedDetailMessageFromBundle, detailArgs);

        HttpHeaders headers = new HttpHeaders();
        // HttpStatusCode status = HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()); // Не обязательно мокать, т.к. передается

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleMethodArgumentNotValid(
                exception, headers, HttpStatus.BAD_REQUEST, mockWebRequest
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseEntity.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problemDetail = (ProblemDetail) responseEntity.getBody();

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetailMessageFromBundle);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).isNotNull().hasSize(2);
        assertThat(invalidParams).extracting("field")
                .containsExactlyInAnyOrder("email", "password");
        assertThat(invalidParams).extracting("rejected_value")
                .containsExactlyInAnyOrder("rejectedValueForEmail", "null");
        assertThat(invalidParams).extracting("message")
                .containsExactlyInAnyOrder("Email error message", "Password error message");
    }

    // --- Тесты для вспомогательного метода extractTokenSnippetFromRequest ---
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