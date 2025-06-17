package com.example.tasktracker.backend.task.service;

import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.dto.TaskUpdateRequest;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.task.exception.TaskNotFoundException;
import com.example.tasktracker.backend.task.repository.TaskRepository;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import com.example.tasktracker.backend.web.exception.ResourceConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

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

    @Mock private TaskRepository mockTaskRepository;
    @Mock private UserRepository mockUserRepository;
    @Mock private Clock mockClock;
    private ObjectMapper realObjectMapper = new ObjectMapper();
    private Validator realValidator = Validation.buildDefaultValidatorFactory().getValidator();
    @Mock private TaskService selfInjectedMock;

    private TaskService taskService;

    // --- Общие тестовые данные и вспомогательные методы ---
    private static final Long DEFAULT_USER_ID = 1L;
    private static final Long DEFAULT_TASK_ID = 10L;
    private static final Integer DEFAULT_VERSION = 0;
    private static final String DEFAULT_TASK_TITLE = "Test Task";
    private static final String DEFAULT_TASK_DESCRIPTION = "Test Description";
    private static final Instant FIXED_INSTANT_NOW = Instant.parse("2025-01-01T12:00:00Z");

    private User mockUserReference;

    @Captor
    private ArgumentCaptor<Task> taskArgumentCaptor;

    @BeforeEach
    void setUpForEachTest() {
        taskService = new TaskService(
                selfInjectedMock, mockTaskRepository, mockUserRepository, realObjectMapper, realValidator, mockClock
        );
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

    private Task createTaskEntity(Long id, String title, String description, TaskStatus status, User user, Integer version) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(status);
        task.setUser(user);
        task.setVersion(version);
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
                    mockUserReference, null);
            savedTaskEntity.setCreatedAt(FIXED_INSTANT_NOW);
            savedTaskEntity.setUpdatedAt(FIXED_INSTANT_NOW);
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
                    mockUserReference, null);
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
            Task task1 = createTaskEntity(1L, "Task 1", "Desc 1", TaskStatus.PENDING, mockUserReference, 0);
            Task task2 = createTaskEntity(2L, "Task 2", "Desc 2", TaskStatus.COMPLETED, mockUserReference,0);
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
            Task foundTask = createTaskEntity(DEFAULT_TASK_ID, DEFAULT_TASK_TITLE, DEFAULT_TASK_DESCRIPTION, TaskStatus.PENDING, mockUserReference, null);
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

    // =====================================================================
    // == Тесты для updateTaskForCurrentUserOrThrow (PUT)
    // =====================================================================
    @Nested
    @DisplayName("updateTaskForCurrentUserOrThrow Tests (PUT)")
    class UpdateTaskTests {

        @Test
        @DisplayName("Успешное обновление -> должен вызвать внутренний транзакционный метод и вернуть DTO")
        void updateTask_whenSuccessful_shouldCallInternalMethodAndReturnDto() {
            // Arrange
            TaskUpdateRequest request = new TaskUpdateRequest("T", "D", TaskStatus.PENDING, DEFAULT_VERSION);
            Task updatedTask = createTaskEntity(DEFAULT_TASK_ID, "T", "D", TaskStatus.PENDING, mockUserReference, 1);

            when(selfInjectedMock.doUpdateTaskInTransaction(DEFAULT_TASK_ID, request, DEFAULT_USER_ID))
                    .thenReturn(updatedTask);

            // Act
            TaskResponse response = taskService.updateTaskForCurrentUserOrThrow(DEFAULT_TASK_ID, request, DEFAULT_USER_ID);

            // Assert
            verify(selfInjectedMock).doUpdateTaskInTransaction(DEFAULT_TASK_ID, request, DEFAULT_USER_ID);
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(updatedTask.getId());
        }

        @Test
        @DisplayName("Конфликт блокировки при коммите -> должен выбросить ResourceConflictException")
        void updateTask_whenOptimisticLockExceptionOnCommit_shouldThrowResourceConflictException() {
            // Arrange
            TaskUpdateRequest request = new TaskUpdateRequest("T", "D", TaskStatus.PENDING, DEFAULT_VERSION);
            when(selfInjectedMock.doUpdateTaskInTransaction(DEFAULT_TASK_ID, request, DEFAULT_USER_ID))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Task.class, DEFAULT_TASK_ID));

            // Act & Assert
            assertThatThrownBy(() -> taskService.updateTaskForCurrentUserOrThrow(DEFAULT_TASK_ID, request, DEFAULT_USER_ID))
                    .isInstanceOf(ResourceConflictException.class)
                    .hasFieldOrPropertyWithValue("conflictingResourceId", DEFAULT_TASK_ID);
        }

        // Тесты для doUpdateTaskInTransaction
        @Test
        @DisplayName("[Internal] Несовпадение версии -> должен выбросить ResourceConflictException")
        void doUpdateTask_whenVersionMismatch_shouldThrowResourceConflictException() {
            // Arrange
            Task existingTask = new Task();
            existingTask.setVersion(1); // Версия в БД - 1
            TaskUpdateRequest request = new TaskUpdateRequest("T", "D", TaskStatus.PENDING, 0); // Клиент шлет старую версию 0

            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID))
                    .thenReturn(Optional.of(existingTask));

            // Act & Assert
            assertThatThrownBy(() -> taskService.doUpdateTaskInTransaction(DEFAULT_TASK_ID, request, DEFAULT_USER_ID))
                    .isInstanceOf(ResourceConflictException.class);
        }
    }

    // =====================================================================
    // == Тесты для patchTask (PATCH)
    // =====================================================================
    @Nested
    @DisplayName("patchTask Tests (PATCH)")
    class PatchTaskTests {

        private JsonNode mockPatchNode;

        @BeforeEach
        void setup() {
            ObjectNode node = realObjectMapper.createObjectNode();
            node.put("title", "Patched Title");
            node.put("version", DEFAULT_VERSION);
            mockPatchNode = node;
        }

        @Test
        @DisplayName("Успешный PATCH -> должен вызвать внутренний транзакционный метод и вернуть DTO")
        void patchTask_whenSuccessful_shouldCallInternalMethod() {
            // Arrange
            Task patchedTask = createTaskEntity(DEFAULT_TASK_ID, "Patched", "D", TaskStatus.PENDING, mockUserReference, 1);
            when(selfInjectedMock.doPatchTaskInTransaction(DEFAULT_TASK_ID, mockPatchNode, DEFAULT_USER_ID))
                    .thenReturn(patchedTask);

            // Act
            TaskResponse response = taskService.patchTask(DEFAULT_TASK_ID, mockPatchNode, DEFAULT_USER_ID);

            // Assert
            verify(selfInjectedMock).doPatchTaskInTransaction(DEFAULT_TASK_ID, mockPatchNode, DEFAULT_USER_ID);
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(patchedTask.getId());
        }

        @Test
        @DisplayName("[Internal] Успешный PATCH -> должен обновить поля и вернуть сущность")
        void doPatchTask_whenValidPatch_shouldUpdateFieldsAndReturnEntity() {
            // Arrange
            Task existingTask = createTaskEntity(DEFAULT_TASK_ID, "Original Title", "Original Desc", TaskStatus.PENDING, mockUserReference, DEFAULT_VERSION);
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID))
                    .thenReturn(Optional.of(existingTask));

            // Act
            Task response = taskService.doPatchTaskInTransaction(DEFAULT_TASK_ID, mockPatchNode, DEFAULT_USER_ID);

            // Assert
            assertThat(response.getTitle()).isEqualTo("Patched Title");
            assertThat(response.getDescription()).isEqualTo("Original Desc");
        }

        @Test
        @DisplayName("[Internal] PATCH без версии -> должен выбросить ConstraintViolationException")
        void doPatchTask_whenVersionIsMissing_shouldThrowConstraintViolationException() {
            // Arrange
            Task existingTask = new Task();
            existingTask.setVersion(DEFAULT_VERSION);
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID))
                    .thenReturn(Optional.of(existingTask));

            ObjectNode patchWithoutVersion = new ObjectMapper().createObjectNode();
            patchWithoutVersion.put("title", "New Title");

            // Act & Assert
            assertThatThrownBy(() -> taskService.doPatchTaskInTransaction(DEFAULT_TASK_ID, patchWithoutVersion, DEFAULT_USER_ID))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("version");
        }

        @Test
        @DisplayName("[Internal] PATCH с невалидным полем -> должен выбросить ConstraintViolationException")
        void doPatchTask_whenPatchMakesEntityInvalid_shouldThrowConstraintViolationException() {
            // Arrange
            Task existingTask = new Task();
            existingTask.setVersion(DEFAULT_VERSION);
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID))
                    .thenReturn(Optional.of(existingTask));

            ObjectNode patchWithInvalidTitle = new ObjectMapper().createObjectNode();
            patchWithInvalidTitle.put("title", ""); // Пустой title невалиден
            patchWithInvalidTitle.put("version", DEFAULT_VERSION);

            // Act & Assert
            assertThatThrownBy(() -> taskService.doPatchTaskInTransaction(DEFAULT_TASK_ID, patchWithInvalidTitle, DEFAULT_USER_ID))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("title");
        }

        @Test
        @DisplayName("[Internal] PATCH с невалидным значением Enum -> должен выбросить HttpMessageConversionException")
        void doPatchTask_whenEnumIsInvalid_shouldThrowHttpMessageConversionException() {
            // Arrange
            Task existingTask = createTaskEntity(DEFAULT_TASK_ID, "T", "D", TaskStatus.PENDING, mockUserReference, DEFAULT_VERSION);
            when(mockTaskRepository.findByIdAndUserId(DEFAULT_TASK_ID, DEFAULT_USER_ID))
                    .thenReturn(Optional.of(existingTask));

            ObjectNode patchWithInvalidEnum = realObjectMapper.createObjectNode();
            patchWithInvalidEnum.put("status", "INVALID_STATUS");
            patchWithInvalidEnum.put("version", DEFAULT_VERSION);

            // Act & Assert
            assertThatThrownBy(() -> taskService.doPatchTaskInTransaction(DEFAULT_TASK_ID, patchWithInvalidEnum, DEFAULT_USER_ID))
                    .isInstanceOf(HttpMessageConversionException.class)
                    .hasMessageContaining("Invalid patch data");
        }

        @Test
        @DisplayName("Конфликт блокировки при коммите -> должен выбросить ResourceConflictException")
        void patchTask_whenOptimisticLockExceptionOnCommit_shouldBeCaughtAndRethrownAsResourceConflict() {
            // Arrange
            // Настраиваем self-инъекцию так, чтобы она выбрасывала исключение,
            // которое Spring выбрасывает при коммите транзакции для внутреннего метода
            when(selfInjectedMock.doPatchTaskInTransaction(anyLong(), any(JsonNode.class), anyLong()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Task.class, DEFAULT_TASK_ID));

            // Act & Assert
            // Проверяем, что публичный метод-обертка patchTask правильно обрабатывает это исключение
            assertThatThrownBy(() -> taskService.patchTask(DEFAULT_TASK_ID, mockPatchNode, DEFAULT_USER_ID))
                    .isInstanceOf(ResourceConflictException.class)
                    .extracting("conflictingResourceId").isEqualTo(DEFAULT_TASK_ID);

            // Убеждаемся, что вызов был делегирован внутреннему методу
            verify(selfInjectedMock).doPatchTaskInTransaction(DEFAULT_TASK_ID, mockPatchNode, DEFAULT_USER_ID);
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