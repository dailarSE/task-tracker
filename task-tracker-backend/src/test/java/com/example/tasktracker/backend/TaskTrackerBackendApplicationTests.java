package com.example.tasktracker.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("ci")
@Testcontainers
class TaskTrackerBackendApplicationIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:17.4-alpine");

    @Test
    void contextLoads() {
    }

}
