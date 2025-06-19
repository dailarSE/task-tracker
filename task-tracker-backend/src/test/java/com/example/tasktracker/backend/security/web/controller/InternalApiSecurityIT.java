package com.example.tasktracker.backend.security.web.controller;

import com.example.tasktracker.backend.security.filter.ApiKeyAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Value("${app.security.api-key.valid-keys[0]}")
    private String validApiKey; // Получаем ключ из application-ci.yml

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/internal";
    }

    @Test
    @DisplayName("Запрос к /internal с валидным API ключом -> должен вернуть 200 OK")
    void requestToInternal_withValidApiKey_shouldReturn200() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthenticationFilter.API_KEY_HEADER_NAME, validApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Act
        ResponseEntity<String> response = testRestTemplate.exchange(
                baseUrl + "/test", HttpMethod.GET, entity, String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Internal API endpoint reached successfully.");
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
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid Credentials"); // Ожидаем ошибку от AuthSvc
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
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid Credentials");
    }

    @Test
    @DisplayName("Запрос к публичному API с API ключом -> должен вернуть 401 (т.к. нужен JWT)")
    void requestToPublicApi_withApiKey_shouldReturn401() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthenticationFilter.API_KEY_HEADER_NAME, validApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String publicApiUrl = "http://localhost:" + port + "/api/v1/users/me";

        // Act
        ResponseEntity<ProblemDetail> response = testRestTemplate.exchange(
                publicApiUrl, HttpMethod.GET, entity, ProblemDetail.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getTitle()).isEqualTo("Authentication Required"); // Ожидаем ошибку от JWT-цепочки
    }
}