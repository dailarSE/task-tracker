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
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
    private static final String TEST_REQUEST_URI = "/test/path";

    @BeforeEach
    void setUp() {
        // Общая настройка для локали
        when(mockWebRequest.getLocale()).thenReturn(TEST_LOCALE);
        when(mockServletWebRequest.getLocale()).thenReturn(TEST_LOCALE);
        // Для setInstanceUriIfAbsent и extractTokenSnippetFromRequest
        when(mockServletWebRequest.getRequest()).thenReturn(mockHttpServletRequest);
        when(mockServletWebRequest.getResponse()).thenReturn(mockHttpResponse);
        when(mockHttpServletRequest.getRequestURI()).thenReturn(TEST_REQUEST_URI); // Пример URI для instance

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

    private void assertProblemDetail(ProblemDetail actual, HttpStatus expectedStatus, String expectedTypeSuffix,
                                     String expectedTitle, String expectedDetail, String expectedInstancePath) {
        assertThat(actual.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(actual.getType()).isEqualTo(URI.create(BASE_PROBLEM_URI + expectedTypeSuffix.replaceAll("\\.", "/")));
        assertThat(actual.getTitle()).isEqualTo(expectedTitle);
        assertThat(actual.getDetail()).isEqualTo(expectedDetail);
        if (expectedInstancePath != null) {
            assertThat(actual.getInstance()).isEqualTo(URI.create(expectedInstancePath));
        } else {
            assertThat(actual.getInstance()).isNull();
        }
    }

    @ParameterizedTest
    @EnumSource(JwtErrorType.class)
    @DisplayName("handleBadJwtException: должен вернуть 401 ProblemDetail с корректными properties")
    void handleBadJwtException_shouldReturnCorrectUnauthorizedProblemDetail(JwtErrorType errorType) {
        // Arrange
        String originalErrorMessage = "JWT Error: " + errorType.name();
        BadJwtException exception = new BadJwtException(originalErrorMessage, errorType, new RuntimeException("Root cause"));
        String typeSuffix = "jwt." + errorType.name().toLowerCase();
        setupMessageSourceForStaticMessages(typeSuffix, "JWT Title", "JWT Detail");
        when(mockServletWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer short");

        // Act
        ProblemDetail problemDetail = globalExceptionHandler.handleBadJwtException(exception, mockServletWebRequest);

        // Assert
        assertProblemDetail(problemDetail, HttpStatus.UNAUTHORIZED, typeSuffix, "JWT Title", "JWT Detail", TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).containsEntry("error_type", errorType.name());
        assertThat(problemDetail.getProperties()).containsEntry("jwt_error_details", originalErrorMessage);
    }

    @Test
    @DisplayName("handleBadJwtException: когда ex.getMessage() is null, jwt_error_details не должен добавляться")
    void handleBadJwtException_whenMessageIsNull_shouldNotAddJwtErrorDetails() {
        BadJwtException exception = new BadJwtException(null, JwtErrorType.EXPIRED, new RuntimeException("Root cause"));
        String typeSuffix = "jwt." + JwtErrorType.EXPIRED.name().toLowerCase();
        setupMessageSourceForStaticMessages(typeSuffix, "JWT Title", "JWT Detail");
        when(mockServletWebRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer short");

        ProblemDetail problemDetail = globalExceptionHandler.handleBadJwtException(exception, mockServletWebRequest);

        assertProblemDetail(problemDetail, HttpStatus.UNAUTHORIZED, typeSuffix, "JWT Title", "JWT Detail", TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).containsEntry("error_type", JwtErrorType.EXPIRED.name());
        assertThat(problemDetail.getProperties()).doesNotContainKey("jwt_error_details");
    }

    @Test
    @DisplayName("handleAuthenticationException (общий): должен вернуть 401 ProblemDetail")
    void handleAuthenticationException_whenGenericAuthError_shouldReturnCorrectUnauthorizedProblemDetail() {
        String originalErrorMessage = "Generic authentication error";
        AuthenticationException exception = new AuthenticationException(originalErrorMessage) {};
        String typeSuffix = "unauthorized";
        setupMessageSourceForStaticMessages(typeSuffix, "Auth Required Title", "Auth Required Detail");

        ProblemDetail problemDetail = globalExceptionHandler.handleAuthenticationException(exception, mockServletWebRequest);

        assertProblemDetail(problemDetail, HttpStatus.UNAUTHORIZED, typeSuffix, "Auth Required Title", "Auth Required Detail", TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).containsEntry("auth_error_details", originalErrorMessage);
    }

    @Test
    @DisplayName("handleAuthenticationException: когда ex.getMessage() is null, auth_error_details не должен добавляться")
    void handleAuthenticationException_whenMessageIsNull_shouldNotAddAuthErrorDetails() {
        AuthenticationException exception = new AuthenticationException(null) {}; // Сообщение null
        String typeSuffix = "unauthorized";
        setupMessageSourceForStaticMessages(typeSuffix, "Auth Required Title", "Auth Required Detail");

        ProblemDetail problemDetail = globalExceptionHandler.handleAuthenticationException(exception, mockServletWebRequest);

        assertProblemDetail(problemDetail, HttpStatus.UNAUTHORIZED, typeSuffix, "Auth Required Title", "Auth Required Detail", TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).isNull(); // Или doesNotContainKey, если Map создается всегда
    }

    @Test
    @DisplayName("handleBadCredentialsException: должен вернуть 401 ProblemDetail с WWW-Authenticate")
    void handleBadCredentialsException_shouldReturnUnauthorizedWithWwwAuth() {
        String originalErrorMessage = "Bad credentials provided";
        BadCredentialsException exception = new BadCredentialsException(originalErrorMessage);
        String typeSuffix = "auth.invalidCredentials";
        setupMessageSourceForStaticMessages(typeSuffix, "Invalid Credentials Title", "Invalid Credentials Detail");

        ProblemDetail problemDetail = globalExceptionHandler.handleBadCredentialsException(exception, mockServletWebRequest);

        assertProblemDetail(problemDetail, HttpStatus.UNAUTHORIZED, typeSuffix, "Invalid Credentials Title", "Invalid Credentials Detail", TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).containsEntry("login_error_details", originalErrorMessage);
        verify(mockHttpResponse).setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"task-tracker\"");
    }

    @Test
    @DisplayName("handleBadCredentialsException: когда getResponse() is null (маловероятно), заголовок не устанавливается")
    void handleBadCredentialsException_whenGetResponseIsNull_shouldNotSetHeader() {
        BadCredentialsException exception = new BadCredentialsException("Test");
        // Настроим mockServletWebRequest так, чтобы getResponse() возвращал null
        when(mockServletWebRequest.getResponse()).thenReturn(null);
        setupMessageSourceForStaticMessages("auth.invalidCredentials", "T", "D");

        globalExceptionHandler.handleBadCredentialsException(exception, mockServletWebRequest);

        verify(mockHttpResponse, never()).setHeader(anyString(), anyString()); // Убедимся, что setHeader не вызывался
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
    @DisplayName("handleAccessDeniedException: когда userPrincipal is null, используется 'anonymous'")
    void handleAccessDeniedException_whenUserPrincipalIsNull_shouldLogAnonymous() {
        AccessDeniedException exception = new AccessDeniedException("Access Denied");
        when(mockServletWebRequest.getUserPrincipal()).thenReturn(null); // No principal
        setupMessageSourceForStaticMessages("forbidden", "T", "D");

        globalExceptionHandler.handleAccessDeniedException(exception, mockServletWebRequest);
        // Проверка логгирования (сложно юнит-тестировать, но поведение важно)
        // Можно было бы использовать LogCaptor, если бы это было критично
    }

    @Test
    @DisplayName("handleAccessDeniedException: когда request не ServletWebRequest, requestUri будет 'N/A'")
    void handleAccessDeniedException_whenNotServletWebRequest_shouldHaveNARequestUriInLog() {
        AccessDeniedException exception = new AccessDeniedException("Access Denied");
        // Используем mockWebRequest, который не является ServletWebRequest
        setupMessageSourceForStaticMessages("forbidden", "T", "D");

        globalExceptionHandler.handleAccessDeniedException(exception, mockWebRequest);
        // Лог должен содержать "N/A" для URI, проверяется визуально или через LogCaptor
    }

    @Test
    @DisplayName("handleConstraintViolationException: должен вернуть 400 ProblemDetail с invalid_params")
    void handleConstraintViolationException_shouldReturnBadRequestWithInvalidParams() {
        ConstraintViolation<?> mockViolation = mock(ConstraintViolation.class);
        Path mockPath = mock(Path.class);
        when(mockPath.toString()).thenReturn("some.field");
        when(mockViolation.getPropertyPath()).thenReturn(mockPath);
        when(mockViolation.getMessage()).thenReturn("must not be blank");
        Set<ConstraintViolation<?>> violations = Set.of(mockViolation);
        ConstraintViolationException exception = new ConstraintViolationException("Validation failed", violations);
        String typeSuffix = "validation.constraintViolation";
        setupMessageSourceForStaticMessages(typeSuffix, "Validation Error Title", "Validation Error Detail");

        ProblemDetail problemDetail = globalExceptionHandler.handleConstraintViolationException(exception, mockServletWebRequest);

        assertProblemDetail(problemDetail, HttpStatus.BAD_REQUEST, typeSuffix, "Validation Error Title", "Validation Error Detail", TEST_REQUEST_URI);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> invalidParams = (List<Map<String, String>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).hasSize(1);
        assertThat(invalidParams.get(0)).containsEntry("field", "field").containsEntry("message", "must not be blank");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: должен вернуть 400 ResponseEntity с ProblemDetail")
    void handleMethodArgumentNotValid_shouldReturnBadRequestWithProblemDetail() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult mockBindingResult = mock(BindingResult.class);
        FieldError mockFieldError = new FieldError("object", "field", "value", false, null, null, "message");
        when(exception.getBindingResult()).thenReturn(mockBindingResult);
        when(mockBindingResult.getFieldErrors()).thenReturn(List.of(mockFieldError));
        when(exception.getErrorCount()).thenReturn(1);
        String typeSuffix = "validation.methodArgumentNotValid";
        setupMessageSourceForStaticMessages(typeSuffix, "Invalid Arg Title", "Invalid Arg Detail");

        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleMethodArgumentNotValid(
                exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, mockServletWebRequest);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = (ProblemDetail) responseEntity.getBody();
        assertProblemDetail(problemDetail, HttpStatus.BAD_REQUEST, typeSuffix, "Invalid Arg Title", "Invalid Arg Detail", TEST_REQUEST_URI);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).hasSize(1);
        assertThat(invalidParams.get(0)).containsEntry("field", "field").containsEntry("message", "message");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: когда getRejectedValue is null, rejected_value должен быть 'null'")
    void handleMethodArgumentNotValid_whenRejectedValueIsNull_shouldSetRejectedValueToNullString() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        // getRejectedValue() вернет null
        FieldError fieldError = new FieldError("object", "field", null, false, null, null, "msg");
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(fieldError));
        setupMessageSourceForStaticMessages("validation.methodArgumentNotValid", "T", "D");

        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleMethodArgumentNotValid(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mockServletWebRequest);
        ProblemDetail pd = (ProblemDetail) responseEntity.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) pd.getProperties().get("invalid_params");
        assertThat(invalidParams.get(0).get("rejected_value")).isEqualTo("null");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: когда getDefaultMessage is null, message должен быть пустой строкой")
    void handleMethodArgumentNotValid_whenDefaultMessageIsNull_shouldSetMessageToEmptyString() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        // getDefaultMessage() вернет null
        FieldError fieldError = new FieldError("object", "field", "val", false, null, null, null);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(fieldError));
        setupMessageSourceForStaticMessages("validation.methodArgumentNotValid", "T", "D");

        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleMethodArgumentNotValid(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mockServletWebRequest);
        ProblemDetail pd = (ProblemDetail) responseEntity.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) pd.getProperties().get("invalid_params");
        assertThat(invalidParams.get(0).get("message")).isEqualTo("");
    }

    @Test
    @DisplayName("handleHttpMessageConversionException: должен вернуть 400 ProblemDetail")
    void handleHttpMessageConversionException_shouldReturnBadRequest() {
        HttpMessageConversionException exception = new HttpMessageNotReadableException("Cannot read HTTP message", (HttpInputMessage) null);
        String typeSuffix = "request.body.conversionError";
        setupMessageSourceForStaticMessages(typeSuffix, "Conversion Error Title", "Conversion Error Detail");

        ProblemDetail problemDetail = globalExceptionHandler.handleHttpMessageConversionException(exception, mockServletWebRequest);

        assertProblemDetail(problemDetail, HttpStatus.BAD_REQUEST, typeSuffix, "Conversion Error Title", "Conversion Error Detail", TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).containsEntry("error_summary", "The request body could not be processed due to a conversion or formatting error.");
    }

    @Test
    @DisplayName("handleHttpMessageConversionException: когда request не ServletWebRequest, requestUriPath будет 'N/A'")
    void handleHttpMessageConversionException_whenNotServletWebRequest_shouldUseNAPath() {
        HttpMessageConversionException exception = new HttpMessageNotReadableException("Msg", (HttpInputMessage) null);
        String typeSuffix = "request.body.conversionError";
        setupMessageSourceForStaticMessages(typeSuffix, "T", "D");

        // Используем mockWebRequest, который не является ServletWebRequest
        ProblemDetail problemDetail = globalExceptionHandler.handleHttpMessageConversionException(exception, mockWebRequest);
        // Проверяем, что instance URI не установлен, так как getRequestURI() не может быть вызван
        assertThat(problemDetail.getInstance()).isNull();
    }


    @Test
    @DisplayName("handleHttpMessageNotReadable: должен делегировать и вернуть 400 ProblemDetail")
    void handleHttpMessageNotReadable_shouldDelegateAndReturnBadRequest() {
        // Мы не можем напрямую мокировать вызов handleHttpMessageConversionException изнутри,
        // но можем проверить, что результат такой же, как если бы мы вызвали его напрямую
        // с HttpMessageNotReadableException
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("Cannot parse JSON", (HttpInputMessage) null);
        String typeSuffix = "request.body.conversionError"; // так как делегирует
        setupMessageSourceForStaticMessages(typeSuffix, "Conversion Error Title", "Conversion Error Detail");

        ResponseEntity<Object> responseEntity = globalExceptionHandler.handleHttpMessageNotReadable(
                exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, mockServletWebRequest);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = (ProblemDetail) responseEntity.getBody();
        assertProblemDetail(problemDetail, HttpStatus.BAD_REQUEST, typeSuffix, "Conversion Error Title", "Conversion Error Detail", TEST_REQUEST_URI);
    }


    @Test
    @DisplayName("handleIllegalStateException: должен вернуть 500 ProblemDetail с error_ref")
    void handleIllegalStateException_shouldReturnInternalServerErrorWithRef() {
        IllegalStateException exception = new IllegalStateException("Something went very wrong");
        String typeSuffix = "internal.illegalState";
        setupMessageSourceForStaticMessages(typeSuffix, "Internal Error Title", "Internal Error Detail with ref");

        ProblemDetail problemDetail = globalExceptionHandler.handleIllegalStateException(exception, mockServletWebRequest);

        assertProblemDetail(problemDetail, HttpStatus.INTERNAL_SERVER_ERROR, typeSuffix, "Internal Error Title", "Internal Error Detail with ref", TEST_REQUEST_URI);
        assertThat(problemDetail.getProperties()).containsKey("error_ref");
        assertThat(problemDetail.getProperties().get("error_ref").toString()).matches("^[0-9a-fA-F-]{36}$"); // UUID format
    }


    @Test
    @DisplayName("buildProblemDetail: когда additionalProperties is null, properties не должны добавляться")
    void buildProblemDetail_whenAdditionalPropertiesNull_shouldNotSetProperties() {
        setupMessageSourceForStaticMessages("some.type", "Title", "Detail");
        ProblemDetail pd = globalExceptionHandler.buildProblemDetail(HttpStatus.OK, "some.type", TEST_LOCALE, null);
        assertThat(pd.getProperties()).isNullOrEmpty(); // В зависимости от реализации ProblemDetail.getProperties()
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