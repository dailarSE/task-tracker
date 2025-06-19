package com.example.tasktracker.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("ci")
class TaskTrackerSchedulerApplicationIT {

    @Test
    @DisplayName("Контекст приложения должен успешно загружаться")
    void contextLoads() {}
}