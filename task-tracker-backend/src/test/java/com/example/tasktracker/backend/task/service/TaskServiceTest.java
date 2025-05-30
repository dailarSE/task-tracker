package com.example.tasktracker.backend.task.service;

import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.dto.TaskStatusUpdateRequest;
import com.example.tasktracker.backend.task.dto.TaskUpdateRequest;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.task.exception.TaskNotFoundException;
import com.example.tasktracker.backend.task.repository.TaskRepository;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException; // Для TC_CS_CREATE_02
import jakarta.persistence.EntityNotFoundException; // Для cause в JpaObjectRetrievalFailureException


import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository mockTaskRepository;
    @Mock
    private UserRepository mockUserRepository;
    @Mock
    private Clock mockClock;

    @InjectMocks
    private TaskService taskService;

    // --- Общие тестовые данные и вспомогательные методы ---
    private static final Long DEFAULT_USER_ID = 1L;
    private static final Long DEFAULT_TASK_ID = 10L;
    private static final String DEFAULT_TASK_TITLE = "Test Task";
    private static final String DEFAULT_TASK_DESCRIPTION = "Test Description";
    private static final Instant FIXED_INSTANT_NOW = Instant.parse("2025-01-01T12:00:00Z");

    private User mockUserReference;

    @Captor
    private ArgumentCaptor<Task> taskArgumentCaptor;

    @BeforeEach
    void setUpForEachTest() {
        // Настраиваем мок Clock для всех тестов, где он может быть использован
        lenient().when(mockClock.instant()).thenReturn(FIXED_INSTANT_NOW);
        lenient().when(mockClock.getZone()).thenReturn(ZoneId.of("UTC")); // Если Clock.systemUTC() используется где-то

        // Общий мок для User, используемый в разных тестах
        mockUserReference = mock(User.class);
        lenient().when(mockUserReference.getId()).thenReturn(DEFAULT_USER_ID);
    }

    private TaskCreateRequest createValidTaskCreateRequest(String title, String description) {
        return new TaskCreateRequest(title, description);
    }

    private TaskUpdateRequest createValidTaskUpdateRequest(String title, String description, TaskStatus status) {
        return new TaskUpdateRequest(title, description, status);
    }

    private Task createTaskEntity(Long id, String title, String description, TaskStatus status, User user, Instant createdAt, Instant updatedAt, Instant completedAt) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(status);
        task.setUser(user);
        task.setCreatedAt(createdAt);
        task.setUpdatedAt(updatedAt);
        task.setCompletedAt(completedAt);
        return task;
    }


    // =====================================================================================
    // == Тесты для метода createTask(TaskCreateRequest, Long)
    // =====================================================================================
    @Nested
    @DisplayName("createTask Tests")
    class CreateTaskTests {
        @Test
        @DisplayName("TC_CS_CREATE_01: Успешное создание задачи (с description)")
        void createTask_whenValidRequestWithDescription_shouldSaveAndReturnCorrectResponse() {
            // Arrange
            TaskCreateRequest request = createValidTaskCreateRequest(DEFAULT_TASK_TITLE, DEFAULT_TASK_DESCRIPTION);
            when(mockUserRepository.getReferenceById(DEFAULT_USER_ID)).thenReturn(mockUserReference);

            Task savedTaskEntity = createTaskEntity(DEFAULT_TASK_ID, DEFAULT_TASK_TITLE, DEFAULT_TASK_DESCRIPTION, TaskStatus.PENDING,
                    mockUserReference, FIXED_INSTANT_NOW, FIXED_INSTANT_NOW, null);
            when(mockTaskRepository.save(any(Task.class))).thenReturn(savedTaskEntity);

            // Act
            TaskResponse response = taskService.createTask(request, DEFAULT_USER_ID);

            // Assert
            verify(mockUserRepository).getReferenceById(DEFAULT_USER_ID);
            verify(mockTaskRepository).save(taskArgumentCaptor.capture());
            Task capturedTask = taskArgumentCaptor.getValue();

            assertThat(capturedTask.getTitle()).isEqualTo(DEFAULT_TASK_TITLE);
            assertThat(capturedTask.getDescription()).isEqualTo(DEFAULT_TASK_DESCRIPTION);
            assertThat(capturedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(capturedTask.getUser()).isSameAs(mockUserReference);
            assertThat(capturedTask.getCreatedAt()).isEqualTo(FIXED_INSTANT_NOW);
            assertThat(capturedTask.getUpdatedAt()).isEqualTo(FIXED_INSTANT_NOW);
            assertThat(capturedTask.getCompletedAt()).isNull();

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(DEFAULT_TASK_ID);
            assertThat(response.getTitle()).isEqualTo(DEFAULT_TASK_TITLE);
            assertThat(response.getDescription()).isEqualTo(DEFAULT_TASK_DESCRIPTION);
            assertThat(response.getUserId()).isEqualTo(DEFAULT_USER_ID);
        }

        @Test
        @DisplayName("TC_CS_CREATE_01: Успешное создание задачи (без description)")
        void createTask_whenValidRequestWithoutDescription_shouldSaveAndReturnCorrectResponse() {
            // Arrange
            TaskCreateRequest request = createValidTaskCreateRequest(DEFAULT_TASK_TITLE, null);
            when(mockUserRepository.getReferenceById(DEFAULT_USER_ID)).thenReturn(mockUserReference);

            Task savedTaskEntity = createTaskEntity(DEFAULT_TASK_ID, DEFAULT_TASK_TITLE, null, TaskStatus.PENDING,
                    mockUserReference, FIXED_INSTANT_NOW, FIXED_INSTANT_NOW, null);
            when(mockTaskRepository.save(any(Task.class))).thenReturn(savedTaskEntity);

            // Act
            TaskResponse response = taskService.createTask(request, DEFAULT_USER_ID);

            // Assert
            verify(mockTaskRepository).save(taskArgumentCaptor.capture());
            Task capturedTask = taskArgumentCaptor.getValue();
            assertThat(capturedTask.getDescription()).isNull();

            assertThat(response.getDescription()).isNull();
            assertThat(response.getTitle()).isEqualTo(DEFAULT_TASK_TITLE);
        }

        @Test
        @DisplayName("TC_CS_CREATE_02: Пользователь не найден")
        void createTask_whenUserNotFound_shouldThrowJpaObjectRetrievalFailureException() {
            // Arrange
            TaskCreateRequest request = createValidTaskCreateRequest(DEFAULT_TASK_TITLE, DEFAULT_TASK_DESCRIPTION);
            Long nonExistentUserId = 999L;
            EntityNotFoundException cause = new EntityNotFoundException("User not found");
            when(mockUserRepository.getReferenceById(nonExistentUserId)).thenThrow(new JpaObjectRetrievalFailureException(cause));

            // Act & Assert
            assertThatThrownBy(() -> taskService.createTask(request, nonExistentUserId))
                    .isInstanceOf(JpaObjectRetrievalFailureException.class)
                    .hasCauseInstanceOf(EntityNotFoundException.class);
            verify(mockTaskRepository, never()).save(any(Task.class));
        }

        @Test
        @DisplayName("TC_CS_CREATE_03: TaskCreateRequest равен null")
        void createTask_whenRequestIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.createTask(null, DEFAULT_USER_ID))
                    .withMessageContaining("request");
        }

        @Test
        @DisplayName("TC_CS_CREATE_04: currentUserId равен null")
        void createTask_whenCurrentUserIdIsNull_shouldThrowNullPointerException() {
            TaskCreateRequest request = createValidTaskCreateRequest(DEFAULT_TASK_TITLE, DEFAULT_TASK_DESCRIPTION);
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.createTask(request, null))
                    .withMessageContaining("currentUserId");
        }
    }

    // =====================================================================================
    // == Тесты для метода getAllTasksForCurrentUser(Long)
    // =====================================================================================
    @Nested
    @DisplayName("getAllTasksForCurrentUser Tests")
    class GetAllTasksForCurrentUserTests {
        @Test
        @DisplayName("TC_CS_GETALL_01: У пользователя есть задачи")
        void getAllTasksForCurrentUser_whenUserHasTasks_shouldReturnListOfTaskResponses() {
            // Arrange
            Task task1 = createTaskEntity(1L, "Task 1", "Desc 1", TaskStatus.PENDING, mockUserReference, FIXED_INSTANT_NOW, FIXED_INSTANT_NOW, null);
            Task task2 = createTaskEntity(2L, "Task 2", "Desc 2", TaskStatus.COMPLETED, mockUserReference, FIXED_INSTANT_NOW, FIXED_INSTANT_NOW, FIXED_INSTANT_NOW);
            when(mockTaskRepository.findAllByUserIdOrderByCreatedAtDesc(DEFAULT_USER_ID)).thenReturn(List.of(task1, task2));

            // Act
            List<TaskResponse> responses = taskService.getAllTasksForCurrentUser(DEFAULT_USER_ID);

            // Assert
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getId()).isEqualTo(task1.getId());
            assertThat(responses.get(1).getId()).isEqualTo(task2.getId());
            verify(mockTaskRepository).findAllByUserIdOrderByCreatedAtDesc(DEFAULT_USER_ID);
        }

        @Test
        @DisplayName("TC_CS_GETALL_02: У пользователя нет задач")
        void getAllTasksForCurrentUser_whenUserHasNoTasks_shouldReturnEmptyList() {
            // Arrange
            when(mockTaskRepository.findAllByUserIdOrderByCreatedAtDesc(DEFAULT_USER_ID)).thenReturn(Collections.emptyList());

            // Act
            List<TaskResponse> responses = taskService.getAllTasksForCurrentUser(DEFAULT_USER_ID);

            // Assert
            assertThat(responses).isEmpty();
            verify(mockTaskRepository).findAllByUserIdOrderByCreatedAtDesc(DEFAULT_USER_ID);
        }

        @Test
        @DisplayName("TC_CS_GETALL_03: currentUserId равен null")
        void getAllTasksForCurrentUser_whenCurrentUserIdIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.getAllTasksForCurrentUser(null))
                    .withMessageContaining("currentUserId");
        }
    }

    // =====================================================================================
    // == Тесты для метода getTaskByIdForCurrentUserOrThrow(Long, Long)
    // =====================================================================================
    @Nested
    @DisplayName("getTaskByIdForCurrentUserOrThrow Tests")
    class GetTaskByIdForCurrentUserOrThrowTests {
        @Test
        @DisplayName("TC_CS_GETBYID_01: Задача найдена и принадлежит пользователю")
        void getTaskById_whenTaskExistsAndBelongsToUser_shouldReturnTaskResponse() {
            // Arrange
            Task foundTask = createTaskEntity(DEFAULT_TASK_ID, DEFAULT_TASK_TITLE, DEFAULT_TASK_DESCRIPTION, TaskStatus.PENDING, mockUserReference, FIXED_INSTANT_NOW, FIXED_INSTANT_NOW, null);
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(Optional.of(foundTask));

            // Act
            TaskResponse response = taskService.getTaskByIdForCurrentUserOrThrow(DEFAULT_TASK_ID, DEFAULT_USER_ID);

            // Assert
            assertThat(response.getId()).isEqualTo(DEFAULT_TASK_ID);
            assertThat(response.getTitle()).isEqualTo(DEFAULT_TASK_TITLE);
            verify(mockTaskRepository).findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID);
        }

        @Test
        @DisplayName("TC_CS_GETBYID_02: Задача не найдена / не принадлежит пользователю")
        void getTaskById_whenTaskNotFoundOrNotBelongsToUser_shouldThrowTaskNotFoundException() {
            // Arrange
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> taskService.getTaskByIdForCurrentUserOrThrow(DEFAULT_TASK_ID, DEFAULT_USER_ID))
                    .isInstanceOf(TaskNotFoundException.class)
                    .satisfies(ex -> {
                        TaskNotFoundException e = (TaskNotFoundException) ex;
                        assertThat(e.getRequestedTaskId()).isEqualTo(DEFAULT_TASK_ID);
                        assertThat(e.getCurrentUserId()).isEqualTo(DEFAULT_USER_ID);
                    });
            verify(mockTaskRepository).findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID);
        }

        @Test
        @DisplayName("TC_CS_GETBYID_03: taskId равен null")
        void getTaskById_whenTaskIdIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.getTaskByIdForCurrentUserOrThrow(null, DEFAULT_USER_ID))
                    .withMessageContaining("taskId");
        }

        @Test
        @DisplayName("TC_CS_GETBYID_04: currentUserId равен null")
        void getTaskById_whenCurrentUserIdIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.getTaskByIdForCurrentUserOrThrow(DEFAULT_TASK_ID, null))
                    .withMessageContaining("currentUserId");
        }
    }

    // =====================================================================================
    // == Тесты для метода updateTaskForCurrentUserOrThrow(Long, TaskUpdateRequest, Long)
    // =====================================================================================
    @Nested
    @DisplayName("updateTaskForCurrentUserOrThrow Tests")
    class UpdateTaskForCurrentUserOrThrowTests {

        @Test
        @DisplayName("TC_CS_UPDATE_01: Успешное обновление задачи (меняем title, description, status PENDING -> COMPLETED)")
        void updateTask_whenValidRequestAndTaskExists_shouldUpdateAndReturnTaskResponse() {
            // Arrange
            TaskUpdateRequest updateRequest = createValidTaskUpdateRequest("Updated Title", "Updated Desc", TaskStatus.COMPLETED);

            Instant initialCreatedAt = FIXED_INSTANT_NOW.minusSeconds(100);
            Instant initialUpdatedAt = FIXED_INSTANT_NOW.minusSeconds(50); // Пусть updatedAt будет немного позже createdAt
            Task existingTask = createTaskEntity(
                    DEFAULT_TASK_ID,
                    DEFAULT_TASK_TITLE,
                    DEFAULT_TASK_DESCRIPTION,
                    TaskStatus.PENDING,
                    mockUserReference,
                    initialCreatedAt,
                    initialUpdatedAt,
                    null // completedAt изначально null
            );

            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(Optional.of(existingTask));

            // Act
            TaskResponse response = taskService.updateTaskForCurrentUserOrThrow(DEFAULT_TASK_ID, updateRequest, DEFAULT_USER_ID);

            // Assert
            verify(mockTaskRepository).findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID);

            assertThat(existingTask.getTitle()).isEqualTo("Updated Title");
            assertThat(existingTask.getDescription()).isEqualTo("Updated Desc");
            assertThat(existingTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(existingTask.getUpdatedAt()).isEqualTo(FIXED_INSTANT_NOW); // Установлено вручную в сервисе
            assertThat(existingTask.getCompletedAt()).isEqualTo(FIXED_INSTANT_NOW); // Установлено через updateCompletedAtBasedOnStatus
            assertThat(existingTask.getCreatedAt()).isEqualTo(initialCreatedAt); // Не должно меняться

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(DEFAULT_TASK_ID);
            assertThat(response.getTitle()).isEqualTo("Updated Title");
            assertThat(response.getDescription()).isEqualTo("Updated Desc");
            assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(response.getUserId()).isEqualTo(DEFAULT_USER_ID);
            assertThat(response.getCreatedAt()).isEqualTo(initialCreatedAt);
            assertThat(response.getUpdatedAt()).isEqualTo(FIXED_INSTANT_NOW);
            assertThat(response.getCompletedAt()).isEqualTo(FIXED_INSTANT_NOW);
        }

        @Test
        @DisplayName("TC_CS_UPDATE_02: Задача не найдена / не принадлежит пользователю")
        void updateTask_whenTaskNotFoundOrNotBelongsToUser_shouldThrowTaskNotFoundException() {
            // Arrange
            TaskUpdateRequest updateRequest = createValidTaskUpdateRequest("Upd", "Upd", TaskStatus.PENDING);
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> taskService.updateTaskForCurrentUserOrThrow(DEFAULT_TASK_ID, updateRequest, DEFAULT_USER_ID))
                    .isInstanceOf(TaskNotFoundException.class);
            verify(mockTaskRepository, never()).save(any(Task.class));
        }

        @Test
        @DisplayName("TC_CS_UPDATE_03: taskId равен null")
        void updateTask_whenTaskIdIsNull_shouldThrowNullPointerException() {
            TaskUpdateRequest request = createValidTaskUpdateRequest("T", "D", TaskStatus.PENDING);
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateTaskForCurrentUserOrThrow(null, request, DEFAULT_USER_ID))
                    .withMessageContaining("taskId");
        }

        @Test
        @DisplayName("TC_CS_UPDATE_04: TaskUpdateRequest равен null")
        void updateTask_whenRequestIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateTaskForCurrentUserOrThrow(DEFAULT_TASK_ID, null, DEFAULT_USER_ID))
                    .withMessageContaining("request");
        }

        @Test
        @DisplayName("TC_CS_UPDATE_05: currentUserId равен null")
        void updateTask_whenCurrentUserIdIsNull_shouldThrowNullPointerException() {
            TaskUpdateRequest request = createValidTaskUpdateRequest("T", "D", TaskStatus.PENDING);
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateTaskForCurrentUserOrThrow(DEFAULT_TASK_ID, request, null))
                    .withMessageContaining("currentUserId");
        }
    }

    // =====================================================================================
    // == Тесты для package-private метода updateCompletedAtBasedOnStatus(Task, TaskStatus, Instant)
    // =====================================================================================
    @Nested
    @DisplayName("updateCompletedAtBasedOnStatus Tests (package-private)")
    class UpdateCompletedAtBasedOnStatusTests {

        private Task task;

        @BeforeEach
        void setUpForThisNestedClass() {
            task = new Task(); // Простая задача для тестов этого метода
            task.setId(DEFAULT_TASK_ID);
            // Clock уже настроен в @BeforeEach внешнего класса на FIXED_INSTANT_NOW
        }

        @Test
        @DisplayName("TC_CS_PRIV_COMP_01: Старый PENDING, новый COMPLETED")
        void updateCompletedAt_whenStatusChangesToCompleted_shouldSetCompletedAt() {
            // Arrange
            task.setStatus(TaskStatus.PENDING);
            task.setCompletedAt(null); // Изначально не завершена

            // Act
            taskService.updateCompletedAtBasedOnStatus(task, TaskStatus.COMPLETED, FIXED_INSTANT_NOW);

            // Assert
            assertThat(task.getCompletedAt()).isEqualTo(FIXED_INSTANT_NOW);
        }

        @Test
        @DisplayName("TC_CS_PRIV_COMP_02: Старый COMPLETED, новый PENDING")
        void updateCompletedAt_whenStatusChangesFromCompletedToPending_shouldSetCompletedAtToNull() {
            // Arrange
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(FIXED_INSTANT_NOW.minusSeconds(10)); // Была завершена ранее

            // Act
            taskService.updateCompletedAtBasedOnStatus(task, TaskStatus.PENDING, FIXED_INSTANT_NOW);

            // Assert
            assertThat(task.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("TC_CS_PRIV_COMP_03: Старый COMPLETED, новый COMPLETED")
        void updateCompletedAt_whenStatusIsCompletedAndRemainsCompleted_shouldNotChangeCompletedAt() {
            // Arrange
            Instant previousCompletedAt = FIXED_INSTANT_NOW.minusSeconds(60);
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(previousCompletedAt);

            // Act
            taskService.updateCompletedAtBasedOnStatus(task, TaskStatus.COMPLETED, FIXED_INSTANT_NOW);

            // Assert
            assertThat(task.getCompletedAt()).isEqualTo(previousCompletedAt); // Не должно измениться
        }

        @Test
        @DisplayName("TC_CS_PRIV_COMP_04: Старый PENDING, новый PENDING")
        void updateCompletedAt_whenStatusIsPendingAndRemainsPending_shouldNotChangeCompletedAt() {
            // Arrange
            task.setStatus(TaskStatus.PENDING);
            task.setCompletedAt(null);

            // Act
            taskService.updateCompletedAtBasedOnStatus(task, TaskStatus.PENDING, FIXED_INSTANT_NOW);

            // Assert
            assertThat(task.getCompletedAt()).isNull(); // Должно остаться null
        }

        @Test
        @DisplayName("TC_CS_PRIV_COMP_05: task равен null")
        void updateCompletedAt_whenTaskIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateCompletedAtBasedOnStatus(null, TaskStatus.COMPLETED, FIXED_INSTANT_NOW))
                    .withMessageContaining("task");
        }

        @Test
        @DisplayName("TC_CS_PRIV_COMP_06: newStatus равен null")
        void updateCompletedAt_whenNewStatusIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateCompletedAtBasedOnStatus(task, null, FIXED_INSTANT_NOW))
                    .withMessageContaining("newStatus");
        }

        @Test
        @DisplayName("TC_CS_PRIV_COMP_07: timestamp равен null")
        void updateCompletedAt_whenTimestampIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateCompletedAtBasedOnStatus(task, TaskStatus.COMPLETED, null))
                    .withMessageContaining("timestamp");
        }

        @Test
        @DisplayName("TC_CS_PRIV_COMP_08: task.getStatus() (старый статус) равен null")
        void updateCompletedAt_whenTaskOldStatusIsNull_shouldThrowNullPointerException() {
            // Arrange
            task.setStatus(null); // Устанавливаем старый статус в null

            // Act & Assert
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateCompletedAtBasedOnStatus(task, TaskStatus.COMPLETED, FIXED_INSTANT_NOW))
                    .withMessage("Old status of the task cannot be null when updating completedAt.");
        }
    }

    // =====================================================================================
    // == Тесты для метода updateTaskStatusForCurrentUserOrThrow(Long, TaskStatusUpdateRequest, Long)
    // =====================================================================================
    @Nested
    @DisplayName("updateTaskStatusForCurrentUserOrThrow Tests")
    class UpdateTaskStatusForCurrentUserOrThrowTests {

        @Test
        @DisplayName("TC_CS_UPD_STATUS_01: Успешное обновление статуса PENDING -> COMPLETED")
        void updateTaskStatus_whenPendingToCompleted_shouldUpdateStatusAndCompletedAt() {
            // Arrange
            TaskStatusUpdateRequest updateRequest = new TaskStatusUpdateRequest(TaskStatus.COMPLETED);
            Task existingTask = createTaskEntity(
                    DEFAULT_TASK_ID, DEFAULT_TASK_TITLE, null, TaskStatus.PENDING,
                    mockUserReference, FIXED_INSTANT_NOW.minusSeconds(10), FIXED_INSTANT_NOW.minusSeconds(5), null
            );
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(Optional.of(existingTask));
            // Clock мокируется в @BeforeEach внешнего класса на FIXED_INSTANT_NOW

            // Act
            TaskResponse response = taskService.updateTaskStatusForCurrentUserOrThrow(DEFAULT_TASK_ID, updateRequest, DEFAULT_USER_ID);

            // Assert
            verify(mockTaskRepository).findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID);
            // Проверяем, что сущность была изменена правильно перед маппингом (save не вызывается)
            assertThat(existingTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(existingTask.getCompletedAt()).isEqualTo(FIXED_INSTANT_NOW); // Установлено текущим временем из Clock
            assertThat(existingTask.getUpdatedAt()).isEqualTo(FIXED_INSTANT_NOW);   // Обновлено

            assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(response.getCompletedAt()).isEqualTo(FIXED_INSTANT_NOW);
            assertThat(response.getUpdatedAt()).isEqualTo(FIXED_INSTANT_NOW);
        }

        @Test
        @DisplayName("TC_CS_UPD_STATUS_02: Успешное обновление статуса COMPLETED -> PENDING")
        void updateTaskStatus_whenCompletedToPending_shouldUpdateStatusAndResetCompletedAt() {
            // Arrange
            TaskStatusUpdateRequest updateRequest = new TaskStatusUpdateRequest(TaskStatus.PENDING);
            Instant previousCompletedAt = FIXED_INSTANT_NOW.minusSeconds(100);
            Task existingTask = createTaskEntity(
                    DEFAULT_TASK_ID, DEFAULT_TASK_TITLE, null, TaskStatus.COMPLETED,
                    mockUserReference, FIXED_INSTANT_NOW.minusSeconds(200), previousCompletedAt, previousCompletedAt
            );
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(Optional.of(existingTask));

            // Act
            TaskResponse response = taskService.updateTaskStatusForCurrentUserOrThrow(DEFAULT_TASK_ID, updateRequest, DEFAULT_USER_ID);

            // Assert
            assertThat(existingTask.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(existingTask.getCompletedAt()).isNull(); // Должно быть сброшено
            assertThat(existingTask.getUpdatedAt()).isEqualTo(FIXED_INSTANT_NOW);

            assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(response.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("TC_CS_UPD_STATUS_03: Статус не меняется (PENDING -> PENDING)")
        void updateTaskStatus_whenStatusDoesNotChangePending_shouldOnlyUpdateUpdatedAt() {
            // Arrange
            TaskStatusUpdateRequest updateRequest = new TaskStatusUpdateRequest(TaskStatus.PENDING);
            Task existingTask = createTaskEntity(
                    DEFAULT_TASK_ID, DEFAULT_TASK_TITLE, null, TaskStatus.PENDING,
                    mockUserReference, FIXED_INSTANT_NOW.minusSeconds(100), FIXED_INSTANT_NOW.minusSeconds(50), null
            );
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(Optional.of(existingTask));

            // Act
            TaskResponse response = taskService.updateTaskStatusForCurrentUserOrThrow(DEFAULT_TASK_ID, updateRequest, DEFAULT_USER_ID);

            // Assert
            assertThat(existingTask.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(existingTask.getCompletedAt()).isNull();
            assertThat(existingTask.getUpdatedAt()).isEqualTo(FIXED_INSTANT_NOW); // updatedAt все равно обновляется

            assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(response.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("TC_CS_UPD_STATUS_04: Статус не меняется (COMPLETED -> COMPLETED)")
        void updateTaskStatus_whenStatusDoesNotChangeCompleted_shouldOnlyUpdateUpdatedAt() {
            // Arrange
            TaskStatusUpdateRequest updateRequest = new TaskStatusUpdateRequest(TaskStatus.COMPLETED);
            Instant previousCompletedAt = FIXED_INSTANT_NOW.minusSeconds(100);
            Task existingTask = createTaskEntity(
                    DEFAULT_TASK_ID, DEFAULT_TASK_TITLE, null, TaskStatus.COMPLETED,
                    mockUserReference, FIXED_INSTANT_NOW.minusSeconds(200), previousCompletedAt, previousCompletedAt
            );
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(Optional.of(existingTask));

            // Act
            TaskResponse response = taskService.updateTaskStatusForCurrentUserOrThrow(DEFAULT_TASK_ID, updateRequest, DEFAULT_USER_ID);

            // Assert
            assertThat(existingTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(existingTask.getCompletedAt()).isEqualTo(previousCompletedAt); // Не должно измениться
            assertThat(existingTask.getUpdatedAt()).isEqualTo(FIXED_INSTANT_NOW); // updatedAt обновляется

            assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(response.getCompletedAt()).isEqualTo(previousCompletedAt);
        }


        @Test
        @DisplayName("TC_CS_UPD_STATUS_05: Задача не найдена / не принадлежит пользователю -> должен выбросить TaskNotFoundException")
        void updateTaskStatus_whenTaskNotFound_shouldThrowTaskNotFoundException() {
            // Arrange
            TaskStatusUpdateRequest updateRequest = new TaskStatusUpdateRequest(TaskStatus.COMPLETED);
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> taskService.updateTaskStatusForCurrentUserOrThrow(DEFAULT_TASK_ID, updateRequest, DEFAULT_USER_ID))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("TC_CS_UPD_STATUS_06: TaskStatusUpdateRequest null -> должен выбросить NullPointerException")
        void updateTaskStatus_whenRequestIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateTaskStatusForCurrentUserOrThrow(DEFAULT_TASK_ID, null, DEFAULT_USER_ID))
                    .withMessageContaining("request");
        }

        @Test
        @DisplayName("TC_CS_UPD_STATUS_07: taskId null -> должен выбросить NullPointerException")
        void updateTaskStatus_whenTaskIdIsNull_shouldThrowNullPointerException() {
            // Arrange
            TaskStatusUpdateRequest validRequest = new TaskStatusUpdateRequest(TaskStatus.PENDING);

            // Act & Assert
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateTaskStatusForCurrentUserOrThrow(null, validRequest, DEFAULT_USER_ID))
                    .withMessageContaining("taskId"); // Проверяем имя параметра из @NonNull
        }

        @Test
        @DisplayName("TC_CS_UPD_STATUS_08: currentUserId null -> должен выбросить NullPointerException")
        void updateTaskStatus_whenCurrentUserIdIsNull_shouldThrowNullPointerException() {
            // Arrange
            TaskStatusUpdateRequest validRequest = new TaskStatusUpdateRequest(TaskStatus.PENDING);

            // Act & Assert
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.updateTaskStatusForCurrentUserOrThrow(DEFAULT_TASK_ID, validRequest, null))
                    .withMessageContaining("currentUserId"); // Проверяем имя параметра из @NonNull
        }
    }

    // =====================================================================================
    // == Тесты для метода deleteTaskForCurrentUserOrThrow(Long, Long)
    // =====================================================================================
    @Nested
    @DisplayName("deleteTaskForCurrentUserOrThrow Tests")
    class DeleteTaskForCurrentUserOrThrowTests {

        @Test
        @DisplayName("TC_CS_DELETE_01: Успешное удаление задачи (репозиторий возвращает 1)")
        void deleteTask_whenTaskExistsAndBelongsToUser_shouldCallRepositoryDeleteAndNotThrow() {
            // Arrange
            when(mockTaskRepository.deleteByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(1);

            // Act & Assert
            assertThatCode(() -> taskService.deleteTaskForCurrentUserOrThrow(DEFAULT_TASK_ID, DEFAULT_USER_ID))
                    .doesNotThrowAnyException();

            verify(mockTaskRepository).deleteByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID);
        }

        @Test
        @DisplayName("TC_CS_DELETE_02: Задача не найдена / не принадлежит (репозиторий возвращает 0) -> должен выбросить TaskNotFoundException")
        void deleteTask_whenTaskNotFoundOrNotBelongsToUser_shouldThrowTaskNotFoundException() {
            // Arrange
            when(mockTaskRepository.deleteByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID)).thenReturn(0);

            // Act & Assert
            assertThatThrownBy(() -> taskService.deleteTaskForCurrentUserOrThrow(DEFAULT_TASK_ID, DEFAULT_USER_ID))
                    .isInstanceOf(TaskNotFoundException.class)
                    .satisfies(ex -> {
                        TaskNotFoundException e = (TaskNotFoundException) ex;
                        assertThat(e.getRequestedTaskId()).isEqualTo(DEFAULT_TASK_ID);
                        assertThat(e.getCurrentUserId()).isEqualTo(DEFAULT_USER_ID);
                    });

            verify(mockTaskRepository).deleteByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID);
        }

        @Test
        @DisplayName("TC_CS_DELETE_03: taskId равен null -> должен выбросить NullPointerException")
        void deleteTask_whenTaskIdIsNull_shouldThrowNullPointerException() {
            // Act & Assert
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.deleteTaskForCurrentUserOrThrow(null, DEFAULT_USER_ID))
                    .withMessageContaining("taskId"); // Проверяем имя параметра из @NonNull
        }

        @Test
        @DisplayName("TC_CS_DELETE_04: currentUserId равен null -> должен выбросить NullPointerException")
        void deleteTask_whenCurrentUserIdIsNull_shouldThrowNullPointerException() {
            // Act & Assert
            assertThatNullPointerException()
                    .isThrownBy(() -> taskService.deleteTaskForCurrentUserOrThrow(DEFAULT_TASK_ID, null))
                    .withMessageContaining("currentUserId"); // Проверяем имя параметра из @NonNull
        }
    }
}