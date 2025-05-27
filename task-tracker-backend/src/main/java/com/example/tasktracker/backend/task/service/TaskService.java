package com.example.tasktracker.backend.task.service;

import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.task.repository.TaskRepository;
import com.example.tasktracker.backend.user.repository.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    /**
     * Создает новую задачу для указанного пользователя.
     * <p>
     * Новой задаче автоматически присваивается статус {@link TaskStatus#PENDING}.
     * Владелец задачи устанавливается на основе предоставленного {@code currentUserId}.
     * Дата создания и обновления устанавливаются автоматически через JPA Auditing.
     * </p>
     *
     * @param request         DTO {@link TaskCreateRequest} с данными для создания задачи. Не должен быть null.
     * @param currentUserId   ID текущего аутентифицированного пользователя, который будет владельцем задачи.
     *                        Не должен быть null.
     * @return {@link TaskResponse} DTO, представляющий созданную задачу.
     * @throws org.springframework.orm.jpa.JpaObjectRetrievalFailureException если пользователь с {@code currentUserId} не найден (выбрасывается {@code userRepository.getReferenceById}).
     * @throws NullPointerException если {@code request} или {@code currentUserId} равны {@code null}.
     */
    @Transactional
    public TaskResponse createTask(@NonNull TaskCreateRequest request, @NonNull Long currentUserId) {
        log.debug("Attempting to create a new task for user ID: {} with title: '{}'",
                currentUserId, request.getTitle());

        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(TaskStatus.PENDING);

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

}