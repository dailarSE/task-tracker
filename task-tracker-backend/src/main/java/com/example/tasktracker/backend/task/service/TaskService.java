package com.example.tasktracker.backend.task.service;

import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.dto.TaskUpdateRequest;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.task.exception.TaskNotFoundException;
import com.example.tasktracker.backend.task.repository.TaskRepository;
import com.example.tasktracker.backend.user.repository.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сервис для управления бизнес-логикой, связанной с задачами.
 * <p>
 * Этот сервис инкапсулирует операции создания, чтения, обновления и удаления задач,
 * обеспечивая применение бизнес-правил и взаимодействие с {@link TaskRepository}
 * и {@link UserRepository}.
 * </p>
 * <p>
 * Все публичные методы, изменяющие состояние данных (например, создание, обновление, удаление),
 * должны быть транзакционными.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Создает новую задачу для указанного пользователя.
     * <p>
     * Новой задаче автоматически присваивается статус {@link TaskStatus#PENDING}.
     * Владелец задачи устанавливается на основе предоставленного {@code currentUserId}.
     * </p>
     *
     * @param request       DTO {@link TaskCreateRequest} с данными для создания задачи. Не должен быть null.
     * @param currentUserId ID текущего аутентифицированного пользователя, который будет владельцем задачи.
     *                      Не должен быть null.
     * @return {@link TaskResponse} DTO, представляющий созданную задачу.
     * @throws org.springframework.orm.jpa.JpaObjectRetrievalFailureException если пользователь с {@code currentUserId} не найден (выбрасывается {@code userRepository.getReferenceById}).
     * @throws NullPointerException                                           если {@code request} или {@code currentUserId} равны {@code null}.
     */
    @Transactional
    public TaskResponse createTask(@NonNull TaskCreateRequest request, @NonNull Long currentUserId) {
        log.debug("Attempting to create a new task for user ID: {} with title: '{}'",
                currentUserId, request.getTitle());

        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());

        task.setStatus(TaskStatus.PENDING);

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        task.setUser(userRepository.getReferenceById(currentUserId));

        Task savedTask = taskRepository.save(task);
        log.info("Task created successfully with ID: {} for user ID: {}", savedTask.getId(), currentUserId);

        return TaskResponse.fromEntity(savedTask);
    }

    /**
     * Получает все задачи для текущего аутентифицированного пользователя,
     * отсортированные по времени создания (новые сначала).
     *
     * @param currentUserId ID текущего пользователя. Не должен быть null.
     * @return Список {@link TaskResponse} DTO. Список будет пустым, если у пользователя нет задач.
     * @throws NullPointerException если {@code currentUserId} равен {@code null}.
     */
    public List<TaskResponse> getAllTasksForCurrentUser(@NonNull Long currentUserId) {
        log.debug("Fetching all tasks for user ID: {}", currentUserId);

        List<Task> tasks = taskRepository.findAllByUserIdOrderByCreatedAtDesc(currentUserId);

        List<TaskResponse> responses = tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());

        log.info("Found {} tasks for user ID: {}", responses.size(), currentUserId);
        return responses;
    }

    /**
     * Находит задачу по ее ID для указанного пользователя.
     * Если задача не найдена или не принадлежит пользователю, выбрасывает {@link TaskNotFoundException}.
     *
     * @param taskId        ID запрашиваемой задачи. Не должен быть null.
     * @param currentUserId ID текущего аутентифицированного пользователя. Не должен быть null.
     * @return {@link TaskResponse} DTO, представляющий найденную задачу.
     * @throws TaskNotFoundException если задача не найдена или не принадлежит пользователю.
     * @throws NullPointerException  если taskId или currentUserId равны null (из-за @NonNull).
     */
    public TaskResponse getTaskByIdForCurrentUserOrThrow(@NonNull Long taskId, @NonNull Long currentUserId) {
        log.debug("Attempting to find task with ID: {} for user ID: {}", taskId, currentUserId);

        return taskRepository.findByIdAndUserId(taskId, currentUserId)
                .map(task -> {
                    log.info("Successfully retrieved task with ID: {} for user ID: {}", task.getId(), currentUserId);
                    return TaskResponse.fromEntity(task);
                })
                .orElseThrow(() -> {
                    log.warn("Task not found or access denied for task ID: {} and user ID: {}", taskId, currentUserId);
                    return new TaskNotFoundException(taskId, currentUserId);
                });
    }

    /**
     * Обновляет существующую задачу для текущего аутентифицированного пользователя.
     * Позволяет изменить заголовок, описание и статус задачи.
     * <p>
     * Если статус задачи меняется на {@link TaskStatus#COMPLETED}, поле {@code completedAt}
     * устанавливается в текущее время. Если статус меняется с {@link TaskStatus#COMPLETED}
     * на {@link TaskStatus#PENDING}, поле {@code completedAt} сбрасывается в {@code null}.
     * </p>
     *
     * @param taskId        ID задачи, которую необходимо обновить. Не должен быть {@code null}.
     * @param request       DTO {@link TaskUpdateRequest} с новыми данными для задачи. Не должен быть {@code null}.
     *                      Все поля в DTO (title, description, status) будут применены к задаче.
     * @param currentUserId ID текущего аутентифицированного пользователя, который должен быть владельцем задачи.
     *                      Не должен быть {@code null}.
     * @return {@link TaskResponse} DTO, представляющий обновленную задачу.
     * @throws TaskNotFoundException если задача с указанным {@code taskId} не найдена для {@code currentUserId}
     *                               или вообще не существует.
     * @throws NullPointerException  если любой из аргументов {@code taskId}, {@code request},
     *                               или {@code currentUserId} равен {@code null}.
     */
    @Transactional
    public TaskResponse updateTaskForCurrentUserOrThrow(@NonNull Long taskId,
                                                        @NonNull TaskUpdateRequest request,
                                                        @NonNull Long currentUserId) {
        log.debug("Attempting to update task with ID: {} for user ID: {} with new title: '{}', status: {}",
                taskId, currentUserId, request.getTitle(), request.getStatus());

        Task taskToUpdate = taskRepository.findByIdAndUserId(taskId, currentUserId)
                .orElseThrow(() -> {
                    log.warn("Update failed: Task not found or access denied for task ID: {} and user ID: {}",
                            taskId, currentUserId);
                    return new TaskNotFoundException(taskId, currentUserId);
                });

        // Обновляем поля
        taskToUpdate.setTitle(request.getTitle());
        taskToUpdate.setDescription(request.getDescription());

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        // Обрабатываем изменение статуса и completedAt
        updateCompletedAtBasedOnStatus(taskToUpdate, request.getStatus(), now);
        taskToUpdate.setStatus(request.getStatus());

        taskToUpdate.setUpdatedAt(now);

        log.info("Task update logic completed for task ID: {} (user ID: {}). Pending transaction commit.",
                taskToUpdate.getId(), currentUserId);
        return TaskResponse.fromEntity(taskToUpdate);
    }


    /**
     * Вспомогательный приватный метод для обновления поля {@code completedAt} задачи
     * на основе изменения ее статуса.
     * <p>
     * Если новый статус задачи {@link TaskStatus#COMPLETED} и предыдущий статус был иным,
     * {@code completedAt} устанавливается в текущее время (полученное через инжектированный {@link Clock}).
     * Если новый статус {@link TaskStatus#PENDING} и предыдущий статус был {@link TaskStatus#COMPLETED},
     * {@code completedAt} сбрасывается в {@code null}.
     * В остальных случаях поле {@code completedAt} не изменяется этим методом.
     * </p>
     *
     * @param task      Сущность {@link Task}, у которой может быть обновлено поле {@code completedAt}.
     *                  Не должна быть {@code null}. Поле {@code task.getStatus()} должно отражать
     *                  *старый* статус задачи до предполагаемого обновления.
     * @param newStatus Новый {@link TaskStatus}, который будет установлен для задачи.
     *                  Не должен быть {@code null}.
     * @param timestamp Временная метка для установки {@code completedAt}.
     * @throws NullPointerException если {@code task} или {@code newStatus} равны {@code null}.
     */
     void updateCompletedAtBasedOnStatus(@NonNull Task task,
                                                @NonNull TaskStatus newStatus,
                                                @NonNull Instant timestamp) {
        TaskStatus oldStatus = task.getStatus(); // Текущий (старый) статус задачи

        Objects.requireNonNull(oldStatus, "Old status of the task cannot be null when updating completedAt.");

        if (newStatus == TaskStatus.COMPLETED && oldStatus != TaskStatus.COMPLETED) {
            task.setCompletedAt(timestamp);
            log.trace("Task ID: {} marked as COMPLETED. Setting completedAt to: {}", task.getId(), task.getCompletedAt());
        } else if (newStatus == TaskStatus.PENDING && oldStatus == TaskStatus.COMPLETED) {
            task.setCompletedAt(null);
            log.trace("Task ID: {} status changed from COMPLETED to PENDING. Resetting completedAt.", task.getId());
        }
        // Если newStatus == oldStatus, или другие переходы (например, PENDING -> PENDING), completedAt не меняем.
    }
}