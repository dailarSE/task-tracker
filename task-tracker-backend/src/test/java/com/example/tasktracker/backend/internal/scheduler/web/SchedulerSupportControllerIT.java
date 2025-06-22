package com.example.tasktracker.backend.internal.scheduler.web;

import com.example.tasktracker.backend.internal.scheduler.config.SchedulerSupportApiProperties;
import com.example.tasktracker.backend.internal.scheduler.dto.PaginatedUserIdsResponse;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReport;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReportRequest;
import com.example.tasktracker.backend.security.apikey.ApiKeyProperties;
import com.example.tasktracker.backend.security.filter.ApiKeyAuthenticationFilter;
import com.example.tasktracker.backend.test.util.TestClockConfiguration;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import com.example.tasktracker.backend.web.ApiConstants;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.checkerframework.checker.nullness.qual.Nullable;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci")
@DisplayName("Интеграционные тесты для SchedulerSupportController")
@Import(TestClockConfiguration.class)
class SchedulerSupportControllerIT {

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
    private ApiKeyProperties apiKeyProperties;
    @Autowired
    private SchedulerSupportApiProperties schedulerSupportApiProperties;
    @Autowired
    private Clock clock;

    private String validApiKey;
    private String baseInternalUrl;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        baseInternalUrl = "http://localhost:" + port + "/api/v1/internal/scheduler-support";

        validApiKey = apiKeyProperties.getKeysToServices().keySet().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No API keys configured in application-ci.yml for testing"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    private HttpEntity<Void> createHttpEntityWithApiKey(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null) {
            headers.set(ApiKeyAuthenticationFilter.API_KEY_HEADER_NAME, apiKey);
        }
        return new HttpEntity<>(headers);
    }

    @Test
    @DisplayName("GET /user-ids: Валидный API-ключ -> должен вернуть 200 OK")
    void getUserIds_withValidApiKey_shouldReturn200() {
        // Arrange
        HttpEntity<Void> entity = createHttpEntityWithApiKey(validApiKey);
        URI uri = UriComponentsBuilder.fromUriString(baseInternalUrl + "/user-ids").build().toUri();

        // Act
        ResponseEntity<PaginatedUserIdsResponse> response =
                testRestTemplate.exchange(uri, HttpMethod.GET, entity, PaginatedUserIdsResponse.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /user-ids: Невалидный API-ключ -> должен вернуть 401 Unauthorized")
    void getUserIds_withInvalidApiKey_shouldReturn401() {
        // Arrange
        HttpEntity<Void> entity = createHttpEntityWithApiKey("invalid-key-123");
        URI uri = UriComponentsBuilder.fromUriString(baseInternalUrl + "/user-ids").build().toUri();

        // Act
        ResponseEntity<ProblemDetail> response =
                testRestTemplate.exchange(uri, HttpMethod.GET, entity, ProblemDetail.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType()).hasToString(ApiConstants.PROBLEM_TYPE_BASE_URI + "auth/invalid-api-key");
    }

    @Test
    @DisplayName("GET /user-ids: Отсутствует API-ключ -> должен вернуть 401 Unauthorized")
    void getUserIds_withoutApiKey_shouldReturn401() {
        // Arrange
        HttpEntity<Void> entity = createHttpEntityWithApiKey(null);
        URI uri = UriComponentsBuilder.fromUriString(baseInternalUrl + "/user-ids").build().toUri();

        // Act
        ResponseEntity<ProblemDetail> response =
                testRestTemplate.exchange(uri, HttpMethod.GET, entity, ProblemDetail.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /user-ids: Невалидный limit -> должен вернуть 400 Bad Request")
    void getUserIds_withInvalidLimit_shouldReturn400() {
        // Arrange
        HttpEntity<Void> entity = createHttpEntityWithApiKey(validApiKey);
        URI uri = UriComponentsBuilder.fromUriString(baseInternalUrl + "/user-ids")
                .queryParam("limit", 0) // Невалидный limit
                .build().toUri();

        // Act
        ResponseEntity<ProblemDetail> response =
                testRestTemplate.exchange(uri, HttpMethod.GET, entity, ProblemDetail.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType()).hasToString(ApiConstants.PROBLEM_TYPE_BASE_URI + "validation/constraint-violation");
    }

    @Test
    @DisplayName("GET /user-ids: Полный цикл пагинации -> должен корректно вернуть все страницы")
    void getUserIds_fullPaginationCycle_shouldReturnAllPagesCorrectly() {
        // Arrange
        // Создаем 5 пользователей
        IntStream.rangeClosed(1, 5).forEach(i -> {
            User user = new User();
            user.setEmail("user" + i + "@example.com");
            user.setPassword("password");
            userRepository.save(user);
        });

        HttpEntity<Void> entity = createHttpEntityWithApiKey(validApiKey);

        // --- Страница 1 ---
        URI uriPage1 = UriComponentsBuilder.fromUriString(baseInternalUrl + "/user-ids")
                .queryParam("limit", 2)
                .build().toUri();

        ResponseEntity<PaginatedUserIdsResponse> response1 =
                testRestTemplate.exchange(uriPage1, HttpMethod.GET, entity, PaginatedUserIdsResponse.class);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        PaginatedUserIdsResponse body1 = response1.getBody();
        assertThat(body1).isNotNull();
        assertThat(body1.getData()).hasSize(2); // ID могут быть не 1 и 2 из-за sequence
        assertThat(body1.getPageInfo().isHasNextPage()).isTrue();
        assertThat(body1.getPageInfo().getNextPageCursor()).isNotBlank();
        String cursor2 = body1.getPageInfo().getNextPageCursor();

        // --- Страница 2 ---
        URI uriPage2 = UriComponentsBuilder.fromUriString(baseInternalUrl + "/user-ids")
                .queryParam("limit", 2)
                .queryParam("cursor", cursor2)
                .build().toUri();

        ResponseEntity<PaginatedUserIdsResponse> response2 =
                testRestTemplate.exchange(uriPage2, HttpMethod.GET, entity, PaginatedUserIdsResponse.class);

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        PaginatedUserIdsResponse body2 = response2.getBody();
        assertThat(body2).isNotNull();
        assertThat(body2.getData()).hasSize(2);
        assertThat(body2.getPageInfo().isHasNextPage()).isTrue();
        assertThat(body2.getPageInfo().getNextPageCursor()).isNotBlank();
        String cursor3 = body2.getPageInfo().getNextPageCursor();

        // --- Страница 3 (последняя) ---
        URI uriPage3 = UriComponentsBuilder.fromUriString(baseInternalUrl + "/user-ids")
                .queryParam("limit", 2)
                .queryParam("cursor", cursor3)
                .build().toUri();

        ResponseEntity<PaginatedUserIdsResponse> response3 =
                testRestTemplate.exchange(uriPage3, HttpMethod.GET, entity, PaginatedUserIdsResponse.class);

        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
        PaginatedUserIdsResponse body3 = response3.getBody();
        assertThat(body3).isNotNull();
        assertThat(body3.getData()).hasSize(1);
        assertThat(body3.getPageInfo().isHasNextPage()).isFalse();
        assertThat(body3.getPageInfo().getNextPageCursor()).isNull();
    }

    @Nested
    @DisplayName("POST /tasks/user-reports Tests")
    class GetTaskReportsTests {

        private String reportsUrl;

        @BeforeEach
        void setupUrl() {
            reportsUrl = baseInternalUrl + "/tasks/user-reports";
        }

        private <T> HttpEntity<T> createHttpEntityWithApiKey(@Nullable T body) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(ApiKeyAuthenticationFilter.API_KEY_HEADER_NAME, validApiKey);
            return new HttpEntity<>(body, headers);
        }

        private void assertProblemDetail(ResponseEntity<ProblemDetail> response,
                                         HttpStatus expectedStatus,
                                         String expectedTitle) {
            assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
            ProblemDetail body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo(expectedStatus.value());
            assertThat(body.getTitle()).isEqualTo(expectedTitle);
            assertThat(body.getInstance()).isNotNull();
        }

        @Test
        @DisplayName("Успешный запрос -> должен вернуть 200 OK")
        void getTaskReports_whenRequestIsValid_shouldReturn200() {
            // Arrange
            UserTaskReportRequest request = new UserTaskReportRequest(
                    List.of(1L),
                    clock.instant().minus(1, ChronoUnit.DAYS),
                    clock.instant()
            );
            HttpEntity<UserTaskReportRequest> entity = createHttpEntityWithApiKey(request);

            // Act
            ResponseEntity<List<UserTaskReport>> response = testRestTemplate.exchange(
                    reportsUrl, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {
                    });

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("Невалидный интервал (from > to) -> должен вернуть 400 Bad Request")
        void getTaskReports_whenFromIsAfterTo_shouldReturn400() {
            // Arrange
            Instant now = clock.instant();
            UserTaskReportRequest request = new UserTaskReportRequest(List.of(1L), now, now.minusSeconds(1));
            HttpEntity<UserTaskReportRequest> entity = createHttpEntityWithApiKey(request);

            // Act
            ResponseEntity<ProblemDetail> response = testRestTemplate
                    .exchange(reportsUrl, HttpMethod.POST, entity, ProblemDetail.class);

            // Assert
            assertProblemDetail(response, HttpStatus.BAD_REQUEST, "Invalid Request Data");
        }

        @Test
        @DisplayName("Невалидный интервал (слишком большой) -> должен вернуть 400 Bad Request")
        void getTaskReports_whenIntervalIsTooLarge_shouldReturn400() {
            // Arrange
            long maxInterval = schedulerSupportApiProperties.getUserTaskReport().getMaxIntervalDays();
            Instant now = clock.instant();
            UserTaskReportRequest request = new UserTaskReportRequest(List.of(1L),
                    now.minus(maxInterval + 1, ChronoUnit.DAYS), now);
            HttpEntity<UserTaskReportRequest> entity = createHttpEntityWithApiKey(request);

            // Act
            ResponseEntity<ProblemDetail> response = testRestTemplate
                    .exchange(reportsUrl, HttpMethod.POST, entity, ProblemDetail.class);

            // Assert
            assertProblemDetail(response, HttpStatus.BAD_REQUEST, "Invalid Request Data");
        }

        @Test
        @DisplayName("Невалидный интервал (слишком старый) -> должен вернуть 400 Bad Request")
        void getTaskReports_whenReportIsTooOld_shouldReturn400() {
            // Arrange
            long maxAge = schedulerSupportApiProperties.getUserTaskReport().getMaxAgeDays();
            Instant now = clock.instant();
            Instant to = now.minus(maxAge + 1, ChronoUnit.DAYS);
            Instant from = to.minus(1, ChronoUnit.DAYS);
            UserTaskReportRequest request = new UserTaskReportRequest(List.of(1L), from, to);
            HttpEntity<UserTaskReportRequest> entity = createHttpEntityWithApiKey(request);

            // Act
            ResponseEntity<ProblemDetail> response = testRestTemplate
                    .exchange(reportsUrl, HttpMethod.POST, entity, ProblemDetail.class);

            // Assert
            assertProblemDetail(response, HttpStatus.BAD_REQUEST, "Invalid Request Data");
        }

        @Test
        @DisplayName("Невалидный запрос (слишком большой batch size) -> должен вернуть 400 Bad Request")
        void getTaskReports_whenBatchSizeIsTooLarge_shouldReturn400() {
            // Arrange
            int maxSize = schedulerSupportApiProperties.getUserTaskReport().getMaxBatchSize();
            List<Long> largeList = LongStream.range(1, maxSize + 2).boxed().toList();
            UserTaskReportRequest request = new UserTaskReportRequest(largeList,
                    clock.instant().minus(1, ChronoUnit.DAYS), clock.instant());
            HttpEntity<UserTaskReportRequest> entity = createHttpEntityWithApiKey(request);

            // Act
            ResponseEntity<ProblemDetail> response = testRestTemplate
                    .exchange(reportsUrl, HttpMethod.POST, entity, ProblemDetail.class);

            // Assert
            assertProblemDetail(response, HttpStatus.BAD_REQUEST, "Invalid Request Data");
        }
    }
}