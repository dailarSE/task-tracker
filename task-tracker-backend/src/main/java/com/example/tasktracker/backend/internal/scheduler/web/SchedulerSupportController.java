package com.example.tasktracker.backend.internal.scheduler.web;

import com.example.tasktracker.backend.internal.scheduler.dto.PaginatedUserIdsResponse;
import com.example.tasktracker.backend.internal.scheduler.service.SystemDataProvisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/scheduler-support")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal API: Scheduler Support", description = "API для поддержки работы сервиса-планировщика")
@SecurityRequirement(name = "apiKeyAuth")
public class SchedulerSupportController {

    private final SystemDataProvisionService provisionService;

    @Operation(
            summary = "Получить пагинированный список ID пользователей для обработки",
            description = "Возвращает порцию ID пользователей с использованием keyset-пагинации на основе непрозрачного курсора."
    )
    @ApiResponse(
            responseCode = "200", description = "Список ID успешно получен.",
            content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PaginatedUserIdsResponse.class)))
    @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedApiKey")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestGeneral")
    @GetMapping("/user-ids")
    public ResponseEntity<PaginatedUserIdsResponse> getUserIdsForProcessing(
            @Parameter(description = "Непрозрачный курсор для получения следующей страницы. " +
                                     "Для первого запроса не передается.")
            @RequestParam(required = false) String cursor,

            @Parameter(description = "Максимальное количество ID в ответе.")
            @Positive(message = "{config.validation.positive}")
            @RequestParam(required = false) Integer limit
    ) {
        log.info("Processing request for user IDs. Cursor is {}, requested limit is {}.",
                cursor != null ? "present" : "absent", limit);
        PaginatedUserIdsResponse response = provisionService.getUserIdsForProcessing(cursor, limit);
        log.info("Successfully returned {} user IDs. HasNextPage: {}.",
                response.getData().size(), response.getPageInfo().isHasNextPage());
        return ResponseEntity.ok(response);
    }
}