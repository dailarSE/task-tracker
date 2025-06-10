package com.example.tasktracker.backend.web.exception;

import com.example.tasktracker.backend.security.exception.BadJwtException;
import com.example.tasktracker.backend.security.jwt.JwtErrorType;
import com.example.tasktracker.backend.security.jwt.JwtValidator;
import com.example.tasktracker.backend.web.ApiConstants;
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
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для НОВОЙ версии {@link GlobalExceptionHandler}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource mockMessageSource;
    @Mock
    private WebRequest mockWebRequest;
    @Mock
    private ServletWebRequest mockServletWebRequest;
    @Mock
    private HttpServletResponse mockHttpResponse;
    @Mock
    private MockHttpServletRequest mockHttpServletRequest;

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    private static final String BASE_PROBLEM_URI = ApiConstants.PROBLEM_TYPE_BASE_URI;
    private static final Locale TEST_LOCALE = Locale.ENGLISH;
    private static final String TEST_REQUEST_URI = "/test/path";

    @BeforeEach
    void setUp() {
        when(mockWebRequest.getLocale()).thenReturn(TEST_LOCALE);
        when(mockServletWebRequest.getLocale()).thenReturn(TEST_LOCALE);
        when(mockServletWebRequest.getRequest()).thenReturn(mockHttpServletRequest);
        when(mockServletWebRequest.getResponse()).thenReturn(mockHttpResponse);
        when(mockHttpServletRequest.getRequestURI()).thenReturn(TEST_REQUEST_URI);
    }

    // ИЗМЕНЕНИЕ: Новый хелпер для настройки MessageSource, который может принимать аргументы
    private void setupMessageSource(String typeSuffix, String expectedTitle, String expectedDetailPattern, Object... detailArgs) {
        String titleKey = "problemDetail." + typeSuffix + ".title";
        String detailKey = "problemDetail." + typeSuffix + ".detail";
        when(mockMessageSource.getMessage(eq(titleKey), isNull(), eq(TEST_LOCALE))).thenReturn(expectedTitle);
        when(mockMessageSource.getMessage(eq(detailKey), eq(detailArgs), eq(TEST_LOCALE))).thenReturn(String.format(expectedDetailPattern.replace("{0}", "%s").replace("{1}", "%s").replace("{2}", "%s"), detailArgs));
    }

    // ИЗМЕНЕНИЕ: Базовый assert, чтобы не дублировать код
    private void assertProblemDetailBase(ProblemDetail actual, HttpStatus expectedStatus, String expectedTypeUriPath, String expectedInstancePath) {
        assertThat(actual.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(actual.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + expectedTypeUriPath));
        assertThat(actual.getInstance()).isEqualTo(URI.create(expectedInstancePath));
    }


    @ParameterizedTest(name = "Для JWT ошибки типа {0}")
    @EnumSource(JwtErrorType.class)
    @DisplayName("handleBadJwtException: должен вернуть 401 ProblemDetail для всех типов ошибок JWT")
    void handleBadJwtException_shouldReturnCorrectUnauthorizedProblemDetail(JwtErrorType errorType) {
        // Arrange
        BadJwtException exception = new BadJwtException("JWT Error", errorType);
        String typeSuffix = "jwt." + errorType.name().toLowerCase();
        String expectedTypeUriPath = "jwt/" + errorType.name().toLowerCase().replace('.','/');
        setupMessageSource(typeSuffix, "JWT Title", "JWT Detail");

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleBadJwtException(exception, mockServletWebRequest);

        // Assert
        assertProblemDetailBase(problemDetail, HttpStatus.UNAUTHORIZED, expectedTypeUriPath, TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).isNullOrEmpty(); // Проверяем, что properties пустые, как договорились
    }

    @Test
    @DisplayName("handleAuthenticationException: должен вернуть 401 ProblemDetail")
    void handleAuthenticationException_shouldReturnCorrectUnauthorizedProblemDetail() {
        // Arrange
        AuthenticationException exception = new AuthenticationException("Generic auth error") {};
        setupMessageSource("unauthorized", "Auth Required", "Auth is required.");

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleAuthenticationException(exception, mockServletWebRequest);

        // Assert
        assertProblemDetailBase(problemDetail, HttpStatus.UNAUTHORIZED, "unauthorized", TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).isNullOrEmpty();
    }

    @Test
    @DisplayName("handleAccessDeniedException: должен вернуть 403 ProblemDetail")
    void handleAccessDeniedException_shouldReturnForbiddenProblemDetail() {
        // Arrange
        AccessDeniedException exception = new AccessDeniedException("Access is denied");
        setupMessageSource("forbidden", "Access Denied", "You shall not pass.");

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleAccessDeniedException(exception, mockServletWebRequest);

        // Assert
        assertProblemDetailBase(problemDetail, HttpStatus.FORBIDDEN, "forbidden", TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).isNullOrEmpty();
    }

    @Test
    @DisplayName("handleBadCredentialsException: должен вернуть 401 ProblemDetail без properties и с заголовком")
    void handleBadCredentialsException_shouldReturnUnauthorizedWithWwwAuth() {
        // Arrange
        BadCredentialsException exception = new BadCredentialsException("Bad creds");
        String typeSuffix = "auth.invalidCredentials";
        setupMessageSource(typeSuffix, "Invalid Credentials", "Incorrect email or password.", (Object[]) null);

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleBadCredentialsException(exception, mockServletWebRequest);

        // Assert
        assertProblemDetailBase(problemDetail, HttpStatus.UNAUTHORIZED, "auth/invalid-credentials", TEST_REQUEST_URI);
        assertThat(problemDetail.getTitle()).isEqualTo("Invalid Credentials");
        assertThat(problemDetail.getDetail()).isEqualTo("Incorrect email or password.");
        assertThat(problemDetail.getProperties()).isNullOrEmpty();
        verify(mockHttpResponse).setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"task-tracker\"");
    }

    @Test
    @DisplayName("handleConstraintViolationException: должен вернуть 400 с invalidParams и динамическим detail")
    void handleConstraintViolationException_shouldReturnBadRequestWithInvalidParamsAndDynamicDetail() {
        // Arrange
        ConstraintViolation<?> mockViolation = mock(ConstraintViolation.class);
        Path mockPath = mock(Path.class);
        when(mockPath.toString()).thenReturn("some.field");
        when(mockViolation.getPropertyPath()).thenReturn(mockPath);
        when(mockViolation.getMessage()).thenReturn("must not be blank");
        Set<ConstraintViolation<?>> violations = Set.of(mockViolation);
        ConstraintViolationException exception = new ConstraintViolationException("Validation failed", violations);

        String typeSuffix = "validation.constraintViolation";
        // ИЗМЕНЕНИЕ: Настраиваем мок с ожиданием аргумента
        setupMessageSource(typeSuffix, "Constraint Violation", "The request is invalid due to {0} constraint violation(s).", 1);

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleConstraintViolationException(exception, mockServletWebRequest);

        // Assert
        assertProblemDetailBase(problemDetail, HttpStatus.BAD_REQUEST, "validation/constraint-violation", TEST_REQUEST_URI);
        assertThat(problemDetail.getTitle()).isEqualTo("Constraint Violation");
        assertThat(problemDetail.getDetail()).isEqualTo("The request is invalid due to 1 constraint violation(s).");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> invalidParams = (List<Map<String, String>>) problemDetail.getProperties().get("invalidParams");
        assertThat(invalidParams).hasSize(1);
        assertThat(invalidParams.getFirst()).containsEntry("field", "field").containsEntry("message", "must not be blank");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: должен вернуть 400 с invalidParams и динамическим detail")
    void handleMethodArgumentNotValid_shouldReturnBadRequestWithProblemDetail() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult mockBindingResult = mock(BindingResult.class);
        FieldError mockFieldError = new FieldError("object", "field", null, false, null, null, "message");
        ObjectError mockGlobalError = new ObjectError("object", "global message");

        when(exception.getBindingResult()).thenReturn(mockBindingResult);
        when(mockBindingResult.getFieldErrors()).thenReturn(List.of(mockFieldError));
        when(mockBindingResult.getGlobalErrors()).thenReturn(List.of(mockGlobalError)); // ИЗМЕНЕНИЕ: Добавили глобальную ошибку
        when(exception.getErrorCount()).thenReturn(2); // Теперь 2 ошибки

        String typeSuffix = "validation.methodArgumentNotValid";
        setupMessageSource(typeSuffix, "Invalid Request Data", "Validation failed. Found {0} error(s). Please check the 'invalidParams' property for details.", 2);

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleMethodArgumentNotValid(
                exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, mockServletWebRequest);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = (ProblemDetail) responseEntity.getBody();
        assertProblemDetailBase(problemDetail, HttpStatus.BAD_REQUEST, "validation/method-argument-not-valid", TEST_REQUEST_URI);
        assertThat(problemDetail.getDetail()).isEqualTo("Validation failed. Found 2 error(s). Please check the 'invalidParams' property for details.");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalidParams");
        assertThat(invalidParams).hasSize(2);
        assertThat(invalidParams.get(0)).containsEntry("field", "field"); // Проверяем ошибку поля
        assertThat(invalidParams.get(1)).containsEntry("global", "object"); // Проверяем глобальную ошибку
    }

    @Test
    @DisplayName("handleTypeMismatch: должен вернуть 400 с деталями об ошибке в properties")
    void handleTypeMismatch_shouldReturnBadRequestWithDetailsInProperties() {
        // Arrange
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException("abc", Long.class, "taskId", null, null);
        String typeSuffix = "request.parameter.typeMismatch";
        setupMessageSource(typeSuffix, "Invalid Parameter Format", "Parameter {0} could not be converted to the required type {2} from the provided value {1}.", "taskId", "abc", "Long");

        // Act
        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleTypeMismatch(exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, mockServletWebRequest);

        // Assert
        ProblemDetail problemDetail = (ProblemDetail) responseEntity.getBody();
        assertProblemDetailBase(problemDetail, HttpStatus.BAD_REQUEST, "request/parameter-type-mismatch", TEST_REQUEST_URI);
        assertThat(problemDetail.getDetail()).isEqualTo("Parameter taskId could not be converted to the required type abc from the provided value Long.");
        assertThat(problemDetail.getProperties())
                .containsEntry("field", "taskId")
                .containsEntry("rejectedValue", "abc")
                .containsEntry("expectedType", "Long");
    }

    @Test
    @DisplayName("handleIllegalStateException: должен делегировать в handleInternalServerError и вернуть 500 с errorRef")
    void handleIllegalStateException_shouldDelegateAndReturn500withErrorRef() {
        // Arrange
        IllegalStateException exception = new IllegalStateException("Internal state error");
        String typeSuffix = "internal.illegalState";

        // Мокируем MessageSource для handleInternalServerError
        when(mockMessageSource.getMessage(eq("problemDetail." + typeSuffix + ".title"), isNull(), eq(TEST_LOCALE)))
                .thenReturn("Internal App State Error");
        // Ожидаем вызов с одним аргументом - errorRef (любая строка)
        when(mockMessageSource.getMessage(eq("problemDetail." + typeSuffix + ".detail"), any(Object[].class), eq(TEST_LOCALE)))
                .thenAnswer(invocation -> "An error occurred. Ref: " + ((Object[])invocation.getArgument(1))[0].toString());

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleIllegalStateException(exception, mockServletWebRequest);

        // Assert
        assertProblemDetailBase(problemDetail, HttpStatus.INTERNAL_SERVER_ERROR, typeSuffix.replace('.', '/'), TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).containsKey("errorRef");
        String errorRef = (String) problemDetail.getProperties().get("errorRef");
        assertThat(problemDetail.getDetail()).isEqualTo("An error occurred. Ref: " + errorRef);
    }

    @Test
    @DisplayName("extractTokenSnippetFromRequest: когда не ServletWebRequest, должен вернуть [non-http request]")
    void extractTokenSnippetFromRequest_whenNotServletWebRequest_shouldReturnNonHttpMarker() {
        String snippet = globalExceptionHandler.extractTokenSnippetFromRequest(mockWebRequest); // Не ServletWebRequest
        assertThat(snippet).isEqualTo("[non-http request]");
    }

    @Test
    @DisplayName("extractTokenSnippetFromRequest: когда заголовок Authorization отсутствует, должен вернуть маркер")
    void extractTokenSnippetFromRequest_whenAuthHeaderMissing_shouldReturnNoTokenMarker() {
        when(mockHttpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        String snippet = globalExceptionHandler.extractTokenSnippetFromRequest(mockServletWebRequest);
        assertThat(snippet).isEqualTo("[token not present or not bearer]");
    }

    @Test
    @DisplayName("extractTokenSnippetFromRequest: когда заголовок Authorization без 'Bearer ', должен вернуть маркер")
    void extractTokenSnippetFromRequest_whenAuthHeaderNotBearer_shouldReturnNoTokenMarker() {
        when(mockHttpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic somecredentials");
        String snippet = globalExceptionHandler.extractTokenSnippetFromRequest(mockServletWebRequest);
        assertThat(snippet).isEqualTo("[token not present or not bearer]");
    }

    @Test
    @DisplayName("getFieldNameFromPath: когда нет точек, должен вернуть исходный путь")
    void getFieldNameFromPath_whenNoDots_shouldReturnOriginalPath() {
        String path = "fieldName";
        String fieldName = globalExceptionHandler.getFieldNameFromPath(path);
        assertThat(fieldName).isEqualTo(path);
    }

    @Test
    @DisplayName("setInstanceUriIfAbsent: когда instance уже установлен, не должен его менять")
    void setInstanceUriIfAbsent_whenInstanceAlreadySet_shouldNotChangeIt() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.OK);
        URI preSetUri = URI.create("/preset/uri");
        pd.setInstance(preSetUri);

        globalExceptionHandler.setInstanceUriIfAbsent(pd, mockServletWebRequest);
        assertThat(pd.getInstance()).isSameAs(preSetUri);
    }

    @Test
    @DisplayName("setInstanceUriIfAbsent: когда request не ServletWebRequest, instance не устанавливается")
    void setInstanceUriIfAbsent_whenNotServletWebRequest_shouldNotSetInstance() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.OK);
        // Используем mockWebRequest, который не является ServletWebRequest
        globalExceptionHandler.setInstanceUriIfAbsent(pd, mockWebRequest);
        assertThat(pd.getInstance()).isNull();
    }

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