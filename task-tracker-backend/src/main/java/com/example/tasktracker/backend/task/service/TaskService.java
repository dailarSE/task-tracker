package com.example.tasktracker.backend.task.service;

import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.dto.TaskUpdateRequest;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.task.exception.TaskNotFoundException;
import com.example.tasktracker.backend.task.repository.TaskRepository;
import com.example.tasktracker.backend.user.repository.UserRepository;
import com.example.tasktracker.backend.web.exception.ResourceConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для управления бизнес-логикой, связанной с задачами.
 * <p>
 * Этот сервис инкапсулирует операции создания, чтения, обновления и удаления задач,
 * обеспечивая применение бизнес-правил и взаимодействие с {@link TaskRepository}
 * и {@link UserRepository}.
 * </p>
 * <p>
 * Транзакционная семантика класса по умолчанию {@code readOnly = true}.
 * Методы, изменяющие состояние, аннотированы {@code @Transactional} отдельно.
 * Публичные методы, изменяющие состояние, реализованы как не-транзакционные обертки,
 * делегирующие выполнение в защищенные транзакционные методы для корректной
 * обработки исключений, возникающих на этапе коммита (например, {@link ObjectOptimisticLockingFailureException}).
 * </p>
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class TaskService {

    private final TaskService self;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;

    public TaskService(@Lazy TaskService self, TaskRepository taskRepository, UserRepository userRepository,
                       ObjectMapper objectMapper, Validator validator, Clock clock) {
        this.self = self;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
    }

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
     * Публичный метод-обертка для полного обновления задачи.
     * <p>
     * Вызывает транзакционный метод и перехватывает {@link ObjectOptimisticLockingFailureException},
     * преобразуя его в доменное исключение {@link ResourceConflictException}.
     * </p>
     *
     * @param taskId        ID задачи для обновления.
     * @param request       DTO с полным набором данных для обновления.
     * @param currentUserId ID текущего пользователя.
     * @return DTO с обновленными данными задачи.
     */
    @Transactional(propagation = Propagation.NEVER)
    public TaskResponse updateTaskForCurrentUserOrThrow(@NonNull Long taskId,
                                                        @NonNull TaskUpdateRequest request,
                                                        @NonNull Long currentUserId) {
        try {
            Task detachedTask = self.doUpdateTaskInTransaction(taskId, request, currentUserId);
            return TaskResponse.fromEntity(detachedTask);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict for task ID: {} during PUT. Triggered at commit time.", taskId, e);
            throw new ResourceConflictException(taskId);
        }
    }

    /**
     * Внутренний транзакционный метод для полного обновления задачи.
     *
     * @param taskId        ID задачи.
     * @param request       DTO с данными для обновления.
     * @param currentUserId ID пользователя.
     * @return Task с обновленными данными.
     */
    @Transactional
    Task doUpdateTaskInTransaction(@NonNull Long taskId,
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

        if (!taskToUpdate.getVersion().equals(request.getVersion())) {
            log.warn("Optimistic lock conflict for task ID: {}. Persistent version: {}, request version: {}",
                    taskId, taskToUpdate.getVersion(), request.getVersion());
            throw new ResourceConflictException(taskId);
        }

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
        return taskToUpdate;
    }

    /**
     * Публичный метод-обертка для частичного обновления задачи.
     * <p>
     * Вызывает транзакционный метод и перехватывает {@link ObjectOptimisticLockingFailureException},
     * преобразуя его в доменное исключение {@link ResourceConflictException}.
     * </p>
     *
     * @param taskId        ID задачи для обновления.
     * @param patchNode     JsonNode, представляющий JSON Merge Patch.
     * @param currentUserId ID текущего пользователя.
     * @return DTO с обновленными данными задачи.
     */
    @Transactional(propagation = Propagation.NEVER)
    public TaskResponse patchTask(@NonNull Long taskId, @NonNull JsonNode patchNode, @NonNull Long currentUserId) {
        try {
            Task detachedTask = self.doPatchTaskInTransaction(taskId, patchNode, currentUserId);
            return TaskResponse.fromEntity(detachedTask);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict for task ID: {} during PATCH. Triggered at commit time.", taskId, e);
            throw new ResourceConflictException(taskId);
        }
    }

    /**
     * Внутренний транзакционный метод для частичного обновления задачи.
     *
     * @param taskId        ID задачи.
     * @param patchNode     JsonNode с изменениями.
     * @param currentUserId ID пользователя.
     * @return Task с обновленными данными.
     */
    @Transactional
    Task doPatchTaskInTransaction(@NonNull Long taskId, @NonNull JsonNode patchNode, @NonNull Long currentUserId) {
        log.debug("Attempting to patch task with ID: {} for user ID: {}", taskId, currentUserId);

        Task taskToUpdate = taskRepository.findByIdAndUserId(taskId, currentUserId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, currentUserId));

        try {
            TaskStatus oldStatus = taskToUpdate.getStatus();

            TaskUpdateRequest updateRequestDto = new TaskUpdateRequest(
                    taskToUpdate.getTitle(),
                    taskToUpdate.getDescription(),
                    taskToUpdate.getStatus(),
                    null // Версия это обязательное поле патча
            );

            objectMapper.readerForUpdating(updateRequestDto).readValue(patchNode);

            Set<ConstraintViolation<TaskUpdateRequest>> violations = validator.validate(updateRequestDto);
            if (!violations.isEmpty()) {
                log.warn("Invalid task update request for task ID: {} for user ID: {}. Violations: {}",
                        taskId, currentUserId, violations);
                throw new ConstraintViolationException(violations);
            }

            if (!taskToUpdate.getVersion().equals(updateRequestDto.getVersion())) {
                log.warn("Optimistic lock conflict for task ID: {}. Persistent version: {}, request version: {}",
                        taskId, taskToUpdate.getVersion(), updateRequestDto.getVersion());
                throw new ResourceConflictException(taskId);
            }

            // Обновляем управляемую сущность из валидированного DTO
            taskToUpdate.setTitle(updateRequestDto.getTitle());
            taskToUpdate.setDescription(updateRequestDto.getDescription());

            Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

            // Обрабатываем изменение статуса и completedAt
            if (updateRequestDto.getStatus() != oldStatus) {
                updateCompletedAtBasedOnStatus(taskToUpdate, updateRequestDto.getStatus(), now);
                taskToUpdate.setStatus(updateRequestDto.getStatus());
            }

            taskToUpdate.setUpdatedAt(now);

            log.info("Task ID: {} patched successfully for user ID: {}. Pending transaction commit.", taskId, currentUserId);
            return taskToUpdate;
        } catch (JsonProcessingException e) {
            log.warn("Failed to apply JSON merge patch to task ID: {}. Invalid JSON format or value. Details: {}",
                    taskId, e.getMessage());
            throw new HttpMessageConversionException("Invalid patch data: " + e.getOriginalMessage(), e);
        } catch ( IOException e) {
            log.error("Failed to apply JSON merge patch to task ID: {}", taskId, e);
            throw new IllegalStateException("Error processing patch for task " + taskId, e);
        }
    }

    /**
     * Удаляет задачу по ее ID для указанного пользователя.
     * <p>
     * Если задача с указанным {@code taskId} не существует или не принадлежит
     * пользователю с {@code currentUserId}, будет выброшено исключение
     * {@link com.example.tasktracker.backend.task.exception.TaskNotFoundException}.
     * </p>
     * <p>
     * Метод является транзакционным и обеспечивает атомарное удаление.
     * </p>
     *
     * @param taskId        ID задачи, которую необходимо удалить. Не должен быть {@code null}.
     * @param currentUserId ID текущего аутентифицированного пользователя, который должен быть
     *                      владельцем удаляемой задачи. Не должен быть {@code null}.
     * @throws TaskNotFoundException если задача не найдена или не принадлежит пользователю.
     * @throws NullPointerException  если {@code taskId} или {@code currentUserId} равны {@code null}
     *                               (из-за аннотаций {@code @NonNull}).
     */
    @Transactional
    public void deleteTaskForCurrentUserOrThrow(@NonNull Long taskId, @NonNull Long currentUserId) {
        log.debug("Attempting to delete task with ID: {} for user ID: {}", taskId, currentUserId);

        int deletedCount = taskRepository.deleteByIdAndUserId(taskId, currentUserId);

        if (deletedCount == 0) {
            // Задача либо не найдена, либо не принадлежит пользователю.
            log.warn("Delete failed: Task not found or access denied for task ID: {} and user ID: {}",
                    taskId, currentUserId);
            throw new TaskNotFoundException(taskId, currentUserId);
        }

        log.info("Task with ID: {} for user ID: {} deleted successfully. {} row(s) affected.",
                taskId, currentUserId, deletedCount);
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