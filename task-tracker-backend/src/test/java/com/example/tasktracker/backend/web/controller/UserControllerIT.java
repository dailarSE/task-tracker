package com.example.tasktracker.backend.web.controller;

import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import com.example.tasktracker.backend.web.ApiConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

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
    private PasswordEncoder passwordEncoder; // Нужен для проверки хеша пароля в БД

    private RegisterRequest validRegisterRequestDto;
    private String baseRegisterUrl;
    private static final Locale TEST_LOCALE = Locale.ENGLISH; // Или другая дефолтная локаль для тестов

    // Предполагаем, что ApiConstants.PROBLEM_TYPE_BASE_URI существует
    // private static final String PROBLEM_TYPE_BASE_URI = ApiConstants.PROBLEM_TYPE_BASE_URI;
    // Если нет, то можно захардкодить или добавить в ApiConstants
    private static final String PROBLEM_TYPE_BASE_URI = "https://task-tracker.example.com/probs/";


    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        validRegisterRequestDto = new RegisterRequest("test@example.com", "password123", "password123");
        baseRegisterUrl = "http://localhost:" + port + ApiConstants.USERS_API_BASE_URL + "/register";
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    private HttpEntity<RegisterRequest> createJsonRequestEntity(RegisterRequest payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    @Test
    @DisplayName("POST /register : Валидный запрос -> должен создать пользователя и вернуть 201 Created с токеном")
    void registerUser_whenRequestIsValid_shouldCreateUserAndReturnCreatedWithToken() {
        HttpEntity<RegisterRequest> requestEntity = createJsonRequestEntity(validRegisterRequestDto);

        ResponseEntity<AuthResponse> responseEntity = testRestTemplate.postForEntity(
                baseRegisterUrl, requestEntity, AuthResponse.class
        );

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

    // Провайдер аргументов для параметризованного теста на невалидные DTO
    // Сообщения валидации теперь предполагаются прямо в аннотациях DTO или
    // как ключи, которые Hibernate Validator сам интерполирует с {min}/{max}
    static Stream<Arguments> invalidRegisterRequestsSource() {
        return Stream.of(
                Arguments.of(new RegisterRequest("", "password123", "password123"), "email", "Email address must not be blank."),
                Arguments.of(new RegisterRequest("not-an-email", "password123", "password123"), "email", "Email address must be a valid format (e.g., user@example.com)."),
                Arguments.of(new RegisterRequest("a".repeat(260) + "@example.com", "password123", "password123"), "email", "Email address length must be between 0 and 255 characters."),
                Arguments.of(new RegisterRequest("test@example.com", "", "password123"), "password", "Password must not be blank."), // Одна из ошибок для пустого пароля
                Arguments.of(new RegisterRequest("test@example.com", "password123", ""), "repeatPassword", "Password confirmation must not be blank.")
        );
    }

    @ParameterizedTest(name = "POST /register : Невалидный DTO (поле {1}, ожидаем \"{2}\") -> должен вернуть 400 Bad Request")
    @MethodSource("invalidRegisterRequestsSource")
    @DisplayName("POST /register : Невалидный DTO -> должен вернуть 400 Bad Request с ProblemDetail")
    void registerUser_whenRequestDtoIsInvalid_shouldReturnBadRequestWithProblemDetail(
            RegisterRequest invalidRequest, String expectedInvalidField, String expectedValidationMessage) {

        HttpEntity<RegisterRequest> requestEntity = createJsonRequestEntity(invalidRequest);
        String expectedProblemTitle = messageSource.getMessage("problemDetail.validation.methodArgumentNotValid.title", null, TEST_LOCALE);

        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseRegisterUrl, HttpMethod.POST, requestEntity, ProblemDetail.class
        );

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "validation/methodArgumentNotValid"));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedProblemTitle);
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).isNotNull();

        // Проверяем, что в списке ошибок есть ошибка для нашего поля с ожидаемым сообщением
        boolean fieldErrorFound = invalidParams.stream().anyMatch(errorMap ->
                expectedInvalidField.equals(errorMap.get("field")) &&
                        expectedValidationMessage.equals(errorMap.get("message"))
        );
        assertThat(fieldErrorFound)
                .as("Expected validation error for field '%s' with message '%s' was not found in invalid_params: %s",
                        expectedInvalidField, expectedValidationMessage, invalidParams)
                .isTrue();

        // Если поле нарушает несколько правил, invalid_params будет содержать несколько записей для этого поля,
        // или несколько записей в целом. Этот тест проверяет наличие *хотя бы одной* ожидаемой ошибки.
        // Для более точной проверки количества ошибок, если одно поле нарушает несколько правил,
        // можно изменить ассерт на invalidParams.size() и проверять все сообщения.

        assertThat(userRepository.count()).isZero();
    }


    @Test
    @DisplayName("POST /register : Пароли не совпадают (ошибка из сервиса) -> должен вернуть 400 Bad Request с ProblemDetail")
    void registerUser_whenPasswordsDoNotMatchInService_shouldReturnBadRequestWithProblemDetail() {
        RegisterRequest requestWithMismatch = new RegisterRequest("mismatch@example.com", "password123", "password456");
        HttpEntity<RegisterRequest> requestEntity = createJsonRequestEntity(requestWithMismatch);

        String serviceErrorMessage = "Passwords do not match."; // Это сообщение из PasswordMismatchException
        String expectedTitle = messageSource.getMessage("problemDetail.user.passwordMismatch.title", null, TEST_LOCALE);
        // Detail теперь должен быть точным сообщением из исключения, которое мы передаем в MessageSource как аргумент {0}
        String expectedDetail = messageSource.getMessage("problemDetail.user.passwordMismatch.detail", new Object[]{serviceErrorMessage}, TEST_LOCALE);

        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(
                baseRegisterUrl, requestEntity, ProblemDetail.class
        );

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "user/password-mismatch"));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /register : Email уже существует (ошибка из сервиса) -> должен вернуть 409 Conflict с ProblemDetail")
    void registerUser_whenEmailAlreadyExistsInService_shouldReturnConflictWithProblemDetail() {
        User existingUser = new User();
        existingUser.setEmail(validRegisterRequestDto.getEmail());
        existingUser.setPassword(passwordEncoder.encode("someOtherPassword"));
        userRepository.saveAndFlush(existingUser);

        HttpEntity<RegisterRequest> requestEntity = createJsonRequestEntity(validRegisterRequestDto);
        String serviceErrorMessage = "User with email " + validRegisterRequestDto.getEmail() + " already exists.";
        String expectedTitle = messageSource.getMessage("problemDetail.user.alreadyExists.title", null, TEST_LOCALE);
        String expectedDetail = messageSource.getMessage("problemDetail.user.alreadyExists.detail", new Object[]{serviceErrorMessage}, TEST_LOCALE);

        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(
                baseRegisterUrl, requestEntity, ProblemDetail.class
        );

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(PROBLEM_TYPE_BASE_URI + "user/already-exists"));
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getDetail()).isEqualTo(expectedDetail);
        assertThat(userRepository.count()).isEqualTo(1);
    }
}