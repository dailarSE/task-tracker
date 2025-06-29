package com.example.tasktracker.backend.task.web;

import com.example.tasktracker.backend.security.common.ControllerSecurityUtils;
import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.task.dto.TaskCreateRequest;
import com.example.tasktracker.backend.task.dto.TaskResponse;
import com.example.tasktracker.backend.task.dto.TaskUpdateRequest;
import com.example.tasktracker.backend.task.service.TaskService;
import com.example.tasktracker.backend.web.ApiConstants;
import com.example.tasktracker.backend.web.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST-контроллер для операций, связанных с задачами (Tasks).
 * <p>
 * Предоставляет API эндпоинты для управления задачами пользователей.
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
 * @see ControllerSecurityUtils Утилиты для работы с principal в контроллерах.
 */
@RestController
@RequestMapping(ApiConstants.TASKS_API_BASE_URL)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tasks", description = "API для управления задачами")
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
     * @param request            DTO с данными для создания задачи. Должен быть аннотирован {@code @Valid}
     *                           для активации валидации.
     * @param currentUserDetails Детали текущего аутентифицированного пользователя,
     *                           внедренные Spring Security. Ожидается, что не будет {@code null}
     *                           для защищенного эндпоинта.
     * @return {@link ResponseEntity} с {@link TaskResponse} в теле и статусом 201 Created,
     * либо ответ об ошибке, сформированный {@link GlobalExceptionHandler}.
     * @throws IllegalStateException если {@code currentUserDetails} не может быть разрешен в корректный {@link AppUserDetails} с ID (выбрасывается из {@link ControllerSecurityUtils}).
     */
    @Operation(summary = "Создание новой задачи")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Задача успешно создана",
                    headers = @Header(name = "Location", description = "URI созданного ресурса задачи"),
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TaskResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestValidation"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedGeneral")
    })
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

    /**
     * Получает список всех задач для текущего аутентифицированного пользователя.
     * Задачи отсортированы по времени создания (новые сначала).
     * <p>
     * Эндпоинт: {@code GET /api/v1/tasks}
     * </p>
     * <p>
     * Доступен только для аутентифицированных пользователей. При успешном запросе
     * возвращает HTTP статус 200 OK со списком {@link TaskResponse} в теле.
     * Если у пользователя нет задач, возвращается пустой список.
     * </p>
     *
     * @param currentUserDetails Детали текущего аутентифицированного пользователя.
     * @return {@link ResponseEntity} со списком {@link TaskResponse} и статусом 200 OK.
     * @throws IllegalStateException если principal не может быть разрешен (от {@link ControllerSecurityUtils}).
     */
    @Operation(summary = "Получение списка задач текущего пользователя")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Список задач успешно получен.",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = TaskResponse.class))
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedGeneral")
    })
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasksForCurrentUser(
            @AuthenticationPrincipal AppUserDetails currentUserDetails) {

        Long currentUserId = ControllerSecurityUtils.getCurrentUserId(currentUserDetails);
        log.info("Processing request to get all tasks for user ID: {}", currentUserId);

        List<TaskResponse> tasks = taskService.getAllTasksForCurrentUser(currentUserId);
        log.info("Retrieved all tasks for user ID: {}", currentUserId);

        return ResponseEntity.ok(tasks);
    }

    /**
     * Получает задачу по ее ID для текущего аутентифицированного пользователя.
     * <p>
     * Эндпоинт: {@code GET /api/v1/tasks/{taskId}}
     * </p>
     * <p>
     * При успешном запросе возвращает HTTP 200 OK с {@link TaskResponse} в теле,
     * содержащим полную информацию о запрошенной задаче.
     * </p>
     *
     * @param taskId             ID запрашиваемой задачи, извлекаемый из пути URL.
     *                           Должен быть валидным числовым идентификатором.
     * @param currentUserDetails Детали текущего аутентифицированного пользователя,
     *                           внедренные Spring Security. Ожидается, что не будет {@code null}.
     * @return {@link ResponseEntity} с {@link TaskResponse} и статусом 200 OK.
     * @throws com.example.tasktracker.backend.task.exception.TaskNotFoundException если задача с указанным ID
     *                                                                              не найдена для текущего пользователя или не существует. В этом случае будет возвращен
     *                                                                              HTTP 404 Not Found.
     * @throws IllegalStateException                                                если principal текущего пользователя не может быть корректно разрешен
     *                                                                              (например, отсутствует ID пользователя в {@code AppUserDetails}). В этом случае будет возвращен
     *                                                                              HTTP 500 Internal Server Error.
     * @see com.example.tasktracker.backend.web.exception.GlobalExceptionHandler Для деталей обработки ошибок API.
     */
    @Operation(summary = "Получение задачи по ID")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Задача успешно найдена.",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TaskResponse.class))
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestTypeMismatch"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedGeneral"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFoundTask")
    })
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long taskId,
                                                    @AuthenticationPrincipal AppUserDetails currentUserDetails) {
        Long currentUserId = ControllerSecurityUtils.getCurrentUserId(currentUserDetails);
        log.info("Processing request to get task ID: {} for user ID: {}", taskId, currentUserId);

        TaskResponse taskResponse = taskService.getTaskByIdForCurrentUserOrThrow(taskId, currentUserId);

        log.info("Successfully retrieved task ID: {} for user ID: {}", taskId, currentUserId);
        return ResponseEntity.ok(taskResponse);
    }

    /**
     * Обновляет существующую задачу для текущего аутентифицированного пользователя.
     * <p>
     * Эндпоинт: {@code PUT /api/v1/tasks/{taskId}}
     * </p>
     * <p>
     * Принимает {@link TaskUpdateRequest} в теле запроса для полного обновления задачи.
     * Поля, не указанные в запросе, могут быть сброшены в значения по умолчанию или null,
     * если это позволяет бизнес-логика (для PUT обычно ожидается полное представление).
     * В случае успешного обновления возвращается HTTP статус 200 OK с {@link TaskResponse},
     * содержащим обновленные данные задачи.
     * </p>
     *
     * @param taskId             ID обновляемой задачи, извлекаемый из пути URL.
     * @param request            DTO {@link TaskUpdateRequest} с новыми данными для задачи.
     * @param currentUserDetails Детали текущего аутентифицированного пользователя.
     * @return {@link ResponseEntity} с {@link TaskResponse} и статусом 200 OK.
     * @throws com.example.tasktracker.backend.task.exception.TaskNotFoundException если задача не найдена.
     * @throws IllegalStateException                                                если principal не может быть разрешен.
     * @throws jakarta.validation.ValidationException                               если данные в {@code request} не проходят валидацию.
     */
    @Operation(summary = "Полное обновление задачи")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Задача успешно обновлена",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TaskResponse.class))
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestGeneral"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedGeneral"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFoundTask"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictGeneral")
    })
    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long taskId,
            @Valid @RequestBody TaskUpdateRequest request,
            @AuthenticationPrincipal AppUserDetails currentUserDetails) {

        Long currentUserId = ControllerSecurityUtils.getCurrentUserId(currentUserDetails);
        log.info("Processing request to update task ID: {} for user ID: {} with title: '{}', status: {}",
                taskId, currentUserId, request.getTitle(), request.getStatus());

        TaskResponse updatedTaskResponse = taskService.updateTaskForCurrentUserOrThrow(taskId, request, currentUserId);

        log.info("Task ID: {} for user ID: {} updated successfully.", updatedTaskResponse.getId(), currentUserId);
        return ResponseEntity.ok(updatedTaskResponse);
    }

    /**
     * Частично обновляет существующую задачу, используя стандарт JSON Merge Patch (RFC 7396).
     * Этот метод позволяет клиенту отправлять только измененные поля.
     * Реализует оптимистическую блокировку: для успешного обновления в теле запроса
     * должно присутствовать поле 'version' с актуальным значением.
     *
     * @param taskId             ID обновляемой задачи.
     * @param patchNode          Тело запроса в формате JSON Merge Patch, представленное как JsonNode.
     * @param currentUserDetails Детали текущего аутентифицированного пользователя.
     * @return {@link ResponseEntity} с {@link TaskResponse} и статусом 200 OK.
     * @throws com.example.tasktracker.backend.web.exception.ResourceConflictException если обнаружен конфликт версий (HTTP 409).
     * @throws jakarta.validation.ConstraintViolationException если состояние задачи после патча невалидно (HTTP 400).
     */
    @Operation(summary = "Частичное обновление задачи (JSON Merge Patch)",
            description = "Принимает Content-Type: application/merge-patch+json. " +
                    "Требует поле 'version' в теле для оптимистической блокировки.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Тело запроса в формате JSON Merge Patch (RFC 7396). Отправляйте только измененные поля и обязательное поле 'version'.",
            required = true,
            content = @Content(
                    mediaType = "application/merge-patch+json",
                    schema = @Schema(ref = "#/components/schemas/TaskPatchRequest")
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Задача успешно обновлена",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestGeneral"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedGeneral"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFoundTask"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictGeneral")
    })
    @PatchMapping(path = "/{taskId}", consumes = "application/merge-patch+json")
    public ResponseEntity<TaskResponse> patchTask(
            @PathVariable Long taskId,
            @RequestBody JsonNode patchNode,
            @AuthenticationPrincipal AppUserDetails currentUserDetails) {

        Long currentUserId = ControllerSecurityUtils.getCurrentUserId(currentUserDetails);
        log.info("Processing PATCH request to update task ID: {} for user ID: {}", taskId, currentUserId);

        TaskResponse updatedTaskResponse = taskService.patchTask(taskId, patchNode, currentUserId);

        log.info("Task ID: {} for user ID: {} patched successfully.", updatedTaskResponse.getId(), currentUserId);
        return ResponseEntity.ok(updatedTaskResponse);
    }

    /**
     * Удаляет задачу по ее ID для текущего аутентифицированного пользователя.
     * <p>
     * Эндпоинт: {@code DELETE /api/v1/tasks/{taskId}}
     * </p>
     * <p>
     * При успешном удалении задачи, принадлежащей текущему пользователю,
     * возвращается HTTP статус 204 No Content.
     * </p>
     *
     * @param taskId             ID удаляемой задачи, извлекаемый из пути URL.
     *                           Должен быть валидным числовым идентификатором.
     * @param currentUserDetails Детали текущего аутентифицированного пользователя,
     *                           внедренные Spring Security. Ожидается, что не будет {@code null}.
     * @return {@link ResponseEntity} со статусом 204 No Content в случае успеха.
     * @throws com.example.tasktracker.backend.task.exception.TaskNotFoundException если задача с указанным ID
     *                                                                              не найдена для текущего пользователя или не существует. В этом случае будет возвращен
     *                                                                              HTTP 404 Not Found.
     * @throws org.springframework.beans.TypeMismatchException                      если {@code taskId} не может быть
     *                                                                              преобразован в {@link Long}. В этом случае будет возвращен HTTP 400 Bad Request.
     * @throws IllegalStateException                                                если principal текущего пользователя не может быть корректно разрешен.
     *                                                                              В этом случае будет возвращен HTTP 500 Internal Server Error.
     * @see com.example.tasktracker.backend.web.exception.GlobalExceptionHandler Для деталей обработки ошибок API.
     */
    @Operation(summary = "Удаление задачи")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Задача успешно удалена.",
                    content = @Content // Пустое тело ответа
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestTypeMismatch"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedGeneral"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFoundTask")
    })
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long taskId,
                                           @AuthenticationPrincipal AppUserDetails currentUserDetails) {
        Long currentUserId = ControllerSecurityUtils.getCurrentUserId(currentUserDetails);
        log.info("Processing request to delete task ID: {} for user ID: {}", taskId, currentUserId);

        taskService.deleteTaskForCurrentUserOrThrow(taskId, currentUserId);

        log.info("Task ID: {} for user ID: {} deleted successfully.", taskId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}