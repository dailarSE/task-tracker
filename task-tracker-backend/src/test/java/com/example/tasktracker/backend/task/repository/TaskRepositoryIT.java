package com.example.tasktracker.backend.task.repository;

import com.example.tasktracker.backend.config.AppConfig;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(AppConfig.class) // Импортируем AppConfig, чтобы получить бин Clock
@Testcontainers
@ActiveProfiles("ci")
class TaskRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:17.4-alpine");

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired // Инжектируем Clock из AppConfig
    private Clock clock;

    private User testUser1;
    private User testUser2;
    private Instant fixedTestTime;


    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        fixedTestTime = Instant.now(clock).truncatedTo(ChronoUnit.MICROS); // Фиксируем время для тестов

        testUser1 = new User(null, "user1@example.com", "password", fixedTestTime, fixedTestTime);
        testUser1 = userRepository.save(testUser1);

        testUser2 = new User(null, "user2@example.com", "password", fixedTestTime, fixedTestTime);
        testUser2 = userRepository.saveAndFlush(testUser2);
    }

    // Вспомогательный метод для создания и предварительной настройки Task
    private Task createTaskEntity(String title, String description, User owner, TaskStatus status, Instant createdAt, Instant updatedAt) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setUser(owner);
        task.setStatus(status);
        task.setCreatedAt(createdAt); // Устанавливаем вручную
        task.setUpdatedAt(updatedAt); // Устанавливаем вручную
        return task;
    }

    private Task createTaskEntity(String title, User owner, TaskStatus status) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return createTaskEntity(title, null, owner, status, now, now);
    }


    // Вспомогательные методы для ConstraintViolation остаются те же
    private ConstraintViolation<?> findViolationByPropertyPath(ConstraintViolationException ex, String propertyPath) {
        return ex.getConstraintViolations().stream()
                .filter(v -> v.getPropertyPath().toString().equals(propertyPath))
                .findFirst()
                .orElse(null);
    }

    private ConstraintViolation<?> findViolationByPropertyPathAndMessageTemplate(ConstraintViolationException ex, String propertyPath, String messageTemplate) {
        return ex.getConstraintViolations().stream()
                .filter(v -> v.getPropertyPath().toString().equals(propertyPath) && v.getMessageTemplate().equals(messageTemplate))
                .findFirst()
                .orElse(null);
    }

    // =====================================================================================
    // == Тесты для метода save(Task task)
    // =====================================================================================
    @Nested
    @DisplayName("TaskRepository.save() Tests")
    class SaveTaskTests {

        @Test
        @DisplayName("TC_TR_SAVE_01: Успешное сохранение новой задачи, поля createdAt/updatedAt установлены вручную")
        void save_whenNewTask_shouldPersistAndReturnTaskWithId() {
            // Arrange
            Instant creationTime = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            Task newTask = createTaskEntity("New Task Save", "Desc", testUser1, TaskStatus.PENDING, creationTime, creationTime);

            // Act
            Task savedTask = taskRepository.save(newTask);

            // Assert
            assertThat(savedTask).isNotNull();
            assertThat(savedTask.getId()).isNotNull().isPositive();
            assertThat(savedTask.getTitle()).isEqualTo("New Task Save");
            assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(savedTask.getUser()).isEqualTo(testUser1);
            assertThat(savedTask.getCreatedAt()).isEqualTo(creationTime); // Проверяем установленное время
            assertThat(savedTask.getUpdatedAt()).isEqualTo(creationTime); // Проверяем установленное время
            assertThat(savedTask.getCompletedAt()).isNull();

            Instant nextCreationTime = Instant.now(clock).truncatedTo(ChronoUnit.MICROS).plusSeconds(1);
            Task secondTask = createTaskEntity("Second Task Save","Desc", testUser1, TaskStatus.PENDING, nextCreationTime, nextCreationTime);
            Task savedSecondTask = taskRepository.save(secondTask);
            assertThat(savedSecondTask.getId()).isGreaterThan(savedTask.getId()); // Или просто isGreaterThan
        }

        @Test
        @DisplayName("TC_TR_SAVE_02: Обновление существующей задачи должно обновить updatedAt")
        void save_whenUpdatingExistingTask_shouldUpdateFieldsAndUpdatedAt() {
            // Arrange
            Instant initialTime = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            Task task = createTaskEntity("Initial Title", "Initial Desc", testUser1, TaskStatus.PENDING, initialTime, initialTime);
            Task savedTask = taskRepository.saveAndFlush(task); // Сохраняем и синхронизируем

            // Act
            // Небольшая задержка, чтобы Instant.now(clock) гарантированно дал новое значение
            Instant updateTime = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

            Task taskToUpdate = taskRepository.findByIdAndUserId(savedTask.getId(), testUser1.getId()).orElseThrow();
            taskToUpdate.setTitle("Updated Title");
            taskToUpdate.setStatus(TaskStatus.COMPLETED);
            taskToUpdate.setUpdatedAt(updateTime); // Устанавливаем updatedAt вручную
            taskToUpdate.setCompletedAt(updateTime); // Устанавливаем completedAt вручную

            Task updatedTask = taskRepository.save(taskToUpdate);

            // Assert
            assertThat(updatedTask.getTitle()).isEqualTo("Updated Title");
            assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(updatedTask.getCreatedAt()).isEqualTo(initialTime); // createdAt не должен меняться
            assertThat(updatedTask.getUpdatedAt()).isEqualTo(updateTime);   // updatedAt должен обновиться
            assertThat(updatedTask.getCompletedAt()).isEqualTo(updateTime); // completedAt должен установиться
        }

        // Тесты на ConstraintViolation остаются актуальными, так как они проверяют ограничения JPA/БД
        // TC_TR_SAVE_03 (был save_whenUserIsNull_shouldThrowConstraintViolationException)
        @Test
        @DisplayName("TC_TR_SAVE_03: Попытка сохранить задачу без пользователя (user=null) -> ConstraintViolationException")
        void save_whenUserIsNull_shouldThrowConstraintViolationException() {
            Task taskWithNullUser = createTaskEntity("Task no user", null, null, TaskStatus.PENDING, fixedTestTime, fixedTestTime);
            // user не установлен

            assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithNullUser))
                    .isInstanceOf(ConstraintViolationException.class)
                    .satisfies(ex -> assertThat(findViolationByPropertyPath((ConstraintViolationException) ex, "user")).isNotNull());
        }

        // TC_TR_SAVE_04 (NotBlank title)
        @Test
        @DisplayName("TC_TR_SAVE_04: Попытка сохранить задачу с пустым title -> ConstraintViolationException")
        void saveAndFlush_whenTitleIsBlank_shouldThrowConstraintViolationExceptionWithMessage() {
            Task taskWithBlankTitle = createTaskEntity("", null, testUser1, TaskStatus.PENDING, fixedTestTime, fixedTestTime);

            assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithBlankTitle))
                    .isInstanceOf(ConstraintViolationException.class)
                    .satisfies(ex -> assertThat(findViolationByPropertyPathAndMessageTemplate((ConstraintViolationException) ex, "title", "{task.entity.title.notBlank}")).isNotNull());
        }

        // ... (остальные тесты на ConstraintViolation для title, description, status аналогично)
        @Test
        @DisplayName("TC_TR_SAVE_05: Попытка сохранить задачу со слишком длинным title -> ConstraintViolationException")
        void saveAndFlush_whenTitleIsTooLong_shouldThrowConstraintViolationException() {
            Task taskWithLongTitle = createTaskEntity("a".repeat(256), null, testUser1, TaskStatus.PENDING, fixedTestTime, fixedTestTime);

            assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithLongTitle))
                    .isInstanceOf(ConstraintViolationException.class)
                    .satisfies(ex -> assertThat(findViolationByPropertyPathAndMessageTemplate((ConstraintViolationException) ex, "title", "{task.entity.title.size}")).isNotNull());
        }

        @Test
        @DisplayName("TC_TR_SAVE_06: Попытка сохранить задачу со слишком длинным description -> ConstraintViolationException")
        void saveAndFlush_whenDescriptionIsTooLong_shouldThrowConstraintViolationException() {
            Task taskWithLongDescription = createTaskEntity("Valid Title", "d".repeat(1001), testUser1, TaskStatus.PENDING, fixedTestTime, fixedTestTime);

            assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithLongDescription))
                    .isInstanceOf(ConstraintViolationException.class)
                    .satisfies(ex -> assertThat(findViolationByPropertyPathAndMessageTemplate((ConstraintViolationException) ex, "description", "{task.entity.description.size}")).isNotNull());
        }

        @Test
        @DisplayName("TC_TR_SAVE_07: Description может быть null и это валидно")
        void saveAndFlush_whenDescriptionIsNull_shouldBeValid() {
            Task taskWithNullDescription = createTaskEntity("Task Null Desc", null, testUser1, TaskStatus.PENDING, fixedTestTime, fixedTestTime);
            taskWithNullDescription.setDescription(null); // Явно null

            assertThatCode(() -> taskRepository.saveAndFlush(taskWithNullDescription)).doesNotThrowAnyException();
            Optional<Task> found = taskRepository.findByIdAndUserId(taskWithNullDescription.getId(), testUser1.getId());
            assertThat(found).isPresent().hasValueSatisfying(t -> assertThat(t.getDescription()).isNull());
        }

        @Test
        @DisplayName("TC_TR_SAVE_08: Попытка сохранить задачу с status=null -> ConstraintViolationException")
        void saveAndFlush_whenStatusIsNull_shouldThrowConstraintViolationException() {
            Task taskWithNullStatus = createTaskEntity("Valid Title Status Null", null, testUser1, null, fixedTestTime, fixedTestTime);

            assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithNullStatus))
                    .isInstanceOf(ConstraintViolationException.class)
                    .satisfies(ex -> assertThat(findViolationByPropertyPath((ConstraintViolationException) ex, "status")).isNotNull());
        }
    }

    // =====================================================================================
    // == Тесты для метода findByIdAndUserId(Long taskId, Long userId)
    // =====================================================================================
    @Nested
    @DisplayName("TaskRepository.findByIdAndUserId() Tests")
    class FindByIdAndUserIdTests {
        // Тесты остаются без изменений, так как они проверяют логику чтения
        @Test
        @DisplayName("TC_TR_FINDIDUID_01: Найти существующую задачу пользователя -> должен вернуть задачу")
        void findByIdAndUserId_whenTaskExistsAndBelongsToUser_shouldReturnTask() {
            Task task1 = taskRepository.save(createTaskEntity("User1 Task1", testUser1, TaskStatus.PENDING));
            Optional<Task> foundTaskOpt = taskRepository.findByIdAndUserId(task1.getId(), testUser1.getId());
            assertThat(foundTaskOpt).isPresent().get().isEqualTo(task1);
        }

        @Test
        @DisplayName("TC_TR_FINDIDUID_02: Задача не существует -> должен вернуть пустой Optional")
        void findByIdAndUserId_whenTaskDoesNotExist_shouldReturnEmpty() {
            Optional<Task> foundTaskOpt = taskRepository.findByIdAndUserId(999L, testUser1.getId());
            assertThat(foundTaskOpt).isEmpty();
        }

        @Test
        @DisplayName("TC_TR_FINDIDUID_03: Задача принадлежит другому пользователю -> должен вернуть пустой Optional")
        void findByIdAndUserId_whenTaskBelongsToAnotherUser_shouldReturnEmpty() {
            Task taskForAnotherUser = taskRepository.save(createTaskEntity("Another User Task", testUser2, TaskStatus.PENDING));
            Optional<Task> foundTaskOpt = taskRepository.findByIdAndUserId(taskForAnotherUser.getId(), testUser1.getId());
            assertThat(foundTaskOpt).isEmpty();
        }
    }

    // =====================================================================================
    // == Тесты для метода findAllByUserId(Long userId, Pageable pageable)
    // =====================================================================================
    @Nested
    @DisplayName("TaskRepository.findAllByUserId(Pageable) Tests")
    class FindAllByUserIdPageableTests {
        // Тесты остаются без изменений
        @Test
        @DisplayName("TC_TR_FINDALLUID_P_01: Должен вернуть страницу задач только для указанного пользователя")
        void findAllByUserId_shouldReturnPagedTasksForCorrectUser() {
            taskRepository.save(createTaskEntity("U1 Task 1", testUser1, TaskStatus.PENDING));
            taskRepository.save(createTaskEntity("U1 Task 2", testUser1, TaskStatus.COMPLETED));
            taskRepository.save(createTaskEntity("U2 Task 1", testUser2, TaskStatus.PENDING));
            Pageable pageable = PageRequest.of(0, 10);

            Page<Task> resultPage = taskRepository.findAllByUserId(testUser1.getId(), pageable);

            assertThat(resultPage).isNotNull();
            assertThat(resultPage.getTotalElements()).isEqualTo(2);
            assertThat(resultPage.getContent()).hasSize(2)
                    .extracting(Task::getTitle).containsExactlyInAnyOrder("U1 Task 1", "U1 Task 2");
            assertThat(resultPage.getContent()).allMatch(task -> task.getUser().getId().equals(testUser1.getId()));
        }
        @Test
        @DisplayName("TC_TR_FINDALLUID_P_02: У пользователя нет задач -> должен вернуть пустую страницу")
        void findAllByUserId_whenUserHasNoTasks_shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Task> resultPage = taskRepository.findAllByUserId(testUser1.getId(), pageable);
            assertThat(resultPage).isNotNull().isEmpty();
        }
    }

    // =====================================================================================
    // == Тесты для метода findAllByUserIdOrderByCreatedAtDesc(Long userId)
    // =====================================================================================
    @Nested
    @DisplayName("TaskRepository.findAllByUserIdOrderByCreatedAtDesc() Tests")
    class FindAllByUserIdOrderByCreatedAtDescTests {
        // Тесты остаются без изменений, но важна установка createdAt
        @Test
        @DisplayName("TC_TR_FINDALLUID_S_01: должен вернуть задачи пользователя, отсортированные по createdAt DESC")
        void findAllByUserIdOrderByCreatedAtDesc_shouldReturnUserTasksSorted() {
            Instant time1 = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            Task task1User1 = createTaskEntity("U1 Task 1 Old", null, testUser1, TaskStatus.PENDING, time1, time1);
            taskRepository.saveAndFlush(task1User1);

            Instant time2 = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            Task task2User1 = createTaskEntity("U1 Task 2 New", null, testUser1, TaskStatus.COMPLETED, time2, time2);
            taskRepository.saveAndFlush(task2User1);

            Instant time3 = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            taskRepository.saveAndFlush(createTaskEntity("U2 Task 1", null, testUser2, TaskStatus.PENDING, time3, time3));

            List<Task> resultTasksUser1 = taskRepository.findAllByUserIdOrderByCreatedAtDesc(testUser1.getId());

            assertThat(resultTasksUser1).isNotNull().hasSize(2)
                    .extracting(Task::getId).containsExactly(task2User1.getId(), task1User1.getId());
        }

        @Test
        @DisplayName("TC_TR_FINDALLUID_S_02: У пользователя нет задач -> должен вернуть пустой список")
        void findAllByUserIdOrderByCreatedAtDesc_whenUserHasNoTasks_shouldReturnEmptyList() {
            List<Task> resultTasks = taskRepository.findAllByUserIdOrderByCreatedAtDesc(testUser1.getId());
            assertThat(resultTasks).isNotNull().isEmpty();
        }
    }

    // =====================================================================================
    // == Тесты для метода findAllByUserIdAndStatus(Long userId, TaskStatus status, Pageable pageable)
    // =====================================================================================
    @Nested
    @DisplayName("TaskRepository.findAllByUserIdAndStatus() Tests")
    class FindAllByUserIdAndStatusTests {
        // Тесты остаются без изменений
        @Test
        @DisplayName("TC_TR_FINDALLUIDST_01: Должен вернуть задачи пользователя с указанным статусом")
        void findAllByUserIdAndStatus_shouldReturnFilteredTasks() {
            taskRepository.save(createTaskEntity("U1 Pending 1", testUser1, TaskStatus.PENDING));
            taskRepository.save(createTaskEntity("U1 Completed 1", testUser1, TaskStatus.COMPLETED));
            taskRepository.save(createTaskEntity("U1 Pending 2", testUser1, TaskStatus.PENDING));
            Pageable pageable = PageRequest.of(0, 10);

            Page<Task> pendingTasks = taskRepository.findAllByUserIdAndStatus(testUser1.getId(), TaskStatus.PENDING, pageable);
            assertThat(pendingTasks.getTotalElements()).isEqualTo(2);

            Page<Task> completedTasks = taskRepository.findAllByUserIdAndStatus(testUser1.getId(), TaskStatus.COMPLETED, pageable);
            assertThat(completedTasks.getTotalElements()).isEqualTo(1);
        }
    }

    // =====================================================================================
    // == Тесты для метода deleteByIdAndUserId(Long taskId, Long userId)
    // =====================================================================================
    @Nested
    @DisplayName("TaskRepository.deleteByIdAndUserId() Tests")
    class DeleteByIdAndUserIdTests {
        // Тесты остаются без изменений
        @Test
        @DisplayName("TC_TR_DELETE_01: Удалить существующую задачу пользователя -> должен вернуть 1 и удалить задачу")
        void deleteByIdAndUserId_whenTaskExistsAndBelongsToUser_shouldReturn1AndRemoveTask() {
            Task task = taskRepository.save(createTaskEntity("To Delete", testUser1, TaskStatus.PENDING));
            Long taskId = task.getId();
            int deletedCount = taskRepository.deleteByIdAndUserId(taskId, testUser1.getId());
            assertThat(deletedCount).isEqualTo(1);
            assertThat(taskRepository.findByIdAndUserId(taskId, testUser1.getId())).isEmpty();
        }

        @Test
        @DisplayName("TC_TR_DELETE_02: Попытка удалить задачу другого пользователя -> должен вернуть 0")
        void deleteByIdAndUserId_whenTaskBelongsToAnotherUser_shouldReturn0AndNotRemoveTask() {
            Task taskOfAnotherUser = taskRepository.save(createTaskEntity("Another's Task", testUser2, TaskStatus.PENDING));
            int deletedCount = taskRepository.deleteByIdAndUserId(taskOfAnotherUser.getId(), testUser1.getId());
            assertThat(deletedCount).isEqualTo(0);
            assertThat(taskRepository.findByIdAndUserId(taskOfAnotherUser.getId(), testUser2.getId())).isPresent();
        }

        @Test
        @DisplayName("TC_TR_DELETE_03: Попытка удалить несуществующую задачу -> должен вернуть 0")
        void deleteByIdAndUserId_whenTaskDoesNotExist_shouldReturn0() {
            int deletedCount = taskRepository.deleteByIdAndUserId(999L, testUser1.getId());
            assertThat(deletedCount).isEqualTo(0);
        }
    }

    // =====================================================================================
    // == Тесты для метода existsByIdAndUserId(Long taskId, Long userId)
    // =====================================================================================
    @Nested
    @DisplayName("TaskRepository.existsByIdAndUserId() Tests")
    class ExistsByIdAndUserIdTests {
        // Тесты остаются без изменений
        @Test
        @DisplayName("TC_TR_EXISTS_01: Проверить существование задачи пользователя -> должен вернуть true")
        void existsByIdAndUserId_whenTaskExistsAndBelongsToUser_shouldReturnTrue() {
            Task task = taskRepository.save(createTaskEntity("Existing Task", testUser1, TaskStatus.PENDING));
            boolean exists = taskRepository.existsByIdAndUserId(task.getId(), testUser1.getId());
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("TC_TR_EXISTS_02: Задача не существует -> должен вернуть false")
        void existsByIdAndUserId_whenTaskDoesNotExist_shouldReturnFalse() {
            boolean exists = taskRepository.existsByIdAndUserId(999L, testUser1.getId());
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("TC_TR_EXISTS_03: Задача принадлежит другому пользователю -> должен вернуть false")
        void existsByIdAndUserId_whenTaskBelongsToAnotherUser_shouldReturnFalse() {
            Task taskForAnotherUser = taskRepository.save(createTaskEntity("Another's Existing Task", testUser2, TaskStatus.PENDING));
            boolean exists = taskRepository.existsByIdAndUserId(taskForAnotherUser.getId(), testUser1.getId());
            assertThat(exists).isFalse();
        }
    }
}