package com.example.tasktracker.backend.security.web.controller;

import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.LoginRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для {@link AuthController} с использованием TestRestTemplate.
 * Запускают полный контекст Spring Boot и реальный веб-сервер на случайном порту.
 * Взаимодействуют с реальной БД через Testcontainers и реальным AuthService.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci") // Используем ваш профиль "ci"
class AuthControllerIT {

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
    private PasswordEncoder passwordEncoder; // Нужен для создания пользователя в @BeforeEach

    @Autowired
    private MessageSource messageSource;

    private LoginRequest validLoginRequestDto;
    private String baseLoginUrl;
    private static final Locale TEST_LOCALE = Locale.ENGLISH;
    private static final String PROBLEM_TYPE_BASE_URI = ApiConstants.PROBLEM_TYPE_BASE_URI; // Используем из ApiConstants

    private static final String TEST_USER_EMAIL = "loginuser@example.com";
    private static final String TEST_USER_PASSWORD = "password123";


    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        // Создаем пользователя для тестов логина
        User user = new User();
        user.setEmail(TEST_USER_EMAIL);
        user.setPassword(passwordEncoder.encode(TEST_USER_PASSWORD));
        // createdAt/updatedAt будут установлены JPA Auditing
        userRepository.saveAndFlush(user); // Сохраняем и делаем flush

        validLoginRequestDto = new LoginRequest(TEST_USER_EMAIL, TEST_USER_PASSWORD);
        baseLoginUrl = "http://localhost:" + port + ApiConstants.AUTH_API_BASE_URL + "/login";
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    private HttpEntity<LoginRequest> createJsonRequestEntity(LoginRequest payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    @Test
    @DisplayName("POST /login : Валидные креды -> должен вернуть 200 OK с токеном")
    void loginUser_whenCredentialsAreValid_shouldReturnOkWithToken() {
        // Arrange
        HttpEntity<LoginRequest> requestEntity = createJsonRequestEntity(validLoginRequestDto);

        // Act
        ResponseEntity<AuthResponse> responseEntity = testRestTemplate.postForEntity(
                baseLoginUrl, requestEntity, AuthResponse.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getHeaders().getFirst(ApiConstants.X_ACCESS_TOKEN_HEADER)).isNotNull().isNotBlank();

        AuthResponse authResponse = responseEntity.getBody();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isEqualTo(responseEntity.getHeaders().getFirst(ApiConstants.X_ACCESS_TOKEN_HEADER));
        assertThat(authResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(authResponse.getExpiresIn()).isPositive();
    }

    // Провайдер аргументов для параметризованного теста на невалидные DTO для логина
    static Stream<Arguments> invalidLoginRequestsSource() {
        // Сообщения берутся из аннотаций DTO (предполагаем, что они используют ключи или прямые сообщения)
        return Stream.of(
                Arguments.of(new LoginRequest("", "password123"), "email", "Email address must not be blank."),
                Arguments.of(new LoginRequest("not-an-email", "password123"), "email", "Email address must be a valid format (e.g., user@example.com)."),
                Arguments.of(new LoginRequest(TEST_USER_EMAIL, ""), "password", "Password must not be blank.")
        );
    }

    @ParameterizedTest(name = "POST /login : Невалидный DTO ({1} - \"{2}\") -> должен вернуть 400 Bad Request")
    @MethodSource("invalidLoginRequestsSource")
    @DisplayName("POST /login : Невалидный DTO -> должен вернуть 400 Bad Request с ProblemDetail")
    void loginUser_whenRequestDtoIsInvalid_shouldReturnBadRequestWithProblemDetail(
            LoginRequest invalidRequest, String expectedInvalidField, String expectedValidationMessage) {

        HttpEntity<LoginRequest> requestEntity = createJsonRequestEntity(invalidRequest);
        String expectedProblemTitle = messageSource.getMessage("problemDetail.validation.methodArgumentNotValid.title", null, TEST_LOCALE);

        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseLoginUrl, HttpMethod.POST, requestEntity, ProblemDetail.class
        );

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "validation/method-argument-not-valid"));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedProblemTitle);
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalidParams");
        assertThat(invalidParams).isNotNull();
        boolean fieldErrorFound = invalidParams.stream().anyMatch(errorMap ->
                expectedInvalidField.equals(errorMap.get("field")) &&
                        expectedValidationMessage.equals(errorMap.get("message"))
        );
        assertThat(fieldErrorFound)
                .as("Expected validation error for field '%s' with message '%s' was not found in invalidParams: %s",
                        expectedInvalidField, expectedValidationMessage, invalidParams)
                .isTrue();
    }

    @Test
    @DisplayName("POST /login : Неверный пароль -> должен вернуть 401 Unauthorized с ProblemDetail")
    void loginUser_whenPasswordIsIncorrect_shouldReturnUnauthorizedWithProblemDetail() {
        // Arrange
        LoginRequest requestWithWrongPassword = new LoginRequest(TEST_USER_EMAIL, "wrongPassword");
        HttpEntity<LoginRequest> requestEntity = createJsonRequestEntity(requestWithWrongPassword);

        String expectedTitle = messageSource.getMessage("problemDetail.auth.invalidCredentials.title", null, TEST_LOCALE);
        String expectedDetail = messageSource.getMessage("problemDetail.auth.invalidCredentials.detail", null, TEST_LOCALE);

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(
                baseLoginUrl, requestEntity, ProblemDetail.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer realm=\"task-tracker\""); // Проверяем заголовок WWW-Authenticate

        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "auth/invalid-credentials")); // Ожидаем специфичный тип
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("POST /login : Пользователь не существует -> должен вернуть 401 Unauthorized с ProblemDetail")
    void loginUser_whenUserDoesNotExist_shouldReturnUnauthorizedWithProblemDetail() {
        // Arrange
        LoginRequest requestForNonExistingUser = new LoginRequest("nonexistent@example.com", "password123");
        HttpEntity<LoginRequest> requestEntity = createJsonRequestEntity(requestForNonExistingUser);

        // Ожидаем те же title/detail, что и для неверного пароля, так как мы не должны раскрывать,
        // существует ли пользователь или нет, из соображений безопасности.
        String expectedTitle = messageSource.getMessage("problemDetail.auth.invalidCredentials.title", null, TEST_LOCALE);
        String expectedDetail = messageSource.getMessage("problemDetail.auth.invalidCredentials.detail", null, TEST_LOCALE);

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(
                baseLoginUrl, requestEntity, ProblemDetail.class
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer realm=\"task-tracker\"");

        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "auth/invalid-credentials"));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
}