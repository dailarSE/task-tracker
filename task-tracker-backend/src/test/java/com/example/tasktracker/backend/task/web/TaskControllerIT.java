package com.example.tasktracker.backend.task.web;

import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.dto.TaskUpdateRequest;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.task.exception.TaskNotFoundException;
import com.example.tasktracker.backend.test.util.TestJwtUtil;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import com.example.tasktracker.backend.web.ApiConstants;
import com.example.tasktracker.backend.web.exception.ResourceConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.core.ParameterizedTypeReference;
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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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

    // UserRepository и PasswordEncoder используются ТОЛЬКО для создания тестовых пользователей в @BeforeEach / @AfterEach.
    // В самих тестах взаимодействие с БД только через API.
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties appJwtProperties;
    @Autowired
    private Clock appClock;
    @Autowired
    private MessageSource messageSource; // Для проверки заголовков ProblemDetail

    private String baseTasksUrl;
    private User testUser1;
    private String jwtForTestUser1;
    private User testUser2;
    private String jwtForTestUser2;

    private TestJwtUtil testJwtUtil;
    private static final Locale TEST_LOCALE = Locale.ENGLISH;

    @BeforeEach
    void setUp() {
        // TaskRepository не нужен, так как CASCADE DELETE настроен для User -> Task
        userRepository.deleteAllInBatch();

        // Создаем тестовых пользователей
        testUser1 = new User(null, "taskuser1@example.com", passwordEncoder.encode("password"), null, null);
        testUser1 = userRepository.saveAndFlush(testUser1);

        testUser2 = new User(null, "taskuser2@example.com", passwordEncoder.encode("password"), null, null);
        testUser2 = userRepository.saveAndFlush(testUser2);

        testJwtUtil = new TestJwtUtil(appJwtProperties, appClock);
        jwtForTestUser1 = testJwtUtil.generateValidToken(testUser1);
        jwtForTestUser2 = testJwtUtil.generateValidToken(testUser2);

        baseTasksUrl = "http://localhost:" + port + ApiConstants.TASKS_API_BASE_URL;
    }

    // --- Вспомогательные методы для HTTP запросов и создания сущностей через API ---

    private <T> HttpEntity<T> createHttpEntity(@Nullable T body, @Nullable String jwtToken, @Nullable MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("application/problem+json")));
        if (contentType != null) {
            headers.setContentType(contentType);
        }
        if (jwtToken != null) {
            headers.setBearerAuth(jwtToken);
        }
        return new HttpEntity<>(body, headers);
    }

    private <T> HttpEntity<T> createHttpEntity(@Nullable T body, @Nullable String jwtToken) {
        return createHttpEntity(body, jwtToken, MediaType.APPLICATION_JSON);

    }

    private TaskResponse createTaskApi(String title, String description, String jwtToken) {
        TaskCreateRequest createRequest = new TaskCreateRequest(title, description);
        HttpEntity<TaskCreateRequest> entity = createHttpEntity(createRequest, jwtToken);
        ResponseEntity<TaskResponse> response = testRestTemplate.postForEntity(baseTasksUrl, entity, TaskResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        return response.getBody();
    }

    private ResponseEntity<TaskResponse> getTaskApi(Long taskId, String jwtToken) {
        String taskUrl = baseTasksUrl + "/" + taskId;
        HttpEntity<Void> entity = createHttpEntity(null, jwtToken);
        return testRestTemplate.exchange(taskUrl, HttpMethod.GET, entity, TaskResponse.class);
    }

    private ResponseEntity<List<TaskResponse>> getAllTasksApi(String jwtToken) {
        HttpEntity<Void> entity = createHttpEntity(null, jwtToken);
        return testRestTemplate.exchange(
                baseTasksUrl, HttpMethod.GET, entity, new ParameterizedTypeReference<List<TaskResponse>>() {});
    }

    private ResponseEntity<TaskResponse> updateTaskApi(Long taskId, TaskUpdateRequest updateRequest, String jwtToken) {
        String taskUrl = baseTasksUrl + "/" + taskId;
        HttpEntity<TaskUpdateRequest> entity = createHttpEntity(updateRequest, jwtToken);
        return testRestTemplate.exchange(taskUrl, HttpMethod.PUT, entity, TaskResponse.class);
    }

    // --- Вспомогательные методы для Assertions ProblemDetail (скопированы и адаптированы из UserControllerIT) ---

    private void assertProblemDetailBase(ResponseEntity<ProblemDetail> responseEntity, HttpStatus expectedStatus, String expectedTypeUriPath, String expectedTitleKeySuffix, String expectedInstanceSuffix) {
        assertThat(responseEntity.getStatusCode()).isEqualTo(expectedStatus);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + expectedTypeUriPath));
        String expectedTitle = messageSource.getMessage("problemDetail." + expectedTitleKeySuffix + ".title", null, TEST_LOCALE);
        assertThat(problemDetail.getTitle()).isEqualTo(expectedTitle);
        assertThat(problemDetail.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(problemDetail.getInstance().toString()).endsWith(expectedInstanceSuffix);
    }

    private void assertUnauthorizedProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, String expectedJwtErrorType) {
        String typePath = "jwt/" + expectedJwtErrorType.toLowerCase().replace("_", "-");
        String titleSuffix = "jwt." + expectedJwtErrorType.toLowerCase();
        assertProblemDetailBase(responseEntity, HttpStatus.UNAUTHORIZED, typePath, titleSuffix, expectedInstanceSuffix);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer realm=\"task-tracker\"");
    }

    private void assertGeneralUnauthorizedProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix) {
        assertProblemDetailBase(responseEntity, HttpStatus.UNAUTHORIZED, "unauthorized", "unauthorized", expectedInstanceSuffix);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"task-tracker\"");
    }

    private void assertTaskNotFoundProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, Long expectedRequestedTaskId, @Nullable Long expectedContextUserId) {
        assertProblemDetailBase(responseEntity, HttpStatus.NOT_FOUND,
                TaskNotFoundException.PROBLEM_TYPE_URI_PATH, // "task/not-found"
                TaskNotFoundException.PROBLEM_TYPE_SUFFIX,   // "task.notFound" для ключа title
                expectedInstanceSuffix);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("requestedTaskId").toString()).isEqualTo(expectedRequestedTaskId.toString());
        if (expectedContextUserId != null) {
            assertThat(problemDetail.getProperties().get("contextUserId").toString()).isEqualTo(expectedContextUserId.toString());
        } else {
            assertThat(problemDetail.getProperties()).doesNotContainKey("contextUserId");
        }
    }

    private void assertValidationProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, String expectedInvalidField) {
        assertProblemDetailBase(responseEntity, HttpStatus.BAD_REQUEST,
                "validation/method-argument-not-valid",
                "validation.methodArgumentNotValid",
                expectedInstanceSuffix);
        ProblemDetail problemDetail = responseEntity.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalidParams");
        assertThat(invalidParams).isNotNull();
        boolean fieldErrorFound = invalidParams.stream().anyMatch(errorMap ->
                expectedInvalidField.equals(errorMap.get("field")) &&
                        errorMap.containsKey("message") &&
                        errorMap.get("message") instanceof String &&
                        !((String) errorMap.get("message")).isEmpty()
        );
        assertThat(fieldErrorFound)
                .as("Expected validation error for field '%s' was not found or message was empty in invalidParams: %s",
                        expectedInvalidField, invalidParams)
                .isTrue();
    }

    private void assertTypeMismatchProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, String expectedParamName, String expectedParamValue, String expectedParamType) {
        assertProblemDetailBase(responseEntity, HttpStatus.BAD_REQUEST,
                "request/parameter-type-mismatch",
                "request.parameter.typeMismatch",
                expectedInstanceSuffix);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("field")).isEqualTo(expectedParamName);
        assertThat(problemDetail.getProperties().get("rejectedValue")).isEqualTo(expectedParamValue);
        assertThat(problemDetail.getProperties().get("expectedType")).isEqualTo(expectedParamType);
    }

    private void assertConflictProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, Long expectedResourceId) {
        assertProblemDetailBase(responseEntity, HttpStatus.CONFLICT,
                ResourceConflictException.PROBLEM_TYPE_URI_PATH,
                ResourceConflictException.PROBLEM_TYPE_SUFFIX,
                expectedInstanceSuffix);
        ProblemDetail problemDetail = responseEntity.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getProperties())
                .isNotNull()
                .hasEntrySatisfying(ResourceConflictException.CONFLICTING_RESOURCE_ID_PROPERTY,
                        value -> assertThat(value.toString()).isEqualTo(expectedResourceId.toString()));
    }

    private void assertConstraintViolationProblemDetail(ResponseEntity<ProblemDetail> responseEntity, String expectedInstanceSuffix, String expectedInvalidField) {
        // Используем правильный type URI для этого типа исключения
        assertProblemDetailBase(responseEntity, HttpStatus.BAD_REQUEST,
                "validation/constraint-violation",
                "validation.constraintViolation",
                expectedInstanceSuffix);

        ProblemDetail problemDetail = responseEntity.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invalidParams = (List<Map<String, Object>>) problemDetail.getProperties().get("invalidParams");
        assertThat(invalidParams).isNotNull();

        // Логика проверки invalidParams остается той же
        boolean fieldErrorFound = invalidParams.stream().anyMatch(errorMap ->
                expectedInvalidField.equals(errorMap.get("field")) &&
                        errorMap.containsKey("message") &&
                        errorMap.get("message") instanceof String &&
                        !((String) errorMap.get("message")).isEmpty()
        );
        assertThat(fieldErrorFound)
                .as("Expected validation error for field '%s' was not found in invalidParams: %s",
                        expectedInvalidField, invalidParams)
                .isTrue();
    }

    // =====================================================================================
    // == Тесты для POST /api/v1/tasks (US4)
    // =====================================================================================
    @Nested
    @DisplayName("POST /api/v1/tasks (Create Task) Tests")
    class CreateTaskITests {
        // TC_IT_CREATE_01 (US4_AC2, US4_AC3)
        @Test
        void createTask_whenValidRequestAndJwt_shouldReturn201AndCreatedTask() {
            TaskCreateRequest requestDto = new TaskCreateRequest("Valid Task Title", "Valid task description.");
            HttpEntity<TaskCreateRequest> requestEntity = createHttpEntity(requestDto, jwtForTestUser1);

            ResponseEntity<TaskResponse> responseEntity = testRestTemplate.postForEntity(
                    baseTasksUrl, requestEntity, TaskResponse.class);

            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            URI location = responseEntity.getHeaders().getLocation();
            assertThat(location).isNotNull();

            TaskResponse taskResponse = responseEntity.getBody();
            assertThat(taskResponse).isNotNull();
            assertThat(taskResponse.getId()).isNotNull().isPositive();
            assertThat(taskResponse.getTitle()).isEqualTo(requestDto.getTitle());
            assertThat(taskResponse.getDescription()).isEqualTo(requestDto.getDescription());
            assertThat(taskResponse.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(taskResponse.getUserId()).isEqualTo(testUser1.getId());
            assertThat(taskResponse.getCreatedAt()).isNotNull();
            assertThat(taskResponse.getUpdatedAt()).isNotNull();
            assertThat(taskResponse.getCompletedAt()).isNull();
            assertThat(location.toString()).endsWith(ApiConstants.TASKS_API_BASE_URL + "/" + taskResponse.getId());

            // Проверка состояния через GET API
            ResponseEntity<TaskResponse> getResponse = getTaskApi(taskResponse.getId(), jwtForTestUser1);
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isEqualToComparingFieldByField(taskResponse);
        }

        // TC_IT_CREATE_02 (часть из US4_AC2 - title пустой/пробелы/слишком длинный, desc слишком длинный)
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
        void createTask_whenDtoIsInvalid_shouldReturn400AndProblemDetail(
                TaskCreateRequest invalidDto, String expectedInvalidField) {
            HttpEntity<TaskCreateRequest> requestEntity = createHttpEntity(invalidDto, jwtForTestUser1);
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);
            assertValidationProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL, expectedInvalidField);
        }

        // TC_IT_CREATE_03 (US4_AC2 - некорректный JSON)
        @Test
        void createTask_whenBodyIsMalformedJson_shouldReturn400() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtForTestUser1);
            HttpEntity<String> requestEntity = new HttpEntity<>("this is not json", headers);

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);

            // Проверяем общий тип, т.к. конкретный суффикс может зависеть от парсера
            assertProblemDetailBase(responseEntity, HttpStatus.BAD_REQUEST,
                    "request/body-conversion-error", // или request.body.notReadable в зависимости от парсера
                    "request.body.conversionError", // ключ для title
                    ApiConstants.TASKS_API_BASE_URL);
        }

        // TC_IT_CREATE_04 (US4_AC1)
        @Test
        void createTask_whenNoJwt_shouldReturn401() {
            TaskCreateRequest requestDto = new TaskCreateRequest("Task without JWT", null);
            HttpEntity<TaskCreateRequest> requestEntity = createHttpEntity(requestDto, null);
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);
            assertGeneralUnauthorizedProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL);
        }

        // TC_IT_CREATE_05 (US4_AC1)
        @Test
        void createTask_whenMalformedJwt_shouldReturn401() {
            TaskCreateRequest requestDto = new TaskCreateRequest("Task with invalid JWT", null);
            HttpEntity<TaskCreateRequest> requestEntity = createHttpEntity(requestDto, "this.is.a.bad.jwt");
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);
            assertUnauthorizedProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL, "MALFORMED");
        }

        // TC_IT_CREATE_06 (US4_AC1)
        @Test
        void createTask_whenJwtIsExpired_shouldReturn401() {
            String expiredToken = testJwtUtil.generateExpiredToken(testUser1, Duration.ofSeconds(10), Duration.ofHours(1));
            TaskCreateRequest requestDto = new TaskCreateRequest("Task with Expired JWT", null);
            HttpEntity<TaskCreateRequest> requestEntity = createHttpEntity(requestDto, expiredToken);
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    baseTasksUrl, HttpMethod.POST, requestEntity, ProblemDetail.class);
            assertUnauthorizedProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL, "EXPIRED");
        }
    }

    // =====================================================================================
    // == Тесты для GET /api/v1/tasks (US5)
    // =====================================================================================
    @Nested
    @DisplayName("GET /api/v1/tasks (Get All Tasks) Tests")
    class GetAllTasksITests {
        // TC_IT_GETALL_01 (US5_AC2, US5_AC4)
        @Test
        void getAllTasks_whenUserHasTasks_shouldReturn200AndTaskListSortedByCreatedAtDesc() {
            TaskResponse task1 = createTaskApi("Task Old", "Old", jwtForTestUser1);
            try { Thread.sleep(10); } catch (InterruptedException ignored) {} // Гарантируем разное время
            TaskResponse task2 = createTaskApi("Task New", "New", jwtForTestUser1);

            ResponseEntity<List<TaskResponse>> responseEntity = getAllTasksApi(jwtForTestUser1);

            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<TaskResponse> tasks = responseEntity.getBody();
            assertThat(tasks).isNotNull().hasSize(2)
                    .extracting(TaskResponse::getId)
                    .containsExactly(task2.getId(), task1.getId()); // Новые первыми
            assertThat(tasks).allMatch(task -> task.getUserId().equals(testUser1.getId()));
        }

        // TC_IT_GETALL_02 (US5_AC3)
        @Test
        void getAllTasks_whenUserHasNoTasks_shouldReturn200AndEmptyList() {
            ResponseEntity<List<TaskResponse>> responseEntity = getAllTasksApi(jwtForTestUser1);
            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(responseEntity.getBody()).isNotNull().isEmpty();
        }

        // TC_IT_GETALL_03 (US5_AC2 - изоляция данных)
        @Test
        void getAllTasks_shouldReturnOnlyOwnTasks() {
            TaskResponse taskUser1 = createTaskApi("Task User 1", "Desc", jwtForTestUser1);
            createTaskApi("Task User 2", "Desc", jwtForTestUser2); // Задача для другого пользователя

            ResponseEntity<List<TaskResponse>> responseEntityUser1 = getAllTasksApi(jwtForTestUser1);

            assertThat(responseEntityUser1.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(responseEntityUser1.getBody()).isNotNull().hasSize(1)
                    .extracting(TaskResponse::getId).containsExactly(taskUser1.getId());
        }

        // TC_IT_GETALL_04 (US5_AC1)
        @Test
        void getAllTasks_whenNoJwt_shouldReturn401() {
            HttpEntity<Void> requestEntityNoJwt = createHttpEntity(null, null);
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    baseTasksUrl, HttpMethod.GET, requestEntityNoJwt, ProblemDetail.class);
            assertGeneralUnauthorizedProblemDetail(responseEntity, ApiConstants.TASKS_API_BASE_URL);
        }
    }

    // =====================================================================================
    // == Тесты для GET /api/v1/tasks/{taskId} (US6)
    // =====================================================================================
    @Nested
    @DisplayName("GET /api/v1/tasks/{taskId} (Get Task By ID) Tests")
    class GetTaskByIdITests {
        // TC_IT_GETBYID_01 (US6_AC2)
        @Test
        void getTaskById_whenOwnTaskAndValidJwt_shouldReturn200AndTaskResponse() {
            TaskResponse createdTask = createTaskApi("My Task 1", "Desc 1", jwtForTestUser1);

            ResponseEntity<TaskResponse> responseEntity = getTaskApi(createdTask.getId(), jwtForTestUser1);

            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(responseEntity.getBody()).isNotNull();
            assertThat(responseEntity.getBody().getId()).isEqualTo(createdTask.getId());
            assertThat(responseEntity.getBody().getTitle()).isEqualTo(createdTask.getTitle());
            assertThat(responseEntity.getBody().getUserId()).isEqualTo(testUser1.getId());
        }

        // TC_IT_GETBYID_02 (US6_AC3)
        @Test
        void getTaskById_whenTaskNotFound_shouldReturn404() {
            Long nonExistentTaskId = 9999L;
            String taskUrl = baseTasksUrl + "/" + nonExistentTaskId;
            HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser1);
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.GET, requestEntity, ProblemDetail.class);
            assertTaskNotFoundProblemDetail(responseEntity, "/tasks/" + nonExistentTaskId, nonExistentTaskId, testUser1.getId());
        }

        // TC_IT_GETBYID_03 (US6_AC3)
        @Test
        void getTaskById_whenTaskBelongsToAnotherUser_shouldReturn404() {
            TaskResponse anotherUserTask = createTaskApi("Another User's Task", "Desc", jwtForTestUser2);
            String taskUrl = baseTasksUrl + "/" + anotherUserTask.getId();
            HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser1); // Запрос от testUser1
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.GET, requestEntity, ProblemDetail.class);
            assertTaskNotFoundProblemDetail(responseEntity, "/tasks/" + anotherUserTask.getId(), anotherUserTask.getId(), testUser1.getId());
        }

        // TC_IT_GETBYID_04 (US6_AC4)
        @Test
        void getTaskById_whenInvalidTaskIdFormat_shouldReturn400() {
            String invalidTaskId = "abc";
            String taskUrl = baseTasksUrl + "/" + invalidTaskId;
            HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser1);
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.GET, requestEntity, ProblemDetail.class);
            assertTypeMismatchProblemDetail(responseEntity, "/tasks/" + invalidTaskId, "taskId", invalidTaskId, "Long");
        }

        // TC_IT_GETBYID_05 (US6_AC1)
        @Test
        void getTaskById_whenNoJwt_shouldReturn401() {
            TaskResponse createdTask = createTaskApi("Task for No JWT test", "Desc", jwtForTestUser1);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();
            HttpEntity<Void> requestEntityNoJwt = createHttpEntity(null, null);
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.GET, requestEntityNoJwt, ProblemDetail.class);
            assertGeneralUnauthorizedProblemDetail(responseEntity, "/tasks/" + createdTask.getId());
        }
    }

    // =====================================================================================
    // == Тесты для PUT /api/v1/tasks/{taskId} (US7)
    // =====================================================================================
    @Nested
    @DisplayName("PUT /api/v1/tasks/{taskId} (Update Task) Tests")
    class UpdateTaskITests {

        @Test
        @DisplayName("Успешное обновление -> должен вернуть 200 OK и обновленную задачу с новой версией")
        void updateTask_whenValidRequest_shouldReturn200AndUpdatedTaskWithNewVersion() {
            // Arrange
            TaskResponse createdTask = createTaskApi("Original Title", "Desc", jwtForTestUser1);
            assertThat(createdTask.getVersion()).isEqualTo(0);

            TaskUpdateRequest updateRequest = new TaskUpdateRequest("Updated Title", "Updated Desc", TaskStatus.COMPLETED, 0); // Передаем версию 0

            // Act
            ResponseEntity<TaskResponse> responseEntity = updateTaskApi(createdTask.getId(), updateRequest, jwtForTestUser1);

            // Assert
            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            TaskResponse updatedTask = responseEntity.getBody();
            assertThat(updatedTask).isNotNull();
            assertThat(updatedTask.getTitle()).isEqualTo("Updated Title");
            assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(updatedTask.getVersion()).isEqualTo(1); // Версия инкрементировалась
        }

        @Test
        @DisplayName("Неверная версия (конфликт) -> должен вернуть 409 Conflict")
        void updateTask_whenVersionIsIncorrect_shouldReturn409Conflict() {
            // Arrange
            TaskResponse createdTask = createTaskApi("Task for conflict", "Desc", jwtForTestUser1); // version = 0

            // Имитируем, что кто-то другой уже обновил задачу, ее версия теперь 1
            TaskUpdateRequest firstUpdateRequest = new TaskUpdateRequest("First update", "d", TaskStatus.PENDING, 0);
            updateTaskApi(createdTask.getId(), firstUpdateRequest, jwtForTestUser1);

            // Теперь пытаемся обновить, но со старой версией 0
            TaskUpdateRequest conflictingUpdateRequest = new TaskUpdateRequest("Conflicting update", "d", TaskStatus.PENDING, 0);
            HttpEntity<TaskUpdateRequest> requestEntity = createHttpEntity(conflictingUpdateRequest, jwtForTestUser1);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();

            // Act
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.PUT, requestEntity, ProblemDetail.class);

            // Assert
            assertConflictProblemDetail(responseEntity, "/tasks/" + createdTask.getId(), createdTask.getId());
        }

        // Обновленный тест на валидацию DTO
        @Test
        void updateTask_whenVersionIsMissing_shouldReturn400() {
            TaskResponse createdTask = createTaskApi("Task To Update", "Desc", jwtForTestUser1);
            // Создаем DTO без версии
            TaskUpdateRequest invalidDto = new TaskUpdateRequest("Any Title", "Any Desc", TaskStatus.PENDING, null);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();
            HttpEntity<TaskUpdateRequest> requestEntity = createHttpEntity(invalidDto, jwtForTestUser1);

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.PUT, requestEntity, ProblemDetail.class);

            assertValidationProblemDetail(responseEntity, "/tasks/" + createdTask.getId(), "version");
        }

        // TC_IT_UPDATE_01 (US7_AC3 - PENDING -> COMPLETED)
        @Test
        void updateTask_whenValidRequestAndOwnTask_statusToCompleted_shouldReturn200AndUpdatedTask() {
            TaskResponse createdTask = createTaskApi("Original Title", "Original Desc", jwtForTestUser1);
            TaskUpdateRequest updateRequest = new TaskUpdateRequest("Updated Title", "Updated Desc", TaskStatus.COMPLETED, 0);

            ResponseEntity<TaskResponse> putResponseEntity = updateTaskApi(createdTask.getId(), updateRequest, jwtForTestUser1);

            assertThat(putResponseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            TaskResponse updatedTaskResponse = putResponseEntity.getBody();
            assertThat(updatedTaskResponse).isNotNull();
            assertThat(updatedTaskResponse.getId()).isEqualTo(createdTask.getId());
            assertThat(updatedTaskResponse.getTitle()).isEqualTo("Updated Title");
            assertThat(updatedTaskResponse.getDescription()).isEqualTo("Updated Desc");
            assertThat(updatedTaskResponse.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(updatedTaskResponse.getUserId()).isEqualTo(testUser1.getId());
            assertThat(updatedTaskResponse.getUpdatedAt()).isAfterOrEqualTo(createdTask.getUpdatedAt()); // updatedAt должно обновиться
            assertThat(updatedTaskResponse.getCompletedAt()).isNotNull(); // completedAt должно установиться

            // Проверка состояния через GET API
            ResponseEntity<TaskResponse> getResponse = getTaskApi(createdTask.getId(), jwtForTestUser1);
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isEqualToComparingFieldByField(updatedTaskResponse);
        }

        // TC_IT_UPDATE_02 (US7_AC3 - COMPLETED -> PENDING)
        @Test
        void updateTask_whenValidRequestAndOwnTask_statusToPending_shouldReturn200AndUpdatedTask() {
            TaskResponse taskCompleted = createTaskApi("To Be Pending", "Desc", jwtForTestUser1);
            // Сначала переводим в COMPLETED
            updateTaskApi(taskCompleted.getId(), new TaskUpdateRequest(taskCompleted.getTitle(), taskCompleted.getDescription(), TaskStatus.COMPLETED,0), jwtForTestUser1);

            TaskUpdateRequest updateToPendingRequest = new TaskUpdateRequest("Still To Be Pending", "New Desc", TaskStatus.PENDING,1);
            ResponseEntity<TaskResponse> putResponseEntity = updateTaskApi(taskCompleted.getId(), updateToPendingRequest, jwtForTestUser1);

            assertThat(putResponseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            TaskResponse updatedTaskResponse = putResponseEntity.getBody();
            assertThat(updatedTaskResponse.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(updatedTaskResponse.getCompletedAt()).isNull(); // completedAt должно сброситься

            ResponseEntity<TaskResponse> getResponse = getTaskApi(taskCompleted.getId(), jwtForTestUser1);
            assertThat(getResponse.getBody().getCompletedAt()).isNull();
        }

        // TC_IT_UPDATE_03 (US7_AC3 - description -> null)
        @Test
        void updateTask_whenDescriptionSetToNull_shouldReturn200AndTaskWithNullDescription() {
            TaskResponse createdTask = createTaskApi("Task With Desc", "Initial Description", jwtForTestUser1);
            TaskUpdateRequest updateRequest = new TaskUpdateRequest(createdTask.getTitle(), null, createdTask.getStatus(),0);

            ResponseEntity<TaskResponse> putResponseEntity = updateTaskApi(createdTask.getId(), updateRequest, jwtForTestUser1);

            assertThat(putResponseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(putResponseEntity.getBody().getDescription()).isNull();

            ResponseEntity<TaskResponse> getResponse = getTaskApi(createdTask.getId(), jwtForTestUser1);
            assertThat(getResponse.getBody().getDescription()).isNull();
        }

        // TC_IT_UPDATE_04 (US7_AC3 - status не меняется)
        @Test
        void updateTask_whenStatusNotChanged_shouldReturn200AndCompletedAtUnchanged() {
            TaskResponse createdTask = createTaskApi("Task PENDING", "Desc", jwtForTestUser1); // status = PENDING
            TaskUpdateRequest updateRequest = new TaskUpdateRequest("Updated Title", "Updated Desc", TaskStatus.PENDING,0);

            ResponseEntity<TaskResponse> putResponseEntity = updateTaskApi(createdTask.getId(), updateRequest, jwtForTestUser1);

            assertThat(putResponseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(putResponseEntity.getBody().getCompletedAt()).isNull(); // Остается null

            // Теперь для COMPLETED
            TaskResponse taskMadeCompleted = createTaskApi("Task COMPLETED", "Desc", jwtForTestUser1);
            updateTaskApi(taskMadeCompleted.getId(), new TaskUpdateRequest(taskMadeCompleted.getTitle(), taskMadeCompleted.getDescription(), TaskStatus.COMPLETED,0), jwtForTestUser1);
            ResponseEntity<TaskResponse> getCompletedResponse = getTaskApi(taskMadeCompleted.getId(), jwtForTestUser1);
            Instant initialCompletedAt = getCompletedResponse.getBody().getCompletedAt();
            assertThat(initialCompletedAt).isNotNull();

            TaskUpdateRequest updateRequestSameStatus = new TaskUpdateRequest("Updated Title COMPLETED", "New Desc", TaskStatus.COMPLETED,1);
            ResponseEntity<TaskResponse> putSameStatusResponseEntity = updateTaskApi(taskMadeCompleted.getId(), updateRequestSameStatus, jwtForTestUser1);

            assertThat(putSameStatusResponseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(putSameStatusResponseEntity.getBody().getCompletedAt()).isEqualTo(initialCompletedAt); // Не должно измениться
        }

        // TC_IT_UPDATE_05 до TC_IT_UPDATE_09 (US7_AC2 - ошибки валидации DTO)
        static Stream<Arguments> invalidTaskUpdateRequests() {
            return Stream.of(
                    Arguments.of(new TaskUpdateRequest("", "desc", TaskStatus.PENDING,0), "title"),
                    Arguments.of(new TaskUpdateRequest(" ", "desc", TaskStatus.PENDING,0), "title"),
                    Arguments.of(new TaskUpdateRequest("t".repeat(256), "desc", TaskStatus.PENDING,0), "title"),
                    Arguments.of(new TaskUpdateRequest("Valid Title", "d".repeat(1001), TaskStatus.PENDING,0), "description"),
                    Arguments.of(new TaskUpdateRequest("Valid Title", "desc", null,0), "status") // status = null
            );
        }

        @ParameterizedTest(name = "PUT /tasks/taskId: Невалидный DTO (поле {1}) -> должен вернуть 400 Bad Request")
        @MethodSource("invalidTaskUpdateRequests")
        void updateTask_whenDtoIsInvalid_shouldReturn400(TaskUpdateRequest invalidDto, String expectedInvalidField) {
            TaskResponse createdTask = createTaskApi("Task To Update", "Desc", jwtForTestUser1);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();
            HttpEntity<TaskUpdateRequest> requestEntity = createHttpEntity(invalidDto, jwtForTestUser1);

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.PUT, requestEntity, ProblemDetail.class);

            assertValidationProblemDetail(responseEntity, "/tasks/" + createdTask.getId(), expectedInvalidField);
        }

        // TC_IT_UPDATE_09 (альтернативный кейс для некорректного JSON)
        @Test
        void updateTask_whenBodyIsMalformedJson_shouldReturn400() {
            TaskResponse createdTask = createTaskApi("Task for Malformed Update", "Desc", jwtForTestUser1);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtForTestUser1);
            HttpEntity<String> requestEntity = new HttpEntity<>("this is not json", headers);

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.PUT, requestEntity, ProblemDetail.class);

            assertProblemDetailBase(responseEntity, HttpStatus.BAD_REQUEST,
                    "request/body-conversion-error",
                    "request.body.conversionError",
                    "/tasks/" + createdTask.getId());
        }


        // TC_IT_UPDATE_10 (US7_AC4)
        @Test
        void updateTask_whenTaskNotFound_shouldReturn404() {
            Long nonExistentTaskId = 999L;
            String taskUrl = baseTasksUrl + "/" + nonExistentTaskId;
            TaskUpdateRequest updateRequest = new TaskUpdateRequest("Any", "Any", TaskStatus.PENDING,0);
            HttpEntity<TaskUpdateRequest> requestEntity = createHttpEntity(updateRequest, jwtForTestUser1);

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.PUT, requestEntity, ProblemDetail.class);

            assertTaskNotFoundProblemDetail(responseEntity, "/tasks/" + nonExistentTaskId, nonExistentTaskId, testUser1.getId());
        }

        // TC_IT_UPDATE_11 (US7_AC4)
        @Test
        void updateTask_whenTaskBelongsToAnotherUser_shouldReturn404() {
            TaskResponse anotherUserTask = createTaskApi("Another User's Task For Update", "Desc", jwtForTestUser2); // Создаем от имени user2
            String taskUrl = baseTasksUrl + "/" + anotherUserTask.getId();
            TaskUpdateRequest updateRequest = new TaskUpdateRequest("Attempted Update", "Desc", TaskStatus.COMPLETED,0);
            HttpEntity<TaskUpdateRequest> requestEntity = createHttpEntity(updateRequest, jwtForTestUser1); // Пытаемся обновить от имени user1

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.PUT, requestEntity, ProblemDetail.class);

            assertTaskNotFoundProblemDetail(responseEntity, "/tasks/" + anotherUserTask.getId(), anotherUserTask.getId(), testUser1.getId());
        }

        // TC_IT_UPDATE_12 (US7_AC1)
        @Test
        void updateTask_whenNoJwt_shouldReturn401() {
            TaskResponse createdTask = createTaskApi("Task for No JWT Update", "Desc", jwtForTestUser1);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();
            TaskUpdateRequest updateRequest = new TaskUpdateRequest("Update No JWT", "Desc", TaskStatus.PENDING,0);
            HttpEntity<TaskUpdateRequest> requestEntity = createHttpEntity(updateRequest, null); // No JWT

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.PUT, requestEntity, ProblemDetail.class);

            assertGeneralUnauthorizedProblemDetail(responseEntity, "/tasks/" + createdTask.getId());
        }

        // TC_IT_UPDATE_13 (US7_AC1)
        @Test
        void updateTask_whenJwtIsExpired_shouldReturn401() {
            TaskResponse createdTask = createTaskApi("Task for Expired JWT Update", "Desc", jwtForTestUser1);
            String expiredToken = testJwtUtil.generateExpiredToken(testUser1, Duration.ofSeconds(1), Duration.ofSeconds(5));
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();
            TaskUpdateRequest updateRequest = new TaskUpdateRequest("Update Expired JWT", "Desc", TaskStatus.PENDING,0);
            HttpEntity<TaskUpdateRequest> requestEntity = createHttpEntity(updateRequest, expiredToken);

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.PUT, requestEntity, ProblemDetail.class);

            assertUnauthorizedProblemDetail(responseEntity, "/tasks/" + createdTask.getId(), "EXPIRED");
        }

        // TC_IT_UPDATE_14 (US7 - обработка невалидного path variable)
        @Test
        void updateTask_whenTaskIdIsInvalidFormat_shouldReturn400() {
            String invalidTaskId = "abc";
            String taskUrl = baseTasksUrl + "/" + invalidTaskId;
            TaskUpdateRequest updateRequest = new TaskUpdateRequest("Valid", "Valid", TaskStatus.PENDING,0);
            HttpEntity<TaskUpdateRequest> requestEntity = createHttpEntity(updateRequest, jwtForTestUser1);

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.PUT, requestEntity, ProblemDetail.class);

            assertTypeMismatchProblemDetail(responseEntity, "/tasks/" + invalidTaskId, "taskId", invalidTaskId, "Long");
        }
    }

    // =====================================================================================
    // == Тесты для PATCH
    // =====================================================================================
    @Nested
    @DisplayName("PATCH /api/v1/tasks/{taskId} (Partial Update) Tests")
    class PatchTaskITests {

        @Autowired private ObjectMapper objectMapper;

        private HttpEntity<JsonNode> createPatchRequest(Map<String, Object> patchData, String jwtToken) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/merge-patch+json"));
            if (jwtToken != null) { headers.setBearerAuth(jwtToken); }
            return new HttpEntity<>(objectMapper.valueToTree(patchData), headers);
        }

        @Test
        @DisplayName("Успешный PATCH (title и status) -> должен вернуть 200 OK и обновленную задачу")
        void patchTask_whenUpdatingTitleAndStatus_shouldReturn200AndUpdatedTask() {
            TaskResponse createdTask = createTaskApi("Original Title", "Original Desc", jwtForTestUser1);
            Map<String, Object> patch = Map.of( "title", "Patched Title", "status", "COMPLETED", "version", 0 );
            HttpEntity<JsonNode> requestEntity = createPatchRequest(patch, jwtForTestUser1);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();

            ResponseEntity<TaskResponse> responseEntity = testRestTemplate.exchange(taskUrl, HttpMethod.PATCH, requestEntity, TaskResponse.class);

            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
            TaskResponse updatedTask = responseEntity.getBody();
            assertThat(updatedTask).isNotNull();
            assertThat(updatedTask.getTitle()).isEqualTo("Patched Title");
            assertThat(updatedTask.getDescription()).isEqualTo("Original Desc");
            assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(updatedTask.getCompletedAt()).isNotNull();
            assertThat(updatedTask.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("PATCH без 'version' -> должен вернуть 400 Bad Request")
        void patchTask_whenVersionIsMissing_shouldReturn400() {
            TaskResponse createdTask = createTaskApi("Task for patch", "Desc", jwtForTestUser1);
            Map<String, Object> patch = Map.of("title", "Some Title"); // Нет 'version'
            HttpEntity<JsonNode> requestEntity = createPatchRequest(patch, jwtForTestUser1);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(taskUrl, HttpMethod.PATCH, requestEntity, ProblemDetail.class);

            assertConstraintViolationProblemDetail(responseEntity, "/tasks/" + createdTask.getId(), "version");
        }

        @Test
        @DisplayName("PATCH с невалидным полем (пустой title) -> должен вернуть 400 Bad Request")
        void patchTask_whenPatchMakesEntityInvalid_shouldReturn400() {
            TaskResponse createdTask = createTaskApi("Task for patch", "Desc", jwtForTestUser1);
            Map<String, Object> patch = Map.of("title", "", "version", 0);
            HttpEntity<JsonNode> requestEntity = createPatchRequest(patch, jwtForTestUser1);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(taskUrl, HttpMethod.PATCH, requestEntity, ProblemDetail.class);

            assertConstraintViolationProblemDetail(responseEntity, "/tasks/" + createdTask.getId(), "title");
        }

        @Test
        @DisplayName("PATCH с неверной версией -> должен вернуть 409 Conflict")
        void patchTask_whenVersionIsIncorrect_shouldReturn409Conflict() {
            TaskResponse createdTask = createTaskApi("Task for patch conflict", "Desc", jwtForTestUser1); // version=0
            updateTaskApi(createdTask.getId(), new TaskUpdateRequest("T", "D", TaskStatus.PENDING, 0), jwtForTestUser1); // version=1
            Map<String, Object> patch = Map.of("title", "Conflicting Patch", "version", 0);
            HttpEntity<JsonNode> requestEntity = createPatchRequest(patch, jwtForTestUser1);
            String taskUrl = baseTasksUrl + "/" + createdTask.getId();

            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(taskUrl, HttpMethod.PATCH, requestEntity, ProblemDetail.class);

            assertConflictProblemDetail(responseEntity, "/tasks/" + createdTask.getId(), createdTask.getId());
        }
    }

    // =====================================================================================
    // == Тесты для DELETE /api/v1/tasks/{taskId} (US8)
    // =====================================================================================
    @Nested
    @DisplayName("DELETE /api/v1/tasks/{taskId} (Delete Task) Tests")
    class DeleteTaskITests {

        // Вспомогательный метод для DELETE запроса
        private ResponseEntity<ProblemDetail> deleteTaskApiReturnProblemDetail(Long taskId, String jwtToken) {
            String taskUrl = baseTasksUrl + "/" + taskId;
            HttpEntity<Void> entity = createHttpEntity(null, jwtToken);
            // Используем exchange для получения ProblemDetail при ошибке
            return testRestTemplate.exchange(taskUrl, HttpMethod.DELETE, entity, ProblemDetail.class);
        }

        private ResponseEntity<Void> deleteTaskApiReturnVoid(Long taskId, String jwtToken) {
            String taskUrl = baseTasksUrl + "/" + taskId;
            HttpEntity<Void> entity = createHttpEntity(null, jwtToken);
            return testRestTemplate.exchange(taskUrl, HttpMethod.DELETE, entity, Void.class);
        }

        // TC_IT_DELETE_01 (US8_AC2)
        @Test
        @DisplayName("Успешное удаление своей задачи -> должен вернуть 204 No Content и задача должна быть удалена")
        void deleteTask_whenOwnTaskAndValidJwt_shouldReturn204AndTaskIsDeleted() {
            // Arrange: Создаем задачу через API, чтобы у нее был ID
            TaskResponse createdTask = createTaskApi("Task to Delete", "This task will be deleted", jwtForTestUser1);
            Long taskIdToDelete = createdTask.getId();

            // Act: Удаляем задачу
            ResponseEntity<Void> deleteResponseEntity = deleteTaskApiReturnVoid(taskIdToDelete, jwtForTestUser1);

            // Assert: Проверяем статус ответа DELETE
            assertThat(deleteResponseEntity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(deleteResponseEntity.getBody()).isNull(); // У 204 No Content не должно быть тела

            // Assert: Проверяем, что задача действительно удалена (GET должен вернуть 404)
            String taskUrl = baseTasksUrl + "/" + taskIdToDelete;
            HttpEntity<Void> getEntity = createHttpEntity(null, jwtForTestUser1);
            ResponseEntity<ProblemDetail> getResponseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.GET, getEntity, ProblemDetail.class);

            assertTaskNotFoundProblemDetail(getResponseEntity, "/tasks/" + taskIdToDelete, taskIdToDelete, testUser1.getId());
        }

        // TC_IT_DELETE_02 (US8_AC3)
        @Test
        @DisplayName("Попытка удалить несуществующую задачу -> должен вернуть 404 Not Found")
        void deleteTask_whenTaskNotFound_shouldReturn404() {
            // Arrange
            Long nonExistentTaskId = 9999L; // ID, которого точно нет

            // Act
            ResponseEntity<ProblemDetail> responseEntity = deleteTaskApiReturnProblemDetail(nonExistentTaskId, jwtForTestUser1);

            // Assert
            assertTaskNotFoundProblemDetail(responseEntity, "/tasks/" + nonExistentTaskId, nonExistentTaskId, testUser1.getId());
        }

        // TC_IT_DELETE_03 (US8_AC3)
        @Test
        @DisplayName("Попытка удалить чужую задачу -> должен вернуть 404 Not Found")
        void deleteTask_whenTaskBelongsToAnotherUser_shouldReturn404() {
            // Arrange: testUser2 создает задачу
            TaskResponse anotherUserTask = createTaskApi("Another User's Task", "This task belongs to user2", jwtForTestUser2);
            Long taskIdOfAnotherUser = anotherUserTask.getId();

            // Act: testUser1 пытается удалить задачу testUser2
            ResponseEntity<ProblemDetail> responseEntity = deleteTaskApiReturnProblemDetail(taskIdOfAnotherUser, jwtForTestUser1);

            // Assert
            assertTaskNotFoundProblemDetail(responseEntity, "/tasks/" + taskIdOfAnotherUser, taskIdOfAnotherUser, testUser1.getId());

            // Дополнительная проверка: задача user2 все еще должна существовать
            ResponseEntity<TaskResponse> getResponseUser2 = getTaskApi(taskIdOfAnotherUser, jwtForTestUser2);
            assertThat(getResponseUser2.getStatusCode()).isEqualTo(HttpStatus.OK); // user2 все еще может получить свою задачу
        }

        // TC_IT_DELETE_04 (US8_AC4)
        @Test
        @DisplayName("Попытка удалить задачу с невалидным taskId в URL (строка) -> должен вернуть 400 Bad Request")
        void deleteTask_whenInvalidTaskIdFormat_shouldReturn400() {
            // Arrange
            String invalidTaskId = "abc-not-a-number";
            String taskUrl = baseTasksUrl + "/" + invalidTaskId;
            HttpEntity<Void> requestEntity = createHttpEntity(null, jwtForTestUser1);

            // Act
            ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.exchange(
                    taskUrl, HttpMethod.DELETE, requestEntity, ProblemDetail.class);

            // Assert
            assertTypeMismatchProblemDetail(responseEntity, "/tasks/" + invalidTaskId, "taskId",
                    invalidTaskId, "Long");
        }

        // TC_IT_DELETE_05 (US8_AC1)
        @Test
        @DisplayName("Попытка удалить задачу без JWT -> должен вернуть 401 Unauthorized")
        void deleteTask_whenNoJwt_shouldReturn401() {
            // Arrange: Создаем задачу, чтобы было что удалять (хотя до этого не дойдет)
            TaskResponse createdTask = createTaskApi("Task for No JWT Delete", "Desc", jwtForTestUser1);
            Long taskId = createdTask.getId();

            // Act
            ResponseEntity<ProblemDetail> responseEntity = deleteTaskApiReturnProblemDetail(taskId, null); // No JWT

            // Assert
            assertGeneralUnauthorizedProblemDetail(responseEntity, "/tasks/" + taskId);
        }

        // TC_IT_DELETE_06 (US8_AC1)
        @Test
        @DisplayName("Попытка удалить задачу с просроченным JWT -> должен вернуть 401 Unauthorized")
        void deleteTask_whenJwtIsExpired_shouldReturn401() {
            // Arrange
            TaskResponse createdTask = createTaskApi("Task for Expired JWT Delete", "Desc", jwtForTestUser1);
            Long taskId = createdTask.getId();
            String expiredToken = testJwtUtil.generateExpiredToken(testUser1, Duration.ofSeconds(1), Duration.ofSeconds(5));

            // Act
            ResponseEntity<ProblemDetail> responseEntity = deleteTaskApiReturnProblemDetail(taskId, expiredToken);

            // Assert
            assertUnauthorizedProblemDetail(responseEntity, "/tasks/" + taskId, "EXPIRED");
        }
    }
}