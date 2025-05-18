package com.example.tasktracker.backend.user.web;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.security.jwt.JwtIssuer;
import com.example.tasktracker.backend.security.jwt.JwtKeyService;
import com.example.tasktracker.backend.security.jwt.JwtProperties;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для {@link UserController} с использованием TestRestTemplate.
 * Запускают полный контекст Spring Boot и реальный веб-сервер на случайном порту.
 * Взаимодействуют с реальной БД через Testcontainers и реальным AuthService.
 */
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
    private JwtIssuer jwtIssuer;
    @Autowired
    private JwtProperties appJwtProperties;

    private String baseUsersUrl;
    private static final Locale TEST_LOCALE = Locale.ENGLISH;
    private static final String PROBLEM_TYPE_BASE_URI = "https://task-tracker.example.com/probs/"; // Как в ApiConstants


    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        baseUsersUrl = "http://localhost:" + port + ApiConstants.USERS_API_BASE_URL;
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    // --- Вспомогательные методы ---

    private HttpEntity<RegisterRequest> createJsonRequestEntityForRegister(RegisterRequest payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    private User createAndSaveTestUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.saveAndFlush(user);
    }

    private String generateJwtForUser(User user) {
        AppUserDetails appUserDetails = new AppUserDetails(user);
        Authentication authentication =
                new TestingAuthenticationToken(appUserDetails, null, appUserDetails.getAuthorities());
        return jwtIssuer.generateToken(authentication);
    }

    // --- Тесты для эндпоинта /register ---

    @Test
    @DisplayName("POST /register : Валидный запрос -> должен создать пользователя и вернуть 201 Created с токеном")
    void registerUser_whenRequestIsValid_shouldCreateUserAndReturnCreatedWithToken() {
        // Arrange
        RegisterRequest validRegisterRequestDto = new RegisterRequest("test@example.com", "password123", "password123");
        String registerUrl = baseUsersUrl + "/register";
        HttpEntity<RegisterRequest> requestEntity = createJsonRequestEntityForRegister(validRegisterRequestDto);

        // Act
        ResponseEntity<AuthResponse> responseEntity = testRestTemplate.postForEntity(
                registerUrl, requestEntity, AuthResponse.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseEntity.getHeaders().getLocation().toString()).endsWith(ApiConstants.USERS_BASE_PATH + "/me");
        assertThat(responseEntity.getHeaders().getFirst(ApiConstants.X_ACCESS_TOKEN_HEADER)).isNotNull().isNotBlank();

        AuthResponse authResponse = responseEntity.getBody();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isEqualTo(responseEntity.getHeaders().getFirst(ApiConstants.X_ACCESS_TOKEN_HEADER));
        assertThat(authResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(authResponse.getExpiresIn()).isPositive();

        Optional<User> savedUserOptional = userRepository.findByEmail(validRegisterRequestDto.getEmail());
        assertThat(savedUserOptional).isPresent();
        User savedUser = savedUserOptional.get();
        assertThat(savedUser.getEmail()).isEqualTo(validRegisterRequestDto.getEmail());
        assertThat(passwordEncoder.matches(validRegisterRequestDto.getPassword(), savedUser.getPassword())).isTrue();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    static Stream<Arguments> invalidRegisterRequestsSource() {
        return Stream.of(
                Arguments.of(new RegisterRequest("", "password123", "password123"), "email", "Email address must not be blank."),
                Arguments.of(new RegisterRequest("not-an-email", "password123", "password123"), "email", "Email address must be a valid format (e.g., user@example.com)."),
                Arguments.of(new RegisterRequest("a".repeat(260) + "@example.com", "password123", "password123"), "email", "Email address length must be between 0 and 255 characters."),
                Arguments.of(new RegisterRequest("test@example.com", "", "password123"), "password", "Password must not be blank."),
                Arguments.of(new RegisterRequest("test@example.com", "password123", ""), "repeatPassword", "Password confirmation must not be blank.")
        );
    }

    @ParameterizedTest(name = "POST /register : Невалидный DTO (поле {1}, ожидаем \"{2}\") -> должен вернуть 400 Bad Request")
    @MethodSource("invalidRegisterRequestsSource")
    @DisplayName("POST /register : Невалидный DTO -> должен вернуть 400 Bad Request с ProblemDetail")
    void registerUser_whenRequestDtoIsInvalid_shouldReturnBadRequestWithProblemDetail(
            RegisterRequest invalidRequest, String expectedInvalidField, String expectedValidationMessage) {
        // Arrange
        String registerUrl = baseUsersUrl + "/register";
        HttpEntity<RegisterRequest> requestEntity = createJsonRequestEntityForRegister(invalidRequest);
        String expectedProblemTitle = messageSource.getMessage("problemDetail.validation.methodArgumentNotValid.title", null, TEST_LOCALE);

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                registerUrl, HttpMethod.POST, requestEntity, ProblemDetail.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "validation/methodArgumentNotValid"));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedProblemTitle);
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create(ApiConstants.USERS_API_BASE_URL + "/register"));


        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).isNotNull();

        boolean fieldErrorFound = invalidParams.stream().anyMatch(errorMap ->
                expectedInvalidField.equals(errorMap.get("field")) &&
                        expectedValidationMessage.equals(errorMap.get("message"))
        );
        assertThat(fieldErrorFound)
                .as("Expected validation error for field '%s' with message '%s' was not found in invalid_params: %s",
                        expectedInvalidField, expectedValidationMessage, invalidParams)
                .isTrue();
        assertThat(userRepository.count()).isZero();
    }


    @Test
    @DisplayName("POST /register : Пароли не совпадают (ошибка из сервиса) -> должен вернуть 400 Bad Request с ProblemDetail")
    void registerUser_whenPasswordsDoNotMatchInService_shouldReturnBadRequestWithProblemDetail() {
        // Arrange
        RegisterRequest requestWithMismatch = new RegisterRequest("mismatch@example.com", "password123", "password456");
        String registerUrl = baseUsersUrl + "/register";
        HttpEntity<RegisterRequest> requestEntity = createJsonRequestEntityForRegister(requestWithMismatch);

        String expectedTitle = messageSource.getMessage("problemDetail.user.passwordMismatch.title", null, TEST_LOCALE);
        String expectedDetail = messageSource.getMessage("problemDetail.user.passwordMismatch.detail", null, TEST_LOCALE);

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(
                registerUrl, requestEntity, ProblemDetail.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "user/password-mismatch"));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create(ApiConstants.USERS_API_BASE_URL + "/register"));
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /register : Email уже существует (ошибка из сервиса) -> должен вернуть 409 Conflict с ProblemDetail")
    void registerUser_whenEmailAlreadyExistsInService_shouldReturnConflictWithProblemDetail() {
        // Arrange
        String existingEmail = "existing@example.com";
        createAndSaveTestUser(existingEmail, "someOtherPassword");

        RegisterRequest requestForExistingEmail = new RegisterRequest(existingEmail, "password123", "password123");
        String registerUrl = baseUsersUrl + "/register";
        HttpEntity<RegisterRequest> requestEntity = createJsonRequestEntityForRegister(requestForExistingEmail);

        String expectedTitle = messageSource.getMessage("problemDetail.user.alreadyExists.title", null, TEST_LOCALE);
        String expectedDetail = messageSource.getMessage("problemDetail.user.alreadyExists.detail", new Object[]{existingEmail}, TEST_LOCALE);

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(
                registerUrl, requestEntity, ProblemDetail.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "user/already-exists"));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create(ApiConstants.USERS_API_BASE_URL + "/register"));
        assertThat(problemDetail.getProperties()).containsEntry("conflicting_email", existingEmail);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    // --- Тесты для эндпоинта /me ---

    @Test
    @DisplayName("GET /users/me: Валидный JWT -> должен вернуть 200 OK с UserResponse")
    void getCurrentUser_whenRequestWithValidJwt_shouldReturnOkWithUserResponse() {
        // Arrange
        User user = createAndSaveTestUser("me_user@example.com", "me_password");
        String jwtToken = generateJwtForUser(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        String meUrl = baseUsersUrl + "/me";

        // Act
        ResponseEntity<UserResponse> responseEntity = testRestTemplate.exchange(
                meUrl,
                HttpMethod.GET,
                entity,
                UserResponse.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse userResponse = responseEntity.getBody();
        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getId()).isEqualTo(user.getId());
        assertThat(userResponse.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("GET /users/me: Отсутствует JWT -> должен вернуть 401 Unauthorized")
    void getCurrentUser_whenRequestWithoutJwt_shouldReturnUnauthorized() {
        // Arrange
        String meUrl = baseUsersUrl + "/me";

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                meUrl,
                HttpMethod.GET,
                null,
                ProblemDetail.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getHeaders()
                .getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"task-tracker\"");
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "unauthorized"));
        assertThat(problemDetail.getTitle()).isEqualTo(messageSource.getMessage("problemDetail.unauthorized.title",
                null, TEST_LOCALE));
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create(ApiConstants.USERS_API_BASE_URL + "/me"));
    }

    @Test
    @DisplayName("GET /users/me: JWT с неверной подписью -> должен вернуть 401 Unauthorized с ProblemDetail")
    void getCurrentUser_whenJwtHasInvalidSignature_shouldReturnUnauthorizedWithInvalidSignatureProblem() {
        // Arrange
        // 1. Создаем пользователя, для которого будем пытаться использовать токен
        User user = createAndSaveTestUser("signature_test@example.com", "password");

        // 2. Генерируем токен с ДРУГИМ, НЕПРАВИЛЬНЫМ ключом
        JwtProperties wrongKeyProps = new JwtProperties();
        // Важно: этот Base64 ключ должен быть ДРУГИМ, чем тот, что используется в application-ci.yml
        // (и который используется инжектированным jwtKeyService/jwtIssuer в приложении)
        // Пример: "anotherTestSecretKeyForTaskTrackerApp123" -> "YW5vdGhlclRlc3RTZWNyZXRLZXlGb3JUYXNrVHJhY2tlckFwcDEyMw=="
        wrongKeyProps.setSecretKey("YW5vdGhlclRlc3RTZWNyZXRLZXlGb3JUYXNrVHJhY2tlckFwcDEyMw==");
        wrongKeyProps.setExpirationMs(3600000L); // 1 час
        JwtKeyService wrongJwtKeyService = new JwtKeyService(wrongKeyProps);
        JwtIssuer wrongKeyIssuer = new JwtIssuer(wrongKeyProps, wrongJwtKeyService, Clock.systemUTC()); // Используем системные часы для генерации

        AppUserDetails appUserDetailsForWrongKey = new AppUserDetails(user);
        Authentication authForWrongKey =
                new TestingAuthenticationToken(appUserDetailsForWrongKey, null, appUserDetailsForWrongKey.getAuthorities());
        String tokenWithWrongSignature = wrongKeyIssuer.generateToken(authForWrongKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenWithWrongSignature);
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String meUrl = baseUsersUrl + "/me";

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                meUrl, HttpMethod.GET, entity, ProblemDetail.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer realm=\"task-tracker\"");

        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "jwt/invalid_signature"));
        assertThat(problemDetail.getTitle()).isEqualTo(messageSource.getMessage("problemDetail.jwt.invalid_signature.title", null, TEST_LOCALE));
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create(ApiConstants.USERS_API_BASE_URL + "/me"));
        assertThat(problemDetail.getProperties()).containsKey("error_type");
        assertThat(problemDetail.getProperties().get("error_type")).isEqualTo("INVALID_SIGNATURE"); // JwtErrorType.INVALID_SIGNATURE.name()
    }

    @Test
    @DisplayName("GET /users/me: Структурно неверный (malformed) JWT -> должен вернуть 401 Unauthorized с ProblemDetail")
    void getCurrentUser_whenJwtIsMalformed_shouldReturnUnauthorizedWithMalformedProblem() {
        // Arrange
        String malformedToken = "this.is.not.a.jwt"; // Явно невалидный формат

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(malformedToken);
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String meUrl = baseUsersUrl + "/me";

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                meUrl, HttpMethod.GET, entity, ProblemDetail.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer realm=\"task-tracker\"");

        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "jwt/malformed"));
        assertThat(problemDetail.getTitle()).isEqualTo(messageSource.getMessage("problemDetail.jwt.malformed.title", null, TEST_LOCALE));
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create(ApiConstants.USERS_API_BASE_URL + "/me"));
        assertThat(problemDetail.getProperties()).containsKey("error_type");
        assertThat(problemDetail.getProperties().get("error_type")).isEqualTo("MALFORMED"); // JwtErrorType.MALFORMED.name()
    }

    @Test
    @DisplayName("GET /users/me: Просроченный JWT -> должен вернуть 401 Unauthorized с ProblemDetail")
    void getCurrentUser_whenJwtIsExpired_shouldReturnUnauthorizedWithExpiredProblem() {
        // Arrange
        User user = createAndSaveTestUser("expired_user@example.com", "password");
        Authentication authentication = new TestingAuthenticationToken(
                new AppUserDetails(user), null, Collections.emptyList()
        );

        // 1. Определяем время: "сейчас" для валидатора и "в прошлом" для генерации токена
        Instant validatorNow = Instant.now(Clock.systemUTC()); // Время, которое будет у валидатора в приложении
        Instant tokenIssueTime = validatorNow.minus(Duration.ofHours(1)); // Токен был выдан час назад
        long tokenLifetimeMs = 10_000L; // Токен жил 10 секунд

        // 2. Настраиваем свойства и компоненты для генерации просроченного токена
        JwtProperties expiredTokenProps = new JwtProperties();
        expiredTokenProps.setSecretKey(appJwtProperties.getSecretKey()); // Ключ из конфигурации приложения
        expiredTokenProps.setExpirationMs(tokenLifetimeMs);
        expiredTokenProps.setEmailClaimKey(appJwtProperties.getEmailClaimKey());
        expiredTokenProps.setAuthoritiesClaimKey(appJwtProperties.getAuthoritiesClaimKey());

        JwtKeyService keyServiceForExpired = new JwtKeyService(expiredTokenProps);
        JwtIssuer issuerForExpiredToken = new JwtIssuer(
                expiredTokenProps,
                keyServiceForExpired,
                Clock.fixed(tokenIssueTime, ZoneOffset.UTC) // Фиксируем время выдачи токена в прошлом
        );

        String expiredToken = issuerForExpiredToken.generateToken(authentication);
        // Токен выдан в tokenIssueTime и истек через tokenLifetimeMs (т.е. задолго до validatorNow)

        // 3. Формируем запрос
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String meUrl = baseUsersUrl + "/me";

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                meUrl, HttpMethod.GET, entity, ProblemDetail.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer realm=\"task-tracker\"");

        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "jwt/expired"));
        assertThat(problemDetail.getTitle()).isEqualTo(messageSource.getMessage("problemDetail.jwt.expired.title", null, TEST_LOCALE));
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getInstance()).isEqualTo(URI.create(ApiConstants.USERS_API_BASE_URL + "/me"));
        assertThat(problemDetail.getProperties()).containsEntry("error_type", "EXPIRED");
    }
}