package com.example.tasktracker.backend.task.service;

import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.task.repository.TaskRepository;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link TaskService}.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository mockTaskRepository;

    @Mock
    private UserRepository mockUserRepository;

    @InjectMocks
    private TaskService taskService;

    private static final Long USER_ID = 1L;
    private static final String TASK_TITLE = "New Test Task";
    private static final String TASK_DESCRIPTION = "Description for the new task.";
    private static final Long SAVED_TASK_ID = 10L;

    @Test
    @DisplayName("createTask: Валидный запрос -> должен сохранить задачу и вернуть корректный TaskResponse")
    void createTask_whenValidRequest_shouldSaveTaskAndReturnResponse() {
        // Arrange
        TaskCreateRequest request = new TaskCreateRequest(TASK_TITLE, TASK_DESCRIPTION);
        User mockUserReference = mock(User.class); // Мокаем User ссылку
        when(mockUserReference.getId()).thenReturn(USER_ID); // Для маппинга в TaskResponse

        // Настраиваем мок userRepository.getReferenceById
        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(mockUserReference);

        // Настраиваем мок taskRepository.save
        // Он должен вернуть сущность Task с установленным ID и полями аудита (симулируем это)
        Task taskToSaveArgument = new Task(); // Это будет захвачено ArgumentCaptor
        taskToSaveArgument.setTitle(TASK_TITLE);
        taskToSaveArgument.setDescription(TASK_DESCRIPTION);
        taskToSaveArgument.setStatus(TaskStatus.PENDING);
        taskToSaveArgument.setUser(mockUserReference);
        // Симулируем, что после сохранения JPA установит ID и временные метки
        Task savedTaskEntity = new Task();
        savedTaskEntity.setId(SAVED_TASK_ID);
        savedTaskEntity.setTitle(TASK_TITLE);
        savedTaskEntity.setDescription(TASK_DESCRIPTION);
        savedTaskEntity.setStatus(TaskStatus.PENDING);
        savedTaskEntity.setUser(mockUserReference);
        savedTaskEntity.setCreatedAt(Instant.now().minusSeconds(10)); // Примерные значения
        savedTaskEntity.setUpdatedAt(Instant.now().minusSeconds(5));  // Примерные значения
        savedTaskEntity.setCompletedAt(null);

        when(mockTaskRepository.save(any(Task.class))).thenReturn(savedTaskEntity);

        // Act
        TaskResponse response = taskService.createTask(request, USER_ID);

        // Assert
        // 1. Проверяем, что getReferenceById был вызван с правильным ID
        verify(mockUserRepository).getReferenceById(USER_ID);

        // 2. Захватываем аргумент, переданный в taskRepository.save()
        ArgumentCaptor<Task> taskArgumentCaptor = ArgumentCaptor.forClass(Task.class);
        verify(mockTaskRepository).save(taskArgumentCaptor.capture());
        Task capturedTask = taskArgumentCaptor.getValue();

        // 3. Проверяем поля захваченной задачи перед сохранением
        assertThat(capturedTask.getTitle()).isEqualTo(TASK_TITLE);
        assertThat(capturedTask.getDescription()).isEqualTo(TASK_DESCRIPTION);
        assertThat(capturedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(capturedTask.getUser()).isSameAs(mockUserReference); // Убеждаемся, что установлена ссылка на пользователя
        assertThat(capturedTask.getId()).isNull(); // ID еще не должен быть установлен до вызова save

        // 4. Проверяем возвращенный TaskResponse
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(SAVED_TASK_ID);
        assertThat(response.getTitle()).isEqualTo(TASK_TITLE);
        assertThat(response.getDescription()).isEqualTo(TASK_DESCRIPTION);
        assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getCreatedAt()).isEqualTo(savedTaskEntity.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(savedTaskEntity.getUpdatedAt());
        assertThat(response.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("createTask: Описание null в запросе -> задача сохраняется с null описанием")
    void createTask_whenDescriptionIsNullInRequest_shouldSaveTaskWithNullDescription() {
        // Arrange
        TaskCreateRequest requestWithNullDesc = new TaskCreateRequest(TASK_TITLE, null);
        User mockUserReference = mock(User.class);
        when(mockUserReference.getId()).thenReturn(USER_ID);
        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(mockUserReference);

        Task savedTaskEntity = new Task(); // Упрощенный мок для возврата из save
        savedTaskEntity.setId(SAVED_TASK_ID);
        savedTaskEntity.setTitle(TASK_TITLE);
        savedTaskEntity.setDescription(null); // Ожидаем null
        savedTaskEntity.setStatus(TaskStatus.PENDING);
        savedTaskEntity.setUser(mockUserReference);
        savedTaskEntity.setCreatedAt(Instant.now());
        savedTaskEntity.setUpdatedAt(Instant.now());

        when(mockTaskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task taskArg = invocation.getArgument(0);
            // Копируем поля, чтобы быть ближе к реальности, и проверяем, что description действительно null
            savedTaskEntity.setTitle(taskArg.getTitle());
            savedTaskEntity.setDescription(taskArg.getDescription()); // Это должно быть null
            savedTaskEntity.setStatus(taskArg.getStatus());
            savedTaskEntity.setUser(taskArg.getUser());
            return savedTaskEntity;
        });


        // Act
        TaskResponse response = taskService.createTask(requestWithNullDesc, USER_ID);

        // Assert
        verify(mockTaskRepository).save(argThat(task -> task.getDescription() == null));
        assertThat(response.getDescription()).isNull();
        assertThat(response.getTitle()).isEqualTo(TASK_TITLE);
    }

    @Test
    @DisplayName("createTask: Пользователь не найден (userRepository.getReferenceById выбрасывает исключение) -> должен пробросить исключение")
    void createTask_whenUserNotFound_shouldPropagateException() {
        // Arrange
        TaskCreateRequest request = new TaskCreateRequest(TASK_TITLE, TASK_DESCRIPTION);
        Long nonExistentUserId = 999L;
        // Симулируем, что getReferenceById выбрасывает EntityNotFoundException (или JpaObjectRetrievalFailureException, если используется Spring Data)
        EntityNotFoundException entityNotFoundCause = new EntityNotFoundException("User not found with ID: " + nonExistentUserId);
        JpaObjectRetrievalFailureException jpaException = new JpaObjectRetrievalFailureException(entityNotFoundCause);

        when(mockUserRepository.getReferenceById(nonExistentUserId)).thenThrow(jpaException);

        // Act & Assert
        assertThatExceptionOfType(JpaObjectRetrievalFailureException.class)
                .isThrownBy(() -> taskService.createTask(request, nonExistentUserId))
                .withCause(entityNotFoundCause)
                .withMessageContaining("User not found with ID: " + nonExistentUserId); // Проверяем, что причина сохранена

        verify(mockTaskRepository, never()).save(any(Task.class)); // save не должен вызываться
    }

    @Test
    @DisplayName("createTask: TaskCreateRequest null -> должен выбросить NullPointerException (через @NonNull)")
    void createTask_whenRequestIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> taskService.createTask(null, USER_ID))
                .withMessageContaining("request is marked non-null but is null"); // Проверяем имя параметра
    }

    @Test
    @DisplayName("createTask: currentUserId null -> должен выбросить NullPointerException (через @NonNull)")
    void createTask_whenCurrentUserIdIsNull_shouldThrowNullPointerException() {
        TaskCreateRequest request = new TaskCreateRequest(TASK_TITLE, TASK_DESCRIPTION);
        assertThatNullPointerException()
                .isThrownBy(() -> taskService.createTask(request, null))
                .withMessageContaining("currentUserId is marked non-null but is null");
    }

    @Test
    @DisplayName("getAllTasksForCurrentUser: когда репозиторий возвращает задачи -> должен вернуть смапленные TaskResponse")
    void getAllTasksForCurrentUser_whenRepositoryReturnsTasks_shouldReturnMappedTaskResponses() {
        // Arrange
        Long currentUserId = 1L;
        User mockUser = new User(); // Для TaskResponse.fromEntity нужен User в Task
        mockUser.setId(currentUserId);

        Task task1 = new Task(10L, "Task 1", "Desc 1", TaskStatus.PENDING, Instant.now(), Instant.now(), null, mockUser);
        Task task2 = new Task(11L, "Task 2", "Desc 2", TaskStatus.COMPLETED, Instant.now(), Instant.now(), Instant.now(), mockUser);
        List<Task> mockTasksFromRepo = List.of(task1, task2);

        when(mockTaskRepository.findAllByUserIdOrderByCreatedAtDesc(currentUserId)).thenReturn(mockTasksFromRepo);

        // Act
        List<TaskResponse> result = taskService.getAllTasksForCurrentUser(currentUserId);

        // Assert
        assertThat(result)
                .isNotNull()
                .hasSize(2);

        assertThat(result.getFirst().getId()).isEqualTo(task1.getId());
        assertThat(result.getFirst().getTitle()).isEqualTo(task1.getTitle());
        assertThat(result.getFirst().getUserId()).isEqualTo(currentUserId);

        assertThat(result.get(1).getId()).isEqualTo(task2.getId());
        assertThat(result.get(1).getTitle()).isEqualTo(task2.getTitle());
        assertThat(result.get(1).getUserId()).isEqualTo(currentUserId);

        verify(mockTaskRepository).findAllByUserIdOrderByCreatedAtDesc(currentUserId);
    }

    @Test
    @DisplayName("getAllTasksForCurrentUser: когда репозиторий возвращает пустой список -> должен вернуть пустой список TaskResponse")
    void getAllTasksForCurrentUser_whenRepositoryReturnsEmptyList_shouldReturnEmptyTaskResponseList() {
        // Arrange
        Long currentUserId = 1L;
        when(mockTaskRepository.findAllByUserIdOrderByCreatedAtDesc(currentUserId)).thenReturn(Collections.emptyList());

        // Act
        List<TaskResponse> result = taskService.getAllTasksForCurrentUser(currentUserId);

        // Assert
        assertThat(result).isNotNull().isEmpty();
        verify(mockTaskRepository).findAllByUserIdOrderByCreatedAtDesc(currentUserId);
    }

    @Test
    @DisplayName("getAllTasksForCurrentUser: currentUserId null -> должен выбросить NullPointerException (Lombok @NonNull)")
    void getAllTasksForCurrentUser_whenCurrentUserIdIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> taskService.getAllTasksForCurrentUser(null))
                .withMessageContaining("currentUserId is marked non-null but is null");
        verifyNoInteractions(mockTaskRepository);
    }

}