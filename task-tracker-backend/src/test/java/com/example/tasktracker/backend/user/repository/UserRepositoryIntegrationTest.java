package com.example.tasktracker.backend.user.repository;

import com.example.tasktracker.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {
    @Autowired
    private UserRepository userRepository;
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer=
            new PostgreSQLContainer<>("postgres:17.4-alpine");

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void whenSaveNewUser_thenSuccess() {
        User user = new User(null, "test@example.com", "password123", null, null);
        User savedUser = userRepository.save(user);

        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }
}