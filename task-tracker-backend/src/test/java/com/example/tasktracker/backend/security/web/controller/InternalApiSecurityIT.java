package com.example.tasktracker.backend.security.web.controller;

import com.example.tasktracker.backend.security.apikey.ApiKeyProperties;
import com.example.tasktracker.backend.security.filter.ApiKeyAuthenticationFilter;
import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.test.util.TestJwtUtil;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci")
class InternalApiSecurityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:17.4-alpine");

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private ApiKeyProperties apiKeyProperties;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private Clock clock;

    private String baseUrl;
    private String validApiKeyForScheduler;
    private TestJwtUtil testJwtUtil;
    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        testUser = userRepository.save(new User(null, "jwt-user@test.com", passwordEncoder.encode("pass"), null, null));
        testJwtUtil = new TestJwtUtil(jwtProperties, clock);

        validApiKeyForScheduler = apiKeyProperties.getKeysToServices().entrySet().stream()
                .filter(entry -> "task-tracker-scheduler".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("API Key for 'task-tracker-scheduler' not found in test configuration"));
        baseUrl = "http://localhost:" + port + "/api/v1/internal";
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Запрос к /internal с валидным ключом и ID экземпляра -> должен вернуть 200 OK и данные сервиса")
    void requestToInternal_withValidKeyAndInstanceId_shouldReturn200AndServiceData() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthenticationFilter.API_KEY_HEADER_NAME, validApiKeyForScheduler);
        headers.set(ApiKeyAuthenticationFilter.SERVICE_INSTANCE_ID_HEADER_NAME, "scheduler-pod-1");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Act
        ResponseEntity<Map<String, String>> response = testRestTemplate.exchange(
                baseUrl + "/test", HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("serviceId", "task-tracker-scheduler")
                .containsEntry("instanceId", "scheduler-pod-1");
    }

    @Test
    @DisplayName("Запрос к /internal с валидным ключом, но без ID экземпляра -> должен вернуть 200 OK и placeholder")
    void requestToInternal_withValidKeyAndNoInstanceId_shouldReturn200AndPlaceholder() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthenticationFilter.API_KEY_HEADER_NAME, validApiKeyForScheduler);
        // Заголовок X-Service-Instance-Id не установлен
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Act
        ResponseEntity<Map<String, String>> response = testRestTemplate.exchange(
                baseUrl + "/test", HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("serviceId", "task-tracker-scheduler")
                .containsEntry("instanceId", ApiKeyAuthenticationFilter.UNKNOWN_INSTANCE_ID);
    }

    @Test
    @DisplayName("Запрос к /internal с невалидным API ключом -> должен вернуть 401 Unauthorized")
    void requestToInternal_withInvalidApiKey_shouldReturn401() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthenticationFilter.API_KEY_HEADER_NAME, "invalid-api-key-value");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Act
        ResponseEntity<ProblemDetail> response = testRestTemplate.exchange(
                baseUrl + "/test", HttpMethod.GET, entity, ProblemDetail.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getType().getPath()).contains("auth/invalid-api-key");
    }

    @Test
    @DisplayName("Запрос к /internal без API ключа -> должен вернуть 401 Unauthorized")
    void requestToInternal_withoutApiKey_shouldReturn401() {
        // Arrange
        HttpEntity<String> entity = new HttpEntity<>(new HttpHeaders());

        // Act
        ResponseEntity<ProblemDetail> response = testRestTemplate.exchange(
                baseUrl + "/test", HttpMethod.GET, entity, ProblemDetail.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getType().getPath()).contains("auth/invalid-api-key");
    }

    @Test
    @DisplayName("Запрос к /internal с валидным JWT, но без API ключа -> должен вернуть 401")
    void requestToInternal_withValidJwtButNoApiKey_shouldReturn401() {
        // Arrange
        String validJwt = testJwtUtil.generateValidToken(testUser);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwt); // Только JWT
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Act
        ResponseEntity<ProblemDetail> response = testRestTemplate.exchange(
                baseUrl + "/test", HttpMethod.GET, entity, ProblemDetail.class);

        // Assert
        // Ожидаем ошибку от ApiKey-цепочки, так как она первая для этого пути
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getType().getPath()).contains("auth/invalid-api-key");
    }

    @Test
    @DisplayName("Запрос к /internal с валидным API ключом и валидным JWT -> должен вернуть 200 OK (API ключ имеет приоритет)")
    void requestToInternal_withValidApiKeyAndValidJwt_shouldReturn200() {
        // Arrange
        String validJwt = testJwtUtil.generateValidToken(testUser);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validJwt); // JWT есть
        headers.set(ApiKeyAuthenticationFilter.API_KEY_HEADER_NAME, validApiKeyForScheduler); // И API ключ есть
        headers.set(ApiKeyAuthenticationFilter.SERVICE_INSTANCE_ID_HEADER_NAME, "mixed-auth-test-1");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Act
        ResponseEntity<Map<String, String>> response = testRestTemplate.exchange(
                baseUrl + "/test", HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

        // Assert
        // ApiKey-цепочка должна сработать и пропустить запрос. JWT должен быть проигнорирован.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("serviceId", "task-tracker-scheduler");
    }
}