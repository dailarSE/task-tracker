package com.example.tasktracker.backend.task.web;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.jwt.JwtIssuer;
import com.example.tasktracker.backend.security.jwt.JwtKeyService;
import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.task.repository.TaskRepository;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для {@link TaskController}, эндпоинт создания задачи.
 * Используют {@link TestRestTemplate}, полный контекст Spring Boot и Testcontainers для PostgreSQL.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci")
class TaskControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:17.4-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProperties appJwtProperties;

    @Autowired
    private Clock appClock;

    @Autowired
    private JwtIssuer jwtIssuer; // Для генерации JWT для тестов

    private String baseTasksUrl;
    private User testUser;
    private String jwtForTestUser;

    @BeforeEach
    void setUp() {
        // Очищаем репозитории перед каждым тестом
        userRepository.deleteAll(); // Удалит пользователей (и задачи по CASCADE)
        userRepository.flush();     // Убедимся, что все ушло в БД

        // Создаем тестового пользователя
        testUser = new User(null, "taskuser@example.com", passwordEncoder.encode("password"), null, null);
        testUser = userRepository.saveAndFlush(testUser);

        // Генерируем JWT для этого пользователя
        AppUserDetails appUserDetails = new AppUserDetails(testUser);
        Authentication authentication = new TestingAuthenticationToken(appUserDetails, null, appUserDetails.getAuthorities());
        jwtForTestUser = jwtIssuer.generateToken(authentication);

        baseTasksUrl = "http://localhost:" + port + ApiConstants.TASKS_API_BASE_URL;
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        userRepository.flush();
    }

    private HttpEntity<Object> createHttpEntityWithJwtAndBody(Object body, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) {
            headers.setBearerAuth(jwt);
        }
        return new HttpEntity<>(body, headers);
    }

    // Вспомогательный метод в TaskControllerIT для создания Task, чтобы не дублировать код
    private Task createTaskEntityForTest(String title, User owner, TaskStatus status) {
        Task task = new Task();
        task.setTitle(title);
        task.setUser(owner);
        task.setStatus(status);
        // createdAt/updatedAt будут установлены JPA Auditing при save
        return task;
    }


    @Test
    @DisplayName("POST /tasks: Валидный запрос с валидным JWT -> должен вернуть 201 Created и созданную задачу")
    void createTask_whenValidRequestAndJwt_shouldReturn201AndCreatedTask() {
        // Arrange
        TaskCreateRequest requestDto = new TaskCreateRequest("Valid Task Title", "Valid task description.");
        HttpEntity<Object> requestEntity = createHttpEntityWithJwtAndBody(requestDto, jwtForTestUser);

        // Act
        ResponseEntity<TaskResponse> responseEntity = testRestTemplate.postForEntity(
                baseTasksUrl, requestEntity, TaskResponse.class);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseEntity.getHeaders().getLocation()).isNotNull();

        TaskResponse taskResponse = responseEntity.getBody();
        assertThat(taskResponse).isNotNull();
        assertThat(taskResponse.getId()).isNotNull().isPositive();
        assertThat(taskResponse.getTitle()).isEqualTo(requestDto.getTitle());
        assertThat(taskResponse.getDescription()).isEqualTo(requestDto.getDescription());
        assertThat(taskResponse.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(taskResponse.getUserId()).isEqualTo(testUser.getId());
        assertThat(taskResponse.getCreatedAt()).isNotNull();
        assertThat(taskResponse.getUpdatedAt()).isNotNull();
        assertThat(taskResponse.getCompletedAt()).isNull();

        // Проверяем Location Header
        String expectedLocationSuffix = ApiConstants.API_V1_PREFIX + "/tasks/" + taskResponse.getId();
        assertThat(responseEntity.getHeaders().getLocation().toString()).endsWith(expectedLocationSuffix);

        // Проверяем состояние в БД
        Optional<Task> savedTaskOpt = taskRepository.findByIdAndUserId(taskResponse.getId(), testUser.getId());
        assertThat(savedTaskOpt).isPresent();
        Task savedTask = savedTaskOpt.get();
        assertThat(savedTask.getTitle()).isEqualTo(requestDto.getTitle());
        assertThat(savedTask.getUser().getId()).isEqualTo(testUser.getId());
    }

    // Источник данных для параметризованных тестов валидации DTO
    static Stream<Arguments> invalidTaskCreateRequests() {
        return Stream.of(
                Arguments.of(new TaskCreateRequest(null, "desc"), "title"),
                Arguments.of(new TaskCreateRequest("", "desc"), "title"),
                Arguments.of(new TaskCreateRequest(" ", "desc"), "title"),
                Arguments.of(new TaskCreateRequest("t".repeat(256), "desc"), "title"),
                Arguments.of(new TaskCreateRequest("Valid Title", "d".repeat(1001)), "description")
        );
    }

    @ParameterizedTest(name = "POST /tasks: Невалидный DTO (поле {1}, ключ сообщения {2}) -> должен вернуть 400 Bad Request")
    @MethodSource("invalidTaskCreateRequests")
    @DisplayName("POST /tasks: Невалидный DTO -> должен вернуть 400 Bad Request с ProblemDetail")
    void createTask_whenDtoIsInvalid_shouldReturn400AndProblemDetail(
            TaskCreateRequest invalidDto, String expectedInvalidField) {
        // Arrange
        HttpEntity<Object> requestEntity = createHttpEntityWithJwtAndBody(invalidDto, jwtForTestUser);

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "validation/methodArgumentNotValid"));
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getInstance().toString()).endsWith(ApiConstants.API_V1_PREFIX + "/tasks");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).isNotNull().hasSize(1);
        assertThat(invalidParams.getFirst().get("field")).isEqualTo(expectedInvalidField);

        assertThat(taskRepository.findAllByUserId(testUser.getId(), Pageable.unpaged()).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("POST /tasks: Отсутствует JWT -> должен вернуть 401 Unauthorized")
    void createTask_whenNoJwt_shouldReturn401() {
        // Arrange
        TaskCreateRequest requestDto = new TaskCreateRequest("Task without JWT", null);
        HttpEntity<Object> requestEntity = createHttpEntityWithJwtAndBody(requestDto, null); // null JWT

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer realm=\"task-tracker\"");
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        // Ожидаем тип "unauthorized", так как JwtAuthenticationFilter вызовет AuthenticationEntryPoint
        // с исключением, которое не является BadJwtException (например, нет токена вообще)
        assertThat(problemDetail.getType()).isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "unauthorized"));
        assertThat(taskRepository.findAllByUserId(testUser.getId(), Pageable.unpaged()).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("POST /tasks: Невалидный JWT (malformed) -> должен вернуть 401 Unauthorized")
    void createTask_whenInvalidJwt_shouldReturn401() {
        // Arrange
        TaskCreateRequest requestDto = new TaskCreateRequest("Task with invalid JWT", null);
        HttpEntity<Object> requestEntity = createHttpEntityWithJwtAndBody(requestDto, "this.is.a.bad.jwt");

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer realm=\"task-tracker\""); // Может содержать детали ошибки
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        // Ожидаем тип, специфичный для ошибки JWT, например, malformed
        assertThat(problemDetail.getType()).isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "jwt/malformed"));
        assertThat(problemDetail.getProperties()).containsEntry("error_type", "MALFORMED");
        assertThat(taskRepository.findAllByUserId(testUser.getId(), Pageable.unpaged()).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("POST /tasks: Просроченный JWT -> должен вернуть 401 Unauthorized с ProblemDetail")
    void createTask_whenJwtIsExpired_shouldReturn401WithExpiredProblem() {
        // Arrange
        // Генерируем просроченный токен
        Authentication authentication = new TestingAuthenticationToken(
                new AppUserDetails(testUser), null, List.of()
        );

        Instant tokenIssueTime = Instant.now(appClock).minus(Duration.ofHours(2)); // Токен выдан 2 часа назад
        long tokenLifetimeMs = Duration.ofHours(1).toMillis(); // Токен жил 1 час

        JwtProperties expiredTokenProps = new JwtProperties();
        expiredTokenProps.setSecretKey(appJwtProperties.getSecretKey()); // Используем тот же ключ, что и в приложении
        expiredTokenProps.setExpirationMs(tokenLifetimeMs);
        expiredTokenProps.setEmailClaimKey(appJwtProperties.getEmailClaimKey());
        expiredTokenProps.setAuthoritiesClaimKey(appJwtProperties.getAuthoritiesClaimKey());

        // Важно: JwtKeyService нужно создавать на основе этих пропсов, если он не инжектируется
        // и если он не является синглтоном, настроенным один раз.
        // В нашем случае JwtKeyService инжектируется и настроен на основные пропсы.
        // Для генерации специального токена нам нужен JwtIssuer с кастомным Clock и Expiration.
        JwtKeyService keyServiceForExpiredToken = new JwtKeyService(expiredTokenProps); // Используем пропсы с нужным ключом
        JwtIssuer issuerForExpiredToken = new JwtIssuer(
                expiredTokenProps, // Пропсы с коротким временем жизни
                keyServiceForExpiredToken,   // KeyService с правильным ключом
                Clock.fixed(tokenIssueTime, ZoneOffset.UTC) // Фиксируем время выдачи в прошлом
        );
        String expiredToken = issuerForExpiredToken.generateToken(authentication);

        // Формируем запрос
        TaskCreateRequest requestDto = new TaskCreateRequest("Task with Expired JWT", null);
        HttpEntity<Object> requestEntity = createHttpEntityWithJwtAndBody(requestDto, expiredToken);

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer realm=\"task-tracker\"");

        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + "jwt/expired"));
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problemDetail.getInstance().toString()).endsWith(ApiConstants.API_V1_PREFIX + "/tasks");
        assertThat(problemDetail.getProperties()).containsEntry("error_type", "EXPIRED");

        assertThat(taskRepository.findAllByUserId(testUser.getId(), Pageable.unpaged()).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("GET /tasks: Валидный JWT, пользователь имеет задачи -> должен вернуть 200 OK со списком задач, отсортированных по createdAt DESC")
    void getAllTasks_whenValidJwtAndUserHasTasks_shouldReturn200AndTaskListSorted() {
        // Arrange
        // testUser уже создан в @BeforeEach и для него есть jwtForTestUser
        // Создаем несколько задач для testUser с разными createdAt (используем Clock для контроля времени)
        // Для этого нам нужен доступ к Clock или возможность его мокировать в IT,
        // что сложнее с TestRestTemplate. Проще создать их с небольшими задержками.

        Task task1 = new Task(null, "Task Old", "Old desc", TaskStatus.PENDING, null, null, null, testUser);
        taskRepository.saveAndFlush(task1); // Сохраняем, чтобы createdAt установился


        Task task2 = new Task(null, "Task New", "New desc", TaskStatus.COMPLETED, null, null, Instant.now(appClock), testUser);
        taskRepository.saveAndFlush(task2);

        HttpEntity<Object> requestEntityWithJwt = createHttpEntityWithJwtAndBody(null, jwtForTestUser);

        // Act
        ResponseEntity<List<TaskResponse>> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, // GET /api/v1/tasks
                HttpMethod.GET,
                requestEntityWithJwt,
                new ParameterizedTypeReference<List<TaskResponse>>() {} // Для получения списка
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TaskResponse> tasks = responseEntity.getBody();
        assertThat(tasks)
                .isNotNull()
                .hasSize(2)
                .extracting(TaskResponse::getTitle)
                .containsExactly("Task New", "Task Old"); // Проверяем порядок (новые первыми)

        assertThat(tasks.get(0).getId()).isEqualTo(task2.getId());
        assertThat(tasks.get(1).getId()).isEqualTo(task1.getId());
        assertThat(tasks).allMatch(task -> task.getUserId().equals(testUser.getId()));
    }

    @Test
    @DisplayName("GET /tasks: Валидный JWT, у пользователя нет задач -> должен вернуть 200 OK с пустым списком")
    void getAllTasks_whenValidJwtAndUserHasNoTasks_shouldReturn200AndEmptyList() {
        // Arrange
        HttpEntity<Object> requestEntityWithJwt = createHttpEntityWithJwtAndBody(null, jwtForTestUser);

        // Act
        ResponseEntity<List<TaskResponse>> responseEntity = testRestTemplate.exchange(
                baseTasksUrl,
                HttpMethod.GET,
                requestEntityWithJwt,
                new ParameterizedTypeReference<List<TaskResponse>>() {}
        );

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TaskResponse> tasks = responseEntity.getBody();
        assertThat(tasks).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("GET /tasks: Пользователь видит только свои задачи")
    void getAllTasks_shouldReturnOnlyOwnTasks() {
        // Arrange
        // testUser (пользователь 1) - для него есть jwtForTestUser
        Task taskUser1 = taskRepository.save(createTaskEntityForTest("Task User 1", testUser, TaskStatus.PENDING));

        // Создаем другого пользователя и его задачу

        User anotherUser = userRepository.save(new User(null, "another@example.com",
                passwordEncoder.encode("password"), null, null));
        taskRepository.saveAndFlush(createTaskEntityForTest("Task Another User", anotherUser, TaskStatus.PENDING));

        HttpEntity<Object> requestEntityUser1 = createHttpEntityWithJwtAndBody(null, jwtForTestUser);

        // Act: Запрос от testUser
        ResponseEntity<List<TaskResponse>> responseEntityUser1 = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.GET, requestEntityUser1, new ParameterizedTypeReference<List<TaskResponse>>() {});

        // Assert: testUser видит только свою задачу
        assertThat(responseEntityUser1.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TaskResponse> tasksUser1 = responseEntityUser1.getBody();
        assertThat(tasksUser1)
                .isNotNull()
                .hasSize(1)
                .extracting(TaskResponse::getId)
                .containsExactly(taskUser1.getId());
    }

    @Test
    @DisplayName("GET /tasks: Отсутствует JWT -> должен вернуть 401 Unauthorized")
    void getAllTasks_whenNoJwt_shouldReturn401() {
        // Arrange
        HttpEntity<Object> requestEntityNoJwt = createHttpEntityWithJwtAndBody(null, null);

        // Act
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.GET, requestEntityNoJwt, ProblemDetail.class);

        // Assert
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Проверки ProblemDetail и WWW-Authenticate в тестах для UserControllerIT
    }
}