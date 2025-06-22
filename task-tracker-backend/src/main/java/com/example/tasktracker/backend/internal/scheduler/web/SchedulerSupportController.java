package com.example.tasktracker.backend.internal.scheduler.web;

import com.example.tasktracker.backend.internal.scheduler.dto.PaginatedUserIdsResponse;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReport;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReportRequest;
import com.example.tasktracker.backend.internal.scheduler.service.SystemDataProvisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/scheduler-support")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal API: Scheduler Support", description = "API для поддержки работы сервиса-планировщика")
@SecurityRequirement(name = "apiKeyAuth")
public class SchedulerSupportController {

    private final SystemDataProvisionService provisionService;

    @Operation(
            summary = "Получить постраничный список ID пользователей для обработки",
            description = "Возвращает порцию ID пользователей."
    )
    @ApiResponse(
            responseCode = "200", description = "Список ID успешно получен.",
            content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PaginatedUserIdsResponse.class)))
    @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedApiKey")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestGeneral")
    @GetMapping("/user-ids")
    public ResponseEntity<PaginatedUserIdsResponse> getUserIdsForProcessing(
            @Parameter(description = "Курсор для получения следующей страницы. " +
                                     "Для первого запроса не передается.")
            @RequestParam(required = false) String cursor,

            @Parameter(description = "Максимальное количество ID в ответе.")
            @Positive(message = "{config.validation.positive}")
            @RequestParam(required = false) Integer limit
    ) {
        log.info("Processing request for user IDs. Cursor: [{}], Limit: [{}]", cursor, limit);

        PaginatedUserIdsResponse response = provisionService.getUserIdsForProcessing(cursor, limit);

        log.info("Successfully returned {} user IDs. HasNextPage: {}.",
                response.getUserIds().size(), response.getPageInfo().isHasNextPage());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить отчеты по задачам для списка пользователей",
            description = """
                Принимает список ID пользователей и временной интервал, возвращает агрегированные отчеты.
            
                **Логика формирования отчета:**
                - **Невыполненные задачи (`tasksPending`):** Включаются до 5 самых старых задач со статусом PENDING.
                - **Выполненные задачи (`tasksCompleted`):** Включаются все задачи, выполненные в заданном интервале `[from, to)`.
            """

    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200", description = "Отчеты успешно сформированы.",
                    content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = UserTaskReport.class)))
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedApiKey"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestGeneral")
    })
    @PostMapping("/tasks/user-reports")
    public ResponseEntity<List<UserTaskReport>> getTaskReportsForUsers(
            @Valid @RequestBody UserTaskReportRequest request) {
        log.info("Processing request for task reports for {} user(s).", request.getUserIds().size());
        List<UserTaskReport> reports = provisionService.getTaskReportsForUsers(request);
        log.info("Successfully returned {} task reports.", reports.size());
        return ResponseEntity.ok(reports);
    }
}