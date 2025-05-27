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

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(AppConfig.class)
@Testcontainers
@ActiveProfiles("ci") // Используем профиль ci для консистентности с Jenkins
class TaskRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:17.4-alpine");

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll(); // Это удалит и задачи из-за onDelete: CASCADE

        testUser = userRepository.save(new User(null, "testuser@example.com", "password", null, null));
        anotherUser = userRepository.save(new User(null, "anotheruser@example.com", "password", null, null));

        userRepository.flush();
    }

    private Task createTaskEntity(String title, User owner, TaskStatus status) {
        Task task = new Task();
        task.setTitle(title);
        task.setUser(owner);
        task.setStatus(status);

        return task;
    }

    // Вспомогательный метод для поиска ConstraintViolation по propertyPath
    private ConstraintViolation<?> findViolationByPropertyPath(ConstraintViolationException ex, String propertyPath) {
        return ex.getConstraintViolations().stream()
                .filter(v -> v.getPropertyPath().toString().equals(propertyPath))
                .findFirst()
                .orElse(null);
    }

    // Вспомогательный метод для поиска ConstraintViolation по propertyPath и шаблону сообщения (ключу)
    private ConstraintViolation<?> findViolationByPropertyPathAndMessageTemplate(ConstraintViolationException ex, String propertyPath, String messageTemplate) {
        return ex.getConstraintViolations().stream()
                .filter(v -> v.getPropertyPath().toString().equals(propertyPath) && v.getMessageTemplate().equals(messageTemplate))
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("save: Сохранение новой задачи должно быть успешным и все поля должны быть установлены")
    void save_whenNewTask_shouldPersistAndSetAuditFieldsAndId() {
        // Arrange
        Task newTask = createTaskEntity("New Task Title", testUser, TaskStatus.PENDING);

        // Act
        Task savedTask = taskRepository.save(newTask);

        // Assert
        assertThat(savedTask).isNotNull();
        assertThat(savedTask.getId()).isNotNull().isPositive();
        assertThat(savedTask.getTitle()).isEqualTo("New Task Title");
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(savedTask.getUser()).isEqualTo(testUser);
        assertThat(savedTask.getCreatedAt()).isNotNull();
        assertThat(savedTask.getUpdatedAt()).isNotNull();
        assertThat(savedTask.getUpdatedAt()).isCloseTo(savedTask.getCreatedAt(), within(100, ChronoUnit.MILLIS));
        assertThat(savedTask.getCompletedAt()).isNull();

        // Проверка, что ID был сгенерирован через sequence
        Task secondTask = taskRepository.save(createTaskEntity("Second Task", testUser, TaskStatus.PENDING));
        assertThat(secondTask.getId()).isGreaterThan(savedTask.getId());
    }

    @Test
    @DisplayName("save: Попытка сохранить задачу без пользователя (user=null) должна вызвать ConstraintViolationException")
    void save_whenUserIsNull_shouldThrowConstraintViolationException() {
        Task taskWithNullUser = new Task();
        taskWithNullUser.setTitle("Task with no user");
        taskWithNullUser.setStatus(TaskStatus.PENDING);
        // user остается null

        assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithNullUser))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(ex -> {
                    ConstraintViolationException cve = (ConstraintViolationException) ex;
                    assertThat(cve.getConstraintViolations()).hasSize(1);
                    ConstraintViolation<?> violation = findViolationByPropertyPath(cve, "user");
                    assertThat(violation).isNotNull();
                });
    }


    @Test
    @DisplayName("findByIdAndUserId: Найти существующую задачу пользователя -> должен вернуть задачу")
    void findByIdAndUserId_whenTaskExistsAndBelongsToUser_shouldReturnTask() {
        // Arrange
        Task task1 = taskRepository.save(createTaskEntity("User1 Task1", testUser, TaskStatus.PENDING));

        // Act
        Optional<Task> foundTaskOpt = taskRepository.findByIdAndUserId(task1.getId(), testUser.getId());

        // Assert
        assertThat(foundTaskOpt).isPresent();
        assertThat(foundTaskOpt.get().getId()).isEqualTo(task1.getId());
        assertThat(foundTaskOpt.get().getTitle()).isEqualTo("User1 Task1");
    }

    @Test
    @DisplayName("findByIdAndUserId: Задача не существует -> должен вернуть пустой Optional")
    void findByIdAndUserId_whenTaskDoesNotExist_shouldReturnEmpty() {
        Optional<Task> foundTaskOpt = taskRepository.findByIdAndUserId(999L, testUser.getId());
        assertThat(foundTaskOpt).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId: Задача принадлежит другому пользователю -> должен вернуть пустой Optional")
    void findByIdAndUserId_whenTaskBelongsToAnotherUser_shouldReturnEmpty() {
        Task taskForAnotherUser = taskRepository.save(createTaskEntity("Another User Task", anotherUser, TaskStatus.PENDING));
        Optional<Task> foundTaskOpt = taskRepository.findByIdAndUserId(taskForAnotherUser.getId(), testUser.getId());
        assertThat(foundTaskOpt).isEmpty();
    }

    @Test
    @DisplayName("findAllByUserId: Должен вернуть страницу задач только для указанного пользователя")
    void findAllByUserId_shouldReturnPagedTasksForCorrectUser() {
        // Arrange
        taskRepository.save(createTaskEntity("U1 Task 1", testUser, TaskStatus.PENDING));
        taskRepository.save(createTaskEntity("U1 Task 2", testUser, TaskStatus.COMPLETED));
        taskRepository.save(createTaskEntity("U2 Task 1", anotherUser, TaskStatus.PENDING));
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Task> resultPage = taskRepository.findAllByUserId(testUser.getId(), pageable);

        // Assert
        assertThat(resultPage).isNotNull();
        assertThat(resultPage.getTotalElements()).isEqualTo(2);
        assertThat(resultPage.getContent()).hasSize(2)
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrder("U1 Task 1", "U1 Task 2");
        assertThat(resultPage.getContent()).allMatch(task -> task.getUser().getId().equals(testUser.getId()));
    }

    @Test
    @DisplayName("findAllByUserIdOrderByCreatedAtDesc: должен вернуть задачи пользователя, отсортированные по createdAt DESC")
    void findAllByUserIdOrderByCreatedAtDesc_shouldReturnUserTasksSorted() {
        // Arrange
        // user1 (testUser) и user2 (anotherUser) создаются в @BeforeEach

        // Задачи для testUser (user1)
        Task task1User1 = taskRepository.saveAndFlush(createTaskEntity("U1 Task 1 Old", testUser, TaskStatus.PENDING)); // Сохраняем первым, createdAt будет раньше
        // Небольшая пауза, чтобы гарантировать разное время создания

        Task task2User1 = taskRepository.save(createTaskEntity("U1 Task 2 New", testUser, TaskStatus.COMPLETED));

        // Задача для anotherUser (user2)
        taskRepository.saveAndFlush(createTaskEntity("U2 Task 1", anotherUser, TaskStatus.PENDING));

        // Act
        List<Task> resultTasksUser1 = taskRepository.findAllByUserIdOrderByCreatedAtDesc(testUser.getId());
        List<Task> resultTasksUser2 = taskRepository.findAllByUserIdOrderByCreatedAtDesc(anotherUser.getId());
        List<Task> resultTasksNonExistentUser = taskRepository.findAllByUserIdOrderByCreatedAtDesc(999L);


        // Assert
        // Для testUser
        assertThat(resultTasksUser1)
                .isNotNull()
                .hasSize(2)
                .extracting(Task::getTitle)
                .containsExactly("U1 Task 2 New", "U1 Task 1 Old"); // Проверяем порядок
        assertThat(resultTasksUser1).allMatch(task -> task.getUser().getId().equals(testUser.getId()));

        // Для anotherUser
        assertThat(resultTasksUser2)
                .isNotNull()
                .hasSize(1)
                .extracting(Task::getTitle)
                .containsExactly("U2 Task 1");

        // Для несуществующего пользователя
        assertThat(resultTasksNonExistentUser).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("findAllByUserIdAndStatus: Должен вернуть задачи пользователя с указанным статусом")
    void findAllByUserIdAndStatus_shouldReturnFilteredTasks() {
        taskRepository.save(createTaskEntity("U1 Pending 1", testUser, TaskStatus.PENDING));
        taskRepository.save(createTaskEntity("U1 Completed 1", testUser, TaskStatus.COMPLETED));
        taskRepository.save(createTaskEntity("U1 Pending 2", testUser, TaskStatus.PENDING));
        Pageable pageable = PageRequest.of(0, 10);

        Page<Task> pendingTasks = taskRepository.findAllByUserIdAndStatus(testUser.getId(), TaskStatus.PENDING, pageable);
        assertThat(pendingTasks.getTotalElements()).isEqualTo(2);
        assertThat(pendingTasks.getContent()).extracting(Task::getTitle).containsExactlyInAnyOrder("U1 Pending 1", "U1 Pending 2");

        Page<Task> completedTasks = taskRepository.findAllByUserIdAndStatus(testUser.getId(), TaskStatus.COMPLETED, pageable);
        assertThat(completedTasks.getTotalElements()).isEqualTo(1);
        assertThat(completedTasks.getContent().getFirst().getTitle()).isEqualTo("U1 Completed 1");
    }

    @Test
    @DisplayName("deleteByIdAndUserId: Удалить существующую задачу пользователя -> должен вернуть 1 и удалить задачу")
    void deleteByIdAndUserId_whenTaskExistsAndBelongsToUser_shouldReturn1AndRemoveTask() {
        Task task = taskRepository.save(createTaskEntity("To Delete", testUser, TaskStatus.PENDING));
        Long taskId = task.getId();

        int deletedCount = taskRepository.deleteByIdAndUserId(taskId, testUser.getId());

        assertThat(deletedCount).isEqualTo(1);
        assertThat(taskRepository.findByIdAndUserId(taskId, testUser.getId())).isEmpty();
    }

    @Test
    @DisplayName("deleteByIdAndUserId: Попытка удалить задачу другого пользователя -> должен вернуть 0 и не удалить задачу")
    void deleteByIdAndUserId_whenTaskBelongsToAnotherUser_shouldReturn0AndNotRemoveTask() {
        Task taskOfAnotherUser = taskRepository.save(createTaskEntity("Another's Task", anotherUser, TaskStatus.PENDING));
        Long taskId = taskOfAnotherUser.getId();

        int deletedCount = taskRepository.deleteByIdAndUserId(taskId, testUser.getId());

        assertThat(deletedCount).isEqualTo(0);
        assertThat(taskRepository.findByIdAndUserId(taskId, anotherUser.getId())).isPresent(); // Задача все еще существует
    }

    @Test
    @DisplayName("existsByIdAndUserId: Проверить существование задачи пользователя -> должен вернуть true")
    void existsByIdAndUserId_whenTaskExistsAndBelongsToUser_shouldReturnTrue() {
        Task task = taskRepository.save(createTaskEntity("Existing Task", testUser, TaskStatus.PENDING));
        boolean exists = taskRepository.existsByIdAndUserId(task.getId(), testUser.getId());
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByIdAndUserId: Задача не существует -> должен вернуть false")
    void existsByIdAndUserId_whenTaskDoesNotExist_shouldReturnFalse() {
        boolean exists = taskRepository.existsByIdAndUserId(999L, testUser.getId());
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("saveAndFlush: Попытка сохранить задачу с пустым title должна вызвать ConstraintViolationException с корректным сообщением")
    void saveAndFlush_whenTitleIsBlank_shouldThrowConstraintViolationExceptionWithMessage() {
        Task taskWithBlankTitle = new Task();
        taskWithBlankTitle.setTitle(""); // Пустой title
        taskWithBlankTitle.setUser(testUser);
        taskWithBlankTitle.setStatus(TaskStatus.PENDING);

        assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithBlankTitle))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(ex -> {
                    ConstraintViolationException cve = (ConstraintViolationException) ex;
                    // Может быть несколько нарушений, если title пустой (NotBlank и Size(min=1))
                    // Давайте проверим конкретно NotBlank
                    ConstraintViolation<?> violation = findViolationByPropertyPathAndMessageTemplate(cve, "title", "{task.entity.title.notBlank}");
                    assertThat(violation).isNotNull();
                });
    }

    @Test
    @DisplayName("saveAndFlush: Попытка сохранить задачу со слишком длинным title должна вызвать ConstraintViolationException")
    void saveAndFlush_whenTitleIsTooLong_shouldThrowConstraintViolationException() {
        Task taskWithLongTitle = new Task();
        taskWithLongTitle.setTitle("a".repeat(256)); // Длина 256, макс 255
        taskWithLongTitle.setUser(testUser);
        taskWithLongTitle.setStatus(TaskStatus.PENDING);

        assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithLongTitle))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(ex -> {
                    ConstraintViolationException cve = (ConstraintViolationException) ex;
                    ConstraintViolation<?> violation = findViolationByPropertyPathAndMessageTemplate(cve, "title", "{task.entity.title.size}");
                    assertThat(violation).isNotNull();
                });
    }

    @Test
    @DisplayName("saveAndFlush: Попытка сохранить задачу со слишком длинным description должна вызвать ConstraintViolationException")
    void saveAndFlush_whenDescriptionIsTooLong_shouldThrowConstraintViolationException() {
        Task taskWithLongDescription = new Task();
        taskWithLongDescription.setTitle("Valid Title");
        taskWithLongDescription.setDescription("d".repeat(1001)); // Длина 1001, макс 1000
        taskWithLongDescription.setUser(testUser);
        taskWithLongDescription.setStatus(TaskStatus.PENDING);

        assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithLongDescription))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(ex -> {
                    ConstraintViolationException cve = (ConstraintViolationException) ex;
                    ConstraintViolation<?> violation = findViolationByPropertyPathAndMessageTemplate(cve, "description", "{task.entity.description.size}");
                    assertThat(violation).isNotNull();
                });
    }

    @Test
    @DisplayName("saveAndFlush: Description может быть null и это валидно")
    void saveAndFlush_whenDescriptionIsNull_shouldBeValid() {
        Task taskWithNullDescription = new Task();
        taskWithNullDescription.setTitle("Task With Null Description");
        taskWithNullDescription.setDescription(null); // Явно null
        taskWithNullDescription.setUser(testUser);
        taskWithNullDescription.setStatus(TaskStatus.PENDING);

        // Ожидаем, что исключения не будет
        assertThatCode(() -> taskRepository.saveAndFlush(taskWithNullDescription))
                .doesNotThrowAnyException();

        Optional<Task> found = taskRepository.findByIdAndUserId(taskWithNullDescription.getId(), testUser.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isNull();
    }

    @Test
    @DisplayName("saveAndFlush: Попытка сохранить задачу с status=null должна вызвать ConstraintViolationException")
    void saveAndFlush_whenStatusIsNull_shouldThrowConstraintViolationException() {
        Task taskWithNullStatus = new Task();
        taskWithNullStatus.setTitle("Valid Title for Null Status Test");
        taskWithNullStatus.setUser(testUser);
        taskWithNullStatus.setStatus(null); // status = null

        assertThatThrownBy(() -> taskRepository.saveAndFlush(taskWithNullStatus))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(ex -> {
                    ConstraintViolationException cve = (ConstraintViolationException) ex;
                    ConstraintViolation<?> violation = findViolationByPropertyPath(cve, "status");
                    assertThat(violation).isNotNull();
                });
    }
}