package com.example.tasktracker.backend.web.exception;

import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.jwt.JwtErrorType;
import com.example.tasktracker.backend.security.jwt.JwtValidator; // Для мока в extractTokenSnippetFromRequest
import com.example.tasktracker.backend.web.ApiConstants; // Для BASE_PROBLEM_URI
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.*;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link GlobalExceptionHandler}.
 * Проверяют корректность формирования {@link ProblemDetail} для различных исключений.
 * Предполагается, что все необходимые ключи для title/detail существуют в {@link MessageSource}
 * и что MessageSource выбросит {@link NoSuchMessageException}, если ключ не найден.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource mockMessageSource;

    // WebRequest мокается для передачи в методы обработчиков
    @Mock
    private WebRequest mockWebRequest;
    @Mock
    private ServletWebRequest mockServletWebRequest; // Для тестов, где нужен HttpServletRequest
    @Mock
    private HttpServletResponse mockHttpResponse;
    @Mock
    private MockHttpServletRequest mockHttpServletRequest; // Для ServletWebRequest

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    private static final String BASE_PROBLEM_URI = ApiConstants.PROBLEM_TYPE_BASE_URI;
    private static final Locale TEST_LOCALE = Locale.ENGLISH;

    @BeforeEach
    void setUp() {
        // Общая настройка для локали
        when(mockWebRequest.getLocale()).thenReturn(TEST_LOCALE);
        when(mockServletWebRequest.getLocale()).thenReturn(TEST_LOCALE);
        // Для setInstanceUriIfAbsent и extractTokenSnippetFromRequest
        when(mockServletWebRequest.getRequest()).thenReturn(mockHttpServletRequest);
        when(mockServletWebRequest.getResponse()).thenReturn(mockHttpResponse);
        when(mockHttpServletRequest.getRequestURI()).thenReturn("/test/path"); // Пример URI для instance

    }

    /**
     * Вспомогательный метод для настройки мока MessageSource.
     * Теперь detailArgs не передается, так как buildProblemDetail ожидает статический detail.
     * @param typeSuffix Суффикс для ключей.
     * @param expectedTitle Ожидаемый заголовок.
     * @param expectedDetail Ожидаемое статическое детальное сообщение.
     */
    private void setupMessageSourceForStaticMessages(String typeSuffix, String expectedTitle, String expectedDetail) {
        String titleKey = "problemDetail." + typeSuffix + ".title";
        String detailKey = "problemDetail." + typeSuffix + ".detail";

        when(mockMessageSource.getMessage(eq(titleKey), isNull(), eq(TEST_LOCALE)))
                .thenReturn(expectedTitle);
        // Detail теперь всегда без аргументов для buildProblemDetail
        when(mockMessageSource.getMessage(eq(detailKey), isNull(), eq(TEST_LOCALE)))
                .thenReturn(expectedDetail);
    }

    @ParameterizedTest
    @EnumSource(JwtErrorType.class)
    @DisplayName("handleBadJwtException: должен вернуть 401 ProblemDetail с error_type и jwt_error_details в properties")
    void handleBadJwtException_shouldReturnCorrectUnauthorizedProblemDetail(JwtErrorType errorType) {
        // Arrange
        String originalErrorMessage = "JWT Error: " + errorType.name();
        BadJwtException exception = new BadJwtException(originalErrorMessage, errorType, new RuntimeException("Root cause"));

        String typeSuffix = "jwt." + errorType.name().toLowerCase();
        String expectedTitle = "JWT Title for " + errorType.name();
        String expectedStaticDetail = "Static detail for JWT error."; // Теперь деталь статическая

        setupMessageSourceForStaticMessages(typeSuffix, expectedTitle, expectedStaticDetail);
        when(mockServletWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer short");

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleBadJwtException(exception, mockServletWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix.replaceAll("\\.", "/")));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedStaticDetail); // Проверяем статическую деталь
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties()).containsEntry("error_type", errorType.name());
        assertThat(problemDetail.getProperties()).containsEntry("jwt_error_details", originalErrorMessage);
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create("/test/path"));
    }

    @Test
    @DisplayName("handleAuthenticationException (общий): должен вернуть 401 ProblemDetail")
    void handleAuthenticationException_whenGenericAuthError_shouldReturnCorrectUnauthorizedProblemDetail() {
        // Arrange
        String originalErrorMessage = "Generic authentication error";
        // Используем AuthenticationException, а не BadCredentialsException, чтобы проверить общий обработчик
        AuthenticationException exception = new AuthenticationException(originalErrorMessage) {};
        String typeSuffix = "unauthorized";
        String expectedTitle = "Auth Required Title";
        String expectedStaticDetail = "Static detail for general auth error.";

        setupMessageSourceForStaticMessages(typeSuffix, expectedTitle, expectedStaticDetail);

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleAuthenticationException(exception, mockWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix.replaceAll("\\.", "/")));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedStaticDetail);
        assertThat(problemDetail.getProperties()).containsEntry("auth_error_details", originalErrorMessage);
        // setInstanceUriIfAbsent не вызовется, если mockWebRequest не ServletWebRequest,
        // но для консистентности можно использовать mockServletWebRequest и для этого теста.
        // Давайте изменим на mockServletWebRequest для проверки instance.
        // when(mockHttpServletRequest.getRequestURI()).thenReturn("/some/other/path"); // Если нужно другое
        // problemDetail = globalExceptionHandler.handleAuthenticationException(exception, mockServletWebRequest);
        // assertThat(problemDetail.getInstance()).isEqualTo(URI.create("/some/other/path"));
        // Пока оставим как есть, но это место для улучшения, если хотим проверять instance всегда.
    }

    @Test
    @DisplayName("handleBadCredentialsException: должен вернуть 401 ProblemDetail с WWW-Authenticate")
    void handleBadCredentialsException_shouldReturnUnauthorizedWithWwwAuth() {
        // Arrange
        String originalErrorMessage = "Bad credentials provided";
        BadCredentialsException exception = new BadCredentialsException(originalErrorMessage);
        String typeSuffix = "auth.invalidCredentials";
        String expectedTitle = "Invalid Credentials Title";
        String expectedStaticDetail = "Static detail for bad credentials.";

        setupMessageSourceForStaticMessages(typeSuffix, expectedTitle, expectedStaticDetail);

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleBadCredentialsException(exception, mockServletWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix.replaceAll("\\.", "/")));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedStaticDetail);
        assertThat(problemDetail.getProperties()).containsEntry("login_error_details", originalErrorMessage);
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create("/test/path"));
        // Проверка установки заголовка теперь делается в интеграционном тесте контроллера,
        // но здесь мы можем проверить, что response.setHeader был вызван, если бы мокали response.
        // Поскольку мы используем MockHttpServletRequest, его response - это MockHttpServletResponse,
        // мы можем проверить заголовок на нем.
        verify(mockHttpResponse).setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"task-tracker\"");
    }

    @Test
    @DisplayName("handleAccessDeniedException: должен вернуть 403 ProblemDetail")
    void handleAccessDeniedException_shouldReturnForbiddenProblemDetail() {
        // Arrange
        String originalErrorMessage = "Access is denied";
        AccessDeniedException exception = new AccessDeniedException(originalErrorMessage);
        String typeSuffix = "forbidden";
        String expectedTitle = "Access Denied Title";
        String expectedStaticDetail = "Static detail for access denied.";

        setupMessageSourceForStaticMessages(typeSuffix, expectedTitle, expectedStaticDetail);
        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("testuser");
        when(mockServletWebRequest.getUserPrincipal()).thenReturn(mockPrincipal);
        when(mockHttpServletRequest.getMethod()).thenReturn("GET");
        // URI уже настроен в setUp на /test/path

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleAccessDeniedException(exception, mockServletWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix.replaceAll("\\.", "/")));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedStaticDetail);
        assertThat(problemDetail.getProperties()).containsEntry("access_denied_reason", originalErrorMessage);
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create("/test/path"));
    }

    @Test
    @DisplayName("handleConstraintViolationException: должен вернуть 400 ProblemDetail с invalid_params")
    void handleConstraintViolationException_shouldReturnBadRequestWithInvalidParams() {
        // Arrange
        ConstraintViolation<?> mockViolation = mock(ConstraintViolation.class);
        Path mockPath = mock(Path.class);
        when(mockPath.toString()).thenReturn("some.field");
        when(mockViolation.getPropertyPath()).thenReturn(mockPath);
        when(mockViolation.getMessage()).thenReturn("must not be blank");
        Set<ConstraintViolation<?>> violations = Set.of(mockViolation);
        ConstraintViolationException exception = new ConstraintViolationException("Validation failed", violations);

        String typeSuffix = "validation.constraintViolation";
        String expectedTitle = "Validation Error Title";
        String expectedStaticDetail = "Static detail for constraint violation.";

        setupMessageSourceForStaticMessages(typeSuffix, expectedTitle, expectedStaticDetail);

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleConstraintViolationException(exception, mockWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix.replaceAll("\\.", "/")));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedStaticDetail);
        assertThat(problemDetail.getProperties()).containsKey("invalid_params");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> invalidParams = (List<Map<String, String>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).hasSize(1);
        assertThat(invalidParams.get(0)).containsEntry("field", "field").containsEntry("message", "must not be blank");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: должен вернуть 400 ResponseEntity со статическим detail и invalid_params/error_count в properties")
    void handleMethodArgumentNotValid_shouldReturnBadRequestWithStaticDetailAndProperties() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult mockBindingResult = mock(BindingResult.class);
        FieldError mockFieldError = new FieldError("objectForValidation", "fieldName", "rejectedFieldValue",
                false, null, null, "Validation error message for fieldName");

        when(exception.getBindingResult()).thenReturn(mockBindingResult);
        when(mockBindingResult.getFieldErrors()).thenReturn(List.of(mockFieldError));
        int errorCount = 1;
        when(exception.getErrorCount()).thenReturn(errorCount);

        String typeSuffix = "validation.methodArgumentNotValid";
        String expectedTitle = "Invalid Request Data From MessageSource"; // Ожидаемый title из MessageSource
        String expectedStaticDetail = "Static detail for invalid request data from MessageSource."; // Ожидаемый СТАТИЧЕСКИЙ detail

        // Настраиваем MessageSource для title и СТАТИЧЕСКОГО detail
        when(mockMessageSource.getMessage(eq("problemDetail." + typeSuffix + ".title"), isNull(), eq(TEST_LOCALE)))
                .thenReturn(expectedTitle);
        when(mockMessageSource.getMessage(eq("problemDetail." + typeSuffix + ".detail"), isNull(), eq(TEST_LOCALE))) // args теперь isNull()
                .thenReturn(expectedStaticDetail);

        HttpHeaders headers = new HttpHeaders();
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleMethodArgumentNotValid(
                exception, headers, httpStatus, mockServletWebRequest); // Используем mockServletWebRequest для instance URI

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(httpStatus);
        ProblemDetail problemDetail = (ProblemDetail) responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix.replaceAll("\\.", "/")));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedStaticDetail); // Проверяем СТАТИЧЕСКИЙ detail

        assertThat(problemDetail.getProperties()).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).isNotNull().hasSize(1);
        assertThat(invalidParams.get(0))
                .containsEntry("field", "fieldName")
                .containsEntry("rejected_value", "rejectedFieldValue")
                .containsEntry("message", "Validation error message for fieldName");

        assertThat(problemDetail.getInstance()).isEqualTo(URI.create("/test/path")); // Проверяем instance URI
    }

    @Test
    @DisplayName("handleNoSuchMessageException: должен вернуть 500 ProblemDetail с информацией об отсутствующем ключе")
    void handleNoSuchMessageException_shouldReturnInternalServerErrorProblemDetail() {
        // Arrange
        String missingKeyName = "non.existent.key"; // Имя ключа
        // Формируем ожидаемое полное сообщение, которое вернет ex.getMessage()
        String expectedExceptionMessage = "No message found under code '" + missingKeyName + "' for locale '" + TEST_LOCALE.toString() + "'.";
        NoSuchMessageException exception = new NoSuchMessageException(missingKeyName, TEST_LOCALE);

        String typeSuffix = "internal.missingMessageResource";
        String expectedTitle = "Internal Config Error Title";
        String expectedStaticDetail = "Static detail for missing message resource.";

        setupMessageSourceForStaticMessages(typeSuffix, expectedTitle, expectedStaticDetail);
        when(mockServletWebRequest.getDescription(true)).thenReturn("request description");

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleNoSuchMessageException(exception, mockServletWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + typeSuffix.replaceAll("\\.", "/")));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedStaticDetail); // detail для основного случая должен быть статическим

        assertThat(problemDetail.getProperties()).isNotNull();
        // Проверяем, что missing_resource_info содержит ПОЛНОЕ сообщение из исключения
        assertThat(problemDetail.getProperties()).containsEntry("missing_resource_info", expectedExceptionMessage);

        assertThat(problemDetail.getInstance()).isEqualTo(URI.create("/test/path"));
    }

    @Test
    @DisplayName("handleNoSuchMessageException: ULTRA-CRITICAL, когда даже ключи для ошибки локализации отсутствуют")
    void handleNoSuchMessageException_whenLocalizationEmergencyKeysMissing_shouldReturnHardcodedFallback() {
        // Arrange
        String originalMissingKeyName = "some.original.missing.key";
        String originalExceptionMessage = "No message found under code '" + originalMissingKeyName + "' for locale '" + TEST_LOCALE.toString() + "'.";
        NoSuchMessageException originalException = new NoSuchMessageException(originalMissingKeyName, TEST_LOCALE);

        String emergencyKeyNameForTitle = "problemDetail.internal.missingMessageResource.title"; // Предположим, этот ключ отсутствует
        String emergencyExceptionMessage = "No message found under code '" + emergencyKeyNameForTitle + "' for locale '" + TEST_LOCALE.toString() + "'.";

        // Имитируем, что ключ для title обработчика NoSuchMessageException отсутствует
        when(mockMessageSource.getMessage(eq(emergencyKeyNameForTitle), isNull(), eq(TEST_LOCALE)))
                .thenThrow(new NoSuchMessageException(emergencyKeyNameForTitle, TEST_LOCALE)); // Это будет localizationEmergency
        // Для detail можно настроить возврат значения, чтобы проверить, что title имеет приоритет в падении
        when(mockMessageSource.getMessage(eq("problemDetail.internal.missingMessageResource.detail"), isNull(), eq(TEST_LOCALE)))
                .thenReturn("Some detail that should not be overridden by fallback if title fails first");

        when(mockServletWebRequest.getDescription(true)).thenReturn("request description");

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleNoSuchMessageException(originalException, mockServletWebRequest);

        // Assert
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problemDetail.getType()).isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "internal/localization-emergency"));
        assertThat(problemDetail.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problemDetail.getDetail()).isEqualTo(
                "A critical internal configuration error occurred regarding localization. Please contact support. " +
                        "Details: " + originalExceptionMessage); // detail включает сообщение от *оригинальной* ошибки

        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties())
                .containsEntry("missing_resource_info", originalExceptionMessage) // От оригинальной ошибки
                .containsEntry("secondary_missing_resource_info", emergencyExceptionMessage); // От ошибки при попытке загрузить сообщение для ошибки

        assertThat(problemDetail.getInstance()).isEqualTo(URI.create("/test/path"));
    }

    // Тесты для extractTokenSnippetFromRequest и getFieldNameFromPath остаются прежними,
    // так как их логика не изменилась.
    @Test
    @DisplayName("extractTokenSnippetFromRequest: Bearer токен присутствует -> должен вернуть сокращенный токен")
    void extractTokenSnippetFromRequest_whenBearerTokenPresent_shouldReturnTruncatedToken() {
        String fullToken = "abc.def.ghi"; // Короткий для простоты, логика усечения в JwtValidator
        String expectedSnippet = JwtValidator.truncateTokenForLogging(fullToken);
        when(mockHttpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + fullToken);
        String snippet = globalExceptionHandler.extractTokenSnippetFromRequest(mockServletWebRequest);
        assertThat(snippet).isEqualTo(expectedSnippet);
    }
}