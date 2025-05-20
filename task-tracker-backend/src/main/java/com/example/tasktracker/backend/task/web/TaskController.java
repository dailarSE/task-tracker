package com.example.tasktracker.backend.task.web;

import com.example.tasktracker.backend.security.common.ControllerSecurityUtils;
import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.service.TaskService;
import com.example.tasktracker.backend.web.ApiConstants;
import com.example.tasktracker.backend.web.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * REST-контроллер для операций, связанных с задачами (Tasks).
 * <p>
 * Предоставляет API эндпоинты для управления задачами пользователей.
 * На данный момент реализован эндпоинт для создания новых задач.
 * </p>
 * <p>
 * Все эндпоинты в этом контроллере требуют аутентификации пользователя.
 * Ошибки (например, ошибки валидации DTO, неавторизованный доступ,
 * бизнес-исключения из {@link TaskService}) обрабатываются глобально
 * в {@link GlobalExceptionHandler} и возвращаются клиенту в формате
 * RFC 9457 Problem Details.
 * </p>
 *
 * @see TaskService Сервис, инкапсулирующий бизнес-логику задач.
 * @see GlobalExceptionHandler Глобальный обработчик исключений.
 * @see TaskCreateRequest DTO для запроса на создание задачи.
 * @see TaskResponse DTO для ответа с информацией о задаче.
 * @see AppUserDetails Детали аутентифицированного пользователя.
 */
@RestController
@RequestMapping(ApiConstants.TASKS_API_BASE_URL)
@RequiredArgsConstructor
@Slf4j
public class TaskController {

    private final TaskService taskService;

    /**
     * Создает новую задачу для текущего аутентифицированного пользователя.
     * <p>
     * Эндпоинт: {@code POST /api/v1/tasks}
     * </p>
     * <p>
     * Принимает {@link TaskCreateRequest} в теле запроса, который должен пройти валидацию.
     * В случае успешного создания:
     * <ul>
     *     <li>Возвращается HTTP статус 201 Created.</li>
     *     <li>В теле ответа содержится {@link TaskResponse} с данными созданной задачи.</li>
     *     <li>В заголовке ответа {@code Location} содержится URI созданного ресурса задачи.</li>
     * </ul>
     * В случае ошибки (например, невалидные данные запроса, ошибки аутентификации),
     * будет возвращен соответствующий HTTP статус ошибки (400, 401) с телом
     * в формате Problem Details.
     * </p>
     *
     * @param request         DTO с данными для создания задачи. Должен быть аннотирован {@code @Valid}
     *                        для активации валидации.
     * @param currentUserDetails Детали текущего аутентифицированного пользователя,
     *                           внедренные Spring Security. Ожидается, что не будет {@code null}
     *                           для защищенного эндпоинта.
     * @return {@link ResponseEntity} с {@link TaskResponse} в теле и статусом 201 Created,
     *         либо ответ об ошибке, сформированный {@link GlobalExceptionHandler}.
     * @throws InsufficientAuthenticationException если {@code currentUserDetails} равен {@code null}
     *                                             (хотя это должно быть обработано Spring Security раньше).
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody TaskCreateRequest request,
            @AuthenticationPrincipal AppUserDetails currentUserDetails) {

        Long currentUserId = ControllerSecurityUtils.getCurrentUserId(currentUserDetails);

        log.info("Processing request to create a new task for user ID: {}", currentUserId);

        TaskResponse createdTaskResponse = taskService.createTask(request, currentUserId);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdTaskResponse.getId())
                .toUri();

        log.info("Task created successfully with ID: {} for user ID: {}. Location: {}",
                createdTaskResponse.getId(), currentUserId, location);

        return ResponseEntity.created(location).body(createdTaskResponse);
    }
}