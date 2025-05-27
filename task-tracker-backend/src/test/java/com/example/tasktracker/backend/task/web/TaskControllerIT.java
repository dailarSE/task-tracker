package com.example.tasktracker.backend.task.web;

import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.task.exception.TaskNotFoundException;
import com.example.tasktracker.backend.task.repository.TaskRepository;
import com.example.tasktracker.backend.test.util.TestJwtUtil;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import com.example.tasktracker.backend.web.ApiConstants;
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
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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


    private String baseTasksUrl;
    private User testUser;
    private String jwtForTestUser;
    private TestJwtUtil testJwtUtil;


    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.flush();

        testUser = new User(null, "taskuser@example.com", passwordEncoder.encode("password"), null, null);
        testUser = userRepository.saveAndFlush(testUser);

        testJwtUtil = new TestJwtUtil(appJwtProperties, appClock);
        jwtForTestUser = testJwtUtil.generateValidToken(testUser);

        baseTasksUrl = "http://localhost:" + port + ApiConstants.TASKS_API_BASE_URL;
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

    private Task persistTask(String title, String description, User owner, TaskStatus status) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setUser(owner);
        task.setStatus(status);
        return taskRepository.saveAndFlush(task);
    }

    private void assertProblemDetailBase(ResponseEntity<ProblemDetail> responseEntity, HttpStatus expectedStatus, String expectedTypeUriPath, String expectedTitle, String expectedInstanceSuffix) {
        assertThat(responseEntity.getStatusCode()).isEqualTo(expectedStatus);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + expectedTypeUriPath)); // Используем path напрямую
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(problemDetail.getInstance().toString()).endsWith(expectedInstanceSuffix);
    }

    private void assertUnauthorizedProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, String expectedJwtErrorType) {
        String expectedTitle;
        // Эти заголовки должны соответствовать messages.properties для security.jwt.<type>.title
        switch (expectedJwtErrorType.toUpperCase()) {
            case "EXPIRED": expectedTitle = "Expired Token"; break;
            case "MALFORMED": expectedTitle = "Malformed Token"; break;
            case "INVALID_SIGNATURE": expectedTitle = "Invalid Token Signature"; break;
            // ... другие типы, если они тестируются
            default: expectedTitle = "Token Processing Error"; // Общий фолбэк
        }
        assertProblemDetailBase(responseEntity, HttpStatus.UNAUTHORIZED,
                "jwt/" + expectedJwtErrorType.toLowerCase().replace("_", "-"),
                expectedTitle,
                expectedInstanceSuffix);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer realm=\"task-tracker\"");
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("error_type")).isEqualTo(expectedJwtErrorType);
    }

    private void assertGeneralUnauthorizedProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix) {
        assertProblemDetailBase(responseEntity, HttpStatus.UNAUTHORIZED,
                "unauthorized", // ключ из security/messages.properties
                "Authentication Required", // title из security/messages.properties
                expectedInstanceSuffix);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"task-tracker\"");
    }

    private void assertTaskNotFoundProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, Long expectedRequestedTaskId, Long expectedContextUserId) {
        assertProblemDetailBase(responseEntity, HttpStatus.NOT_FOUND,
                TaskNotFoundException.PROBLEM_TYPE_URI_PATH, // Используем константу из исключения
                "Task Not Found", // title из task/messages.properties
                expectedInstanceSuffix);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("requested_task_id").toString()).isEqualTo(expectedRequestedTaskId.toString());
        if (expectedContextUserId != null) {
            assertThat(problemDetail.getProperties().get("context_user_id").toString()).isEqualTo(expectedContextUserId.toString());
        } else {
            assertThat(problemDetail.getProperties()).doesNotContainKey("context_user_id");
        }
    }

    private void assertValidationProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, String expectedInvalidField) {
        assertProblemDetailBase(responseEntity, HttpStatus.BAD_REQUEST,
                "validation/methodArgumentNotValid", // из GlobalExceptionHandler
                "Invalid Request Data", // title из common/messages.properties
                expectedInstanceSuffix);
        ProblemDetail problemDetail = responseEntity.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalid_params");
        assertThat(invalidParams).isNotNull().hasSize(1);
        assertThat(invalidParams.getFirst().get("field")).isEqualTo(expectedInvalidField);
    }

    private void assertTypeMismatchProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, String expectedParamName, String expectedParamValue, String expectedParamType) {
        assertProblemDetailBase(responseEntity, HttpStatus.BAD_REQUEST,
                "request/parameter/typeMismatch", // из GlobalExceptionHandler
                "Invalid Parameter Format", // title из common/messages.properties
                expectedInstanceSuffix);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("parameter_name")).isEqualTo(expectedParamName);
        assertThat(problemDetail.getProperties().get("parameter_value")).isEqualTo(expectedParamValue);
        assertThat(problemDetail.getProperties().get("expected_type")).isEqualTo(expectedParamType);
    }


    // --- Тесты для POST /api/v1/tasks ---
    @Test
    @DisplayName("POST /tasks: Валидный запрос с валидным JWT -> должен вернуть 201 Created и созданную задачу")
    void createTask_whenValidRequestAndJwt_shouldReturn201AndCreatedTask() {
        TaskCreateRequest requestDto = new TaskCreateRequest("Valid Task Title", "Valid task description.");
        HttpEntity<Object> requestEntity = createHttpEntity(requestDto, jwtForTestUser);

        ResponseEntity<TaskResponse> responseEntity = testRestTemplate.postForEntity(
                baseTasksUrl, requestEntity, TaskResponse.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseEntity.getHeaders().getLocation()).isNotNull();
        TaskResponse taskResponse = responseEntity.getBody();
        assertThat(taskResponse).isNotNull();
        assertThat(taskResponse.getId()).isNotNull().isPositive();
        assertThat(taskResponse.getTitle()).isEqualTo(requestDto.getTitle());
        assertThat(taskResponse.getUserId()).isEqualTo(testUser.getId());
        String expectedLocationSuffix = ApiConstants.TASKS_API_BASE_URL + "/" + taskResponse.getId();
        assertThat(responseEntity.getHeaders().getLocation().toString()).endsWith(expectedLocationSuffix);
        assertThat(taskRepository.findByIdAndUserId(taskResponse.getId(), testUser.getId())).isPresent();
    }

    static Stream<Arguments> invalidTaskCreateRequests() {
        return Stream.of(
                Arguments.of(new TaskCreateRequest(null, "desc"), "title"),
                Arguments.of(new TaskCreateRequest("", "desc"), "title"),
                Arguments.of(new TaskCreateRequest(" ", "desc"), "title"),
                Arguments.of(new TaskCreateRequest("t".repeat(256), "desc"), "title"),
                Arguments.of(new TaskCreateRequest("Valid Title", "d".repeat(1001)), "description")
        );
    }

    @ParameterizedTest(name = "POST /tasks: Невалидный DTO (поле {1}) -> должен вернуть 400 Bad Request")
    @MethodSource("invalidTaskCreateRequests")
    @DisplayName("POST /tasks: Невалидный DTO -> должен вернуть 400 Bad Request с ProblemDetail")
    void createTask_whenDtoIsInvalid_shouldReturn400AndProblemDetail(TaskCreateRequest invalidDto, String expectedInvalidField) {
        HttpEntity<Object> requestEntity = createHttpEntity(invalidDto, jwtForTestUser);
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);
        assertValidationProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL, expectedInvalidField);
        assertThat(taskRepository.findAllByUserId(testUser.getId(), Pageable.unpaged()).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("POST /tasks: Отсутствует JWT -> должен вернуть 401 Unauthorized")
    void createTask_whenNoJwt_shouldReturn401() {
        TaskCreateRequest requestDto = new TaskCreateRequest("Task without JWT", null);
        HttpEntity<Object> requestEntity = createHttpEntity(requestDto, null);
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);
        assertGeneralUnauthorizedProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL);
        assertThat(taskRepository.findAllByUserId(testUser.getId(), Pageable.unpaged()).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("POST /tasks: Невалидный JWT (malformed) -> должен вернуть 401 Unauthorized")
    void createTask_whenMalformedJwt_shouldReturn401() {
        TaskCreateRequest requestDto = new TaskCreateRequest("Task with invalid JWT", null);
        HttpEntity<Object> requestEntity = createHttpEntity(requestDto, "this.is.a.bad.jwt");
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);
        assertUnauthorizedProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL, "MALFORMED");
        assertThat(taskRepository.findAllByUserId(testUser.getId(), Pageable.unpaged()).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("POST /tasks: Просроченный JWT -> должен вернуть 401 Unauthorized")
    void createTask_whenJwtIsExpired_shouldReturn401() {
        String expiredToken = testJwtUtil.generateExpiredToken(testUser, Duration.ofSeconds(10), Duration.ofHours(1));
        TaskCreateRequest requestDto = new TaskCreateRequest("Task with Expired JWT", null);
        HttpEntity<Object> requestEntity = createHttpEntity(requestDto, expiredToken);
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);
        assertUnauthorizedProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL, "EXPIRED");
        assertThat(taskRepository.findAllByUserId(testUser.getId(), Pageable.unpaged()).getTotalElements()).isZero();
    }


    // --- Тесты для GET /api/v1/tasks ---
    @Test
    @DisplayName("GET /tasks: Валидный JWT, пользователь имеет задачи -> должен вернуть 200 OK со списком задач, отсортированных по createdAt DESC")
    void getAllTasks_whenValidJwtAndUserHasTasks_shouldReturn200AndTaskListSorted() {
        Task task1 = persistTask("Task Old", "Old desc", testUser, TaskStatus.PENDING);
        try { Thread.sleep(20); } catch (InterruptedException ignored) {} // Увеличил задержку
        Task task2 = persistTask("Task New", "New desc", testUser, TaskStatus.COMPLETED);
        HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser);

        ResponseEntity<List<TaskResponse>> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<>() {});

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TaskResponse> tasks = responseEntity.getBody();
        assertThat(tasks)
                .isNotNull()
                .hasSize(2)
                .extracting(TaskResponse::getTitle)
                .containsExactly("Task New", "Task Old");
        assertThat(tasks).allMatch(task -> task.getUserId().equals(testUser.getId()));
    }

    @Test
    @DisplayName("GET /tasks: Валидный JWT, у пользователя нет задач -> должен вернуть 200 OK с пустым списком")
    void getAllTasks_whenValidJwtAndUserHasNoTasks_shouldReturn200AndEmptyList() {
        HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser);
        ResponseEntity<List<TaskResponse>> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<>() {});
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("GET /tasks: Пользователь видит только свои задачи")
    void getAllTasks_shouldReturnOnlyOwnTasks() {
        Task taskUser1 = persistTask("Task User 1", "Desc", testUser, TaskStatus.PENDING);
        User another = userRepository.saveAndFlush(new User(null, "another@example.com", passwordEncoder.encode("pass"), null, null));
        persistTask("Task Another User", "Desc", another, TaskStatus.PENDING);
        HttpEntity<Void> requestEntityUser1 = createHttpEntity(null, jwtForTestUser);

        ResponseEntity<List<TaskResponse>> responseEntityUser1 = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.GET, requestEntityUser1, new ParameterizedTypeReference<>() {});

        assertThat(responseEntityUser1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntityUser1.getBody()).isNotNull().hasSize(1)
                .extracting(TaskResponse::getId).containsExactly(taskUser1.getId());
    }

    @Test
    @DisplayName("GET /tasks: Отсутствует JWT -> должен вернуть 401 Unauthorized")
    void getAllTasks_whenNoJwt_shouldReturn401() {
        HttpEntity<Void> requestEntityNoJwt = createHttpEntity(null, null);
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.GET, requestEntityNoJwt, ProblemDetail.class);
        assertGeneralUnauthorizedProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL);
    }


    // --- Тесты для GET /api/v1/tasks/{taskId} ---
    @Test
    @DisplayName("GET /tasks/{taskId}: Валидный JWT, своя задача -> должен вернуть 200 OK и TaskResponse")
    void getTaskById_whenOwnTaskAndValidJwt_shouldReturn200AndTaskResponse() {
        Task myTask = persistTask("My Task 1", "Description 1", testUser, TaskStatus.PENDING);
        String taskUrl = baseTasksUrl + "/" + myTask.getId();
        HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser);

        ResponseEntity<TaskResponse> responseEntity = testRestTemplate.exchange(
                taskUrl, HttpMethod.GET, requestEntity, TaskResponse.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskResponse taskResponse = responseEntity.getBody();
        assertThat(taskResponse).isNotNull();
        assertThat(taskResponse.getId()).isEqualTo(myTask.getId());
        assertThat(taskResponse.getTitle()).isEqualTo(myTask.getTitle());
        assertThat(taskResponse.getUserId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("GET /tasks/{taskId}: Несуществующая задача -> должен вернуть 404 Not Found")
    void getTaskById_whenTaskNotFound_shouldReturn404() {
        Long nonExistentTaskId = 9999L;
        String taskUrl = baseTasksUrl + "/" + nonExistentTaskId;
        HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser);
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                taskUrl, HttpMethod.GET, requestEntity, ProblemDetail.class);
        assertTaskNotFoundProblemDetail(responseEntity, "/tasks/" + nonExistentTaskId, nonExistentTaskId, testUser.getId());
    }

    @Test
    @DisplayName("GET /tasks/{taskId}: Задача другого пользователя -> должен вернуть 404 Not Found")
    void getTaskById_whenTaskBelongsToAnotherUser_shouldReturn404() {
        User another = userRepository.saveAndFlush(new User(null, "other@example.com", passwordEncoder.encode("p"), null, null));
        Task anotherUserTask = persistTask("Another User's Task", "Desc", another, TaskStatus.PENDING);
        String taskUrl = baseTasksUrl + "/" + anotherUserTask.getId();
        HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser);
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                taskUrl, HttpMethod.GET, requestEntity, ProblemDetail.class);
        assertTaskNotFoundProblemDetail(responseEntity, "/tasks/" + anotherUserTask.getId(), anotherUserTask.getId(), testUser.getId());
    }

    @Test
    @DisplayName("GET /tasks/{taskId}: Невалидный формат taskId -> должен вернуть 400 Bad Request")
    void getTaskById_whenInvalidTaskIdFormat_shouldReturn400() {
        String invalidTaskId = "abc";
        String taskUrl = baseTasksUrl + "/" + invalidTaskId;
        HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser);
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                taskUrl, HttpMethod.GET, requestEntity, ProblemDetail.class);
        assertTypeMismatchProblemDetail(responseEntity, "/tasks/" + invalidTaskId, "taskId", invalidTaskId, "Long");
    }

    @Test
    @DisplayName("GET /tasks/{taskId}: Отсутствует JWT -> должен вернуть 401 Unauthorized")
    void getTaskById_whenNoJwt_shouldReturn401() {
        Task myTask = persistTask("My Task for No JWT test", "Desc", testUser, TaskStatus.PENDING);
        String taskUrl = baseTasksUrl + "/" + myTask.getId();
        HttpEntity<Void> requestEntityNoJwt = createHttpEntity(null, null);
        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                taskUrl, HttpMethod.GET, requestEntityNoJwt, ProblemDetail.class);
        assertGeneralUnauthorizedProblemDetail(responseEntity, "/tasks/" + myTask.getId());
    }
}