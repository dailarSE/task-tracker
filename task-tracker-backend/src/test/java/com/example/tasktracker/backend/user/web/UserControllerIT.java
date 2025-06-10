package com.example.tasktracker.backend.user.web;

import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.test.util.TestJwtUtil;
import com.example.tasktracker.backend.user.dto.UserResponse;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import com.example.tasktracker.backend.web.ApiConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.MessageSource;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci")
class UserControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:17.4-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties appJwtProperties;

    @Autowired
    private Clock appClock;

    private String baseUsersUrl;
    private TestJwtUtil testJwtUtil;
    private static final Locale TEST_LOCALE = Locale.ENGLISH;


    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        baseUsersUrl = "http://localhost:" + port + ApiConstants.USERS_API_BASE_URL;
        testJwtUtil = new TestJwtUtil(appJwtProperties, appClock);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    private <T> HttpEntity<T> createHttpEntity(@Nullable T body, @Nullable String jwtToken) {
        HttpHeaders headers = new HttpHeaders();
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (jwtToken != null) {
            headers.setBearerAuth(jwtToken);
        }
        return new HttpEntity<>(body, headers);
    }

    private User createAndSaveTestUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.saveAndFlush(user);
    }

    private void assertProblemDetailBase(ResponseEntity<ProblemDetail> responseEntity, HttpStatus expectedStatus, String expectedTypeUriPath, String expectedTitle, String expectedInstanceSuffix) {
        assertThat(responseEntity.getStatusCode()).isEqualTo(expectedStatus);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + expectedTypeUriPath));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(problemDetail.getInstance().toString()).endsWith(expectedInstanceSuffix);
    }

    private void assertUnauthorizedProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, String expectedJwtErrorType) {
        String titleKey = "problemDetail.jwt." + expectedJwtErrorType.toLowerCase() + ".title";
        String expectedTitle = messageSource.getMessage(titleKey, null, TEST_LOCALE);

        assertProblemDetailBase(responseEntity, HttpStatus.UNAUTHORIZED,
                "jwt/" + expectedJwtErrorType.toLowerCase(),
                expectedTitle,
                expectedInstanceSuffix);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer realm=\"task-tracker\"");
    }

    private void assertGeneralUnauthorizedProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix) {
        String expectedTitle = messageSource.getMessage("problemDetail.unauthorized.title", null, TEST_LOCALE);
        assertProblemDetailBase(responseEntity, HttpStatus.UNAUTHORIZED,
                "unauthorized",
                expectedTitle,
                expectedInstanceSuffix);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"task-tracker\"");
    }

    private void assertValidationProblemDetail(
            ResponseEntity<ProblemDetail> responseEntity,
            String expectedInstanceSuffix,
            String expectedInvalidField) {

        String expectedProblemTitle = messageSource.getMessage("problemDetail.validation.methodArgumentNotValid.title", null, TEST_LOCALE);
        assertProblemDetailBase(responseEntity, HttpStatus.BAD_REQUEST,
                "validation/method-argument-not-valid",
                expectedProblemTitle,
                expectedInstanceSuffix);

        ProblemDetail problemDetail = responseEntity.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalidParams");
        assertThat(invalidParams).isNotNull();

        boolean fieldErrorFound = invalidParams.stream().anyMatch(errorMap ->
                expectedInvalidField.equals(errorMap.get("field")) &&
                        errorMap.containsKey("message") &&
                        errorMap.get("message") instanceof String &&
                        !((String) errorMap.get("message")).isEmpty() // Убедимся, что сообщение не пустое
        );

        assertThat(fieldErrorFound)
                .as("Expected validation error for field '%s' was not found in invalid_params, or its message was missing/empty: %s",
                        expectedInvalidField, invalidParams)
                .isTrue();
    }

    @Test
    @DisplayName("POST /register : Валидный запрос -> должен создать пользователя и вернуть 201 Created с токеном")
    void registerUser_whenRequestIsValid_shouldCreateUserAndReturnCreatedWithToken() {
        RegisterRequest validRegisterRequestDto = new RegisterRequest("test@example.com", "password123", "password123");
        String registerUrl = baseUsersUrl + "/register";
        HttpEntity<RegisterRequest> requestEntity = createHttpEntity(validRegisterRequestDto, null);

        ResponseEntity<AuthResponse> responseEntity = testRestTemplate.postForEntity(
                registerUrl, requestEntity, AuthResponse.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseEntity.getHeaders().getLocation().toString()).endsWith(ApiConstants.USERS_API_BASE_URL + "/me");
        assertThat(userRepository.findByEmail(validRegisterRequestDto.getEmail())).isPresent();
    }

    static Stream<Arguments> invalidRegisterRequestsSource() {
        return Stream.of(
                Arguments.of(new RegisterRequest("", "password123", "password123"), "email"),
                Arguments.of(new RegisterRequest("not-an-email", "password123", "password123"), "email"),
                Arguments.of(new RegisterRequest("a".repeat(260) + "@example.com", "password123", "password123"), "email"),
                Arguments.of(new RegisterRequest("test@example.com", "", "password123"), "password"),
                Arguments.of(new RegisterRequest("test@example.com", "p".repeat(256), "p".repeat(256)), "password"),
                Arguments.of(new RegisterRequest("test@example.com", "password123", ""), "repeatPassword")
        );
    }

    @ParameterizedTest(name = "POST /register : Невалидный DTO (поле {1}) -> должен вернуть 400 Bad Request")
    @MethodSource("invalidRegisterRequestsSource")
    @DisplayName("POST /register : Невалидный DTO -> должен вернуть 400 Bad Request с ProblemDetail")
    void registerUser_whenRequestDtoIsInvalid_shouldReturnBadRequestWithProblemDetail(
            RegisterRequest invalidRequest, String expectedInvalidField) {
        String registerUrl = baseUsersUrl + "/register";
        HttpEntity<RegisterRequest> requestEntity = createHttpEntity(invalidRequest, null);

        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                registerUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);

        assertValidationProblemDetail(responseEntity, ApiConstants.USERS_API_BASE_URL + "/register", expectedInvalidField);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /register : Пароли не совпадают (ошибка из сервиса) -> должен вернуть 400 Bad Request")
    void registerUser_whenPasswordsDoNotMatchInService_shouldReturnBadRequest() {
        RegisterRequest requestWithMismatch = new RegisterRequest("mismatch@example.com", "password123", "password456");
        String registerUrl = baseUsersUrl + "/register";
        HttpEntity<RegisterRequest> requestEntity = createHttpEntity(requestWithMismatch, null);
        String expectedTitle = messageSource.getMessage("problemDetail.user.passwordMismatch.title", null, TEST_LOCALE);

        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(
                registerUrl, requestEntity, ProblemDetail.class);

        assertProblemDetailBase(responseEntity, HttpStatus.BAD_REQUEST,
                "user/password-mismatch",
                expectedTitle,
                ApiConstants.USERS_API_BASE_URL + "/register");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /register : Email уже существует (ошибка из сервиса) -> должен вернуть 409 Conflict")
    void registerUser_whenEmailAlreadyExistsInService_shouldReturnConflict() {
        String existingEmail = "existing@example.com";
        createAndSaveTestUser(existingEmail, "someOtherPassword");
        RegisterRequest requestForExistingEmail = new RegisterRequest(existingEmail, "password123", "password123");
        String registerUrl = baseUsersUrl + "/register";
        HttpEntity<RegisterRequest> requestEntity = createHttpEntity(requestForExistingEmail, null);
        String expectedTitle = messageSource.getMessage("problemDetail.user.alreadyExists.title", null, TEST_LOCALE);

        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(
                registerUrl, requestEntity, ProblemDetail.class);

        assertProblemDetailBase(responseEntity, HttpStatus.CONFLICT,
                "user/already-exists",
                expectedTitle,
                ApiConstants.USERS_API_BASE_URL + "/register");
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail.getProperties()).containsEntry("conflictingEmail", existingEmail);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /users/me: Валидный JWT -> должен вернуть 200 OK с UserResponse")
    void getCurrentUser_whenRequestWithValidJwt_shouldReturnOkWithUserResponse() {
        User user = createAndSaveTestUser("me_user@example.com", "me_password");
        String jwtToken = testJwtUtil.generateValidToken(user);
        HttpEntity<Void> entity = createHttpEntity(null, jwtToken);
        String meUrl = baseUsersUrl + "/me";

        ResponseEntity<UserResponse> responseEntity = testRestTemplate.exchange(
                meUrl, HttpMethod.GET, entity, UserResponse.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse userResponse = responseEntity.getBody();
        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getId()).isEqualTo(user.getId());
        assertThat(userResponse.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("GET /users/me: Отсутствует JWT -> должен вернуть 401 Unauthorized")
    void getCurrentUser_whenRequestWithoutJwt_shouldReturnUnauthorized() {
        String meUrl = baseUsersUrl + "/me";
        HttpEntity<Void> entity = createHttpEntity(null, null);
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                meUrl, HttpMethod.GET, entity, ProblemDetail.class);
        assertGeneralUnauthorizedProblemDetail(responseEntity, ApiConstants.USERS_API_BASE_URL + "/me");
    }

    @Test
    @DisplayName("GET /users/me: JWT с неверной подписью -> должен вернуть 401 Unauthorized")
    void getCurrentUser_whenJwtHasInvalidSignature_shouldReturnUnauthorizedWithInvalidSignatureProblem() {
        User user = createAndSaveTestUser("signature_test@example.com", "password");
        JwtProperties wrongKeyProps = new JwtProperties();
        wrongKeyProps.setSecretKey("YW5vdGhlclRlc3RTZWNyZXRLZXlGb3JUYXNrVHJhY2tlckFwcDEyMw==");
        wrongKeyProps.setExpirationMs(appJwtProperties.getExpirationMs());
        wrongKeyProps.setEmailClaimKey(appJwtProperties.getEmailClaimKey());
        wrongKeyProps.setAuthoritiesClaimKey(appJwtProperties.getAuthoritiesClaimKey());
        TestJwtUtil wrongKeyUtil = new TestJwtUtil(wrongKeyProps, appClock);
        String tokenWithWrongSignature = wrongKeyUtil.generateValidToken(user);

        HttpEntity<Void> entity = createHttpEntity(null, tokenWithWrongSignature);
        String meUrl = baseUsersUrl + "/me";
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                meUrl, HttpMethod.GET, entity, ProblemDetail.class);
        assertUnauthorizedProblemDetail(responseEntity, ApiConstants.USERS_API_BASE_URL + "/me", "INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("GET /users/me: Структурно неверный (malformed) JWT -> должен вернуть 401 Unauthorized")
    void getCurrentUser_whenJwtIsMalformed_shouldReturnUnauthorizedWithMalformedProblem() {
        String malformedToken = "this.is.not.a.jwt";
        HttpEntity<Void> entity = createHttpEntity(null, malformedToken);
        String meUrl = baseUsersUrl + "/me";
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                meUrl, HttpMethod.GET, entity, ProblemDetail.class);
        assertUnauthorizedProblemDetail(responseEntity, ApiConstants.USERS_API_BASE_URL + "/me", "MALFORMED");
    }

    @Test
    @DisplayName("GET /users/me: Просроченный JWT -> должен вернуть 401 Unauthorized")
    void getCurrentUser_whenJwtIsExpired_shouldReturnUnauthorizedWithExpiredProblem() {
        User user = createAndSaveTestUser("expired_user@example.com", "password");
        String expiredToken = testJwtUtil.generateExpiredToken(user, Duration.ofSeconds(1), Duration.ofSeconds(5));
        HttpEntity<Void> entity = createHttpEntity(null, expiredToken);
        String meUrl = baseUsersUrl + "/me";
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                meUrl, HttpMethod.GET, entity, ProblemDetail.class);
        assertUnauthorizedProblemDetail(responseEntity, ApiConstants.USERS_API_BASE_URL + "/me", "EXPIRED");
    }
}