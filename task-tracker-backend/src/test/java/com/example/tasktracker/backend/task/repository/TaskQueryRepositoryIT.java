package com.example.tasktracker.backend.task.repository;

import com.example.tasktracker.backend.internal.scheduler.dto.TaskInfo;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReport;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci")
@DisplayName("Интеграционные тесты для TaskQueryRepository")
class TaskQueryRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.4-alpine");

    @Autowired private TaskQueryRepository taskQueryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private Clock clock;

    private User user1_mixed, user2_pendingOnly, user3_noRelevantTasks;

    @BeforeEach
    void setupTestData() {
        // Создаем пользователей
        user1_mixed = userRepository.save(new User(null, "user1@test.com", "p", null, null));
        user2_pendingOnly = userRepository.save(new User(null, "user2@test.com", "p", null, null));
        user3_noRelevantTasks = userRepository.save(new User(null, "user3@test.com", "p", null, null));

        Instant now = clock.instant();

        // -- Задачи для user1 (смешанный случай) --
        // 6 несделанных, чтобы проверить лимит в 5 и сортировку (старые первыми)
        for (int i = 0; i < 6; i++) {
            createTask("U1-Pending-" + i, user1_mixed, TaskStatus.PENDING, now.minus(i, ChronoUnit.DAYS), null);
        }
        // 7 недавно сделанных, чтобы проверить отсутствие лимита и сортировку (новые первыми)
        for (int i = 0; i < 7; i++) {
            createTask("U1-Completed-Recent-" + i, user1_mixed, TaskStatus.COMPLETED, now, now.minus(i, ChronoUnit.HOURS));
        }

        // -- Задачи для user2 (только PENDING) --
        createTask("U2-Pending-Old", user2_pendingOnly, TaskStatus.PENDING, now.minus(2, ChronoUnit.DAYS), null);
        createTask("U2-Pending-New", user2_pendingOnly, TaskStatus.PENDING, now.minus(1, ChronoUnit.DAYS), null);

        // -- Задачи для user3 (только старые COMPLETED) --
        createTask("U3-Completed-Old", user3_noRelevantTasks, TaskStatus.COMPLETED, now, now.minus(30, ChronoUnit.DAYS));
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    private void createTask(String title, User user, TaskStatus status, Instant createdAt, Instant completedAt) {
        Task task = new Task();
        task.setTitle(title);
        task.setUser(user);
        task.setStatus(status);
        task.setCreatedAt(createdAt);
        task.setUpdatedAt(createdAt);
        if (completedAt != null) {
            task.setCompletedAt(completedAt);
        }
        taskRepository.save(task);
    }

    private UserTaskReport findReportForUser(List<UserTaskReport> reports, Long userId) {
        return reports.stream().filter(r -> r.getUserId().equals(userId)).findFirst().orElse(null);
    }

    @Nested
    @DisplayName("Проверка полноты и корректности отчетов")
    class ReportCorrectnessTests {

        @Test
        @DisplayName("TC-1,7,8,9,10: Смешанный случай - должен вернуть корректный отчет с лимитами и сортировкой")
        void findTaskReports_forMixedCaseUser_shouldReturnCorrectlyLimitedAndSortedReport() {
            Instant from = clock.instant().minus(24, ChronoUnit.HOURS);
            Instant to = clock.instant().plus(1, ChronoUnit.MINUTES); // +1 минута для захвата now()

            List<UserTaskReport> reports = taskQueryRepository.findTaskReportsForUsers(List.of(user1_mixed.getId()), from, to);

            assertThat(reports).hasSize(1);
            UserTaskReport report = reports.get(0);

            assertThat(report.getUserId()).isEqualTo(user1_mixed.getId());
            assertThat(report.getEmail()).isEqualTo(user1_mixed.getEmail());

            assertThat(report.getTasksPending()).as("PENDING tasks should be the 5 oldest ones (created_at ASC)")
                    .hasSize(5)
                    .extracting(TaskInfo::getTitle)
                    .containsExactly("U1-Pending-5", "U1-Pending-4", "U1-Pending-3", "U1-Pending-2", "U1-Pending-1");

            assertThat(report.getTasksCompleted()).as("COMPLETED tasks should include all 7 recent ones (completed_at DESC)")
                    .hasSize(7)
                    .extracting(TaskInfo::getTitle)
                    .containsExactly("U1-Completed-Recent-0", "U1-Completed-Recent-1", "U1-Completed-Recent-2", "U1-Completed-Recent-3", "U1-Completed-Recent-4", "U1-Completed-Recent-5", "U1-Completed-Recent-6");
        }

        @Test
        @DisplayName("TC-2: Пользователь только с PENDING задачами")
        void findTaskReports_forUserWithOnlyPendingTasks_shouldReturnPendingTasksAndEmptyCompleted() {
            Instant from = clock.instant().minus(24, ChronoUnit.HOURS);
            Instant to = clock.instant().plus(1, ChronoUnit.MINUTES);

            List<UserTaskReport> reports = taskQueryRepository.findTaskReportsForUsers(List.of(user2_pendingOnly.getId()), from, to);

            assertThat(reports).hasSize(1);
            UserTaskReport report = reports.get(0);
            assertThat(report.getEmail()).isEqualTo(user2_pendingOnly.getEmail());
            assertThat(report.getTasksPending()).extracting(TaskInfo::getTitle).containsExactlyInAnyOrder("U2-Pending-Old", "U2-Pending-New");
            assertThat(report.getTasksCompleted()).isEmpty();
        }

        @Test
        @DisplayName("TC-3: Пользователь только с RECENTLY COMPLETED задачами")
        void findTaskReports_forUserWithOnlyRecentCompleted_shouldReturnCompletedTasksAndEmptyPending() {
            Instant from = clock.instant().minus(24, ChronoUnit.HOURS);
            Instant to = clock.instant().plus(1, ChronoUnit.MINUTES);

            List<UserTaskReport> reports = taskQueryRepository.findTaskReportsForUsers(List.of(user1_mixed.getId()), from, to);

            // Проверяем, что отчет по user1 содержит выполненные задачи
            UserTaskReport report1 = findReportForUser(reports, user1_mixed.getId());
            assertThat(report1).isNotNull();
            assertThat(report1.getTasksCompleted()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Проверка граничных случаев и отсутствия данных")
    class EdgeCaseTests {

        @Test
        @DisplayName("TC-4 & TC-5: Пользователи без релевантных задач не должны попадать в отчет")
        void findTaskReports_forUsersWithNoRelevantTasks_shouldReturnEmptyList() {
            Instant from = clock.instant().minus(24, ChronoUnit.HOURS);
            Instant to = clock.instant().plus(1, ChronoUnit.MINUTES);

            List<UserTaskReport> reports = taskQueryRepository.findTaskReportsForUsers(
                    List.of(user3_noRelevantTasks.getId()), from, to
            );

            assertThat(reports).isEmpty();
        }

        @Test
        @DisplayName("TC-6: Пустой список ID на входе должен вернуть пустой список")
        void findTaskReports_whenUserIdsListIsEmpty_shouldReturnEmptyList() {
            List<UserTaskReport> reports = taskQueryRepository.findTaskReportsForUsers(Collections.emptyList(), clock.instant(), clock.instant());
            assertThat(reports).isNotNull().isEmpty();
        }
    }
}