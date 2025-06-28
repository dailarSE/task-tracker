package com.example.tasktracker.scheduler.client;

import com.example.tasktracker.scheduler.client.dto.PaginatedUserIdsResponse;
import com.example.tasktracker.scheduler.client.dto.UserTaskReport;
import com.example.tasktracker.scheduler.client.dto.UserTaskReportRequest;
import com.example.tasktracker.scheduler.client.retry.BackendApiRetryable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * HTTP-клиент для взаимодействия с внутренним API сервиса task-tracker-backend.
 * <p>
 * Этот компонент инкапсулирует всю логику выполнения HTTP-запросов,
 * включая формирование URI, сериализацию/десериализацию DTO и применение
 * политики повторных попыток.
 * </p>
 *
 * @see com.example.tasktracker.scheduler.config.ClientConfig Конфигурация RestClient
 * @see BackendApiRetryable Аннотация, управляющая логикой ретраев
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackendApiClient {
    private static final ParameterizedTypeReference<List<UserTaskReport>> USER_TASK_REPORT_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Получает пагинированный список ID пользователей от Backend API.
     * <p>
     * Метод использует keyset-пагинацию на основе курсора.
     * Повторные попытки выполняются автоматически при серверных (5xx)
     * или сетевых ошибках благодаря аннотации {@link BackendApiRetryable}.
     * </p>
     *
     * @param cursor Непрозрачный курсор (Base64), полученный из предыдущего ответа.
     *               Может быть {@code null} для первого запроса.
     * @param limit  Максимальное количество ID для возврата.
     * @return Объект {@link PaginatedUserIdsResponse}, содержащий порцию ID и
     *         информацию для следующей страницы.
     * @throws HttpClientErrorException При получении 4xx ошибок от бэкенда.
     * @throws HttpServerErrorException При получении 5xx ошибок после всех ретраев.
     */
    @BackendApiRetryable
    public PaginatedUserIdsResponse fetchUserIds(@Nullable String cursor, int limit) {
        log.debug("Fetching user IDs with cursor: [{}], limit: [{}]", cursor, limit);
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/internal/scheduler-support/user-ids")
                        .queryParam("limit", limit)
                        .queryParamIfPresent("cursor", Optional.ofNullable(cursor))
                        .build())
                .retrieve()
                .body(PaginatedUserIdsResponse.class);
    }

    /**
     * Получает агрегированные отчеты по задачам для указанного списка пользователей.
     * <p>
     * Отправляет список ID и временной интервал в теле POST-запроса.
     * Повторные попытки выполняются автоматически при серверных (5xx)
     * или сетевых ошибках благодаря аннотации {@link BackendApiRetryable}.
     * </p>
     *
     * @param userIds Список ID пользователей для обработки.
     * @param from    Начало временного интервала (включительно).
     * @param to      Конец временного интервала (исключительно).
     * @return Список объектов {@link UserTaskReport}.
     * @throws HttpClientErrorException При получении 4xx ошибок от бэкенда (например, из-за невалидного интервала).
     * @throws HttpServerErrorException При получении 5xx ошибок после всех ретраев.
     */
    @BackendApiRetryable
    public List<UserTaskReport> fetchTaskReports(List<Long> userIds, Instant from, Instant to) {
        var requestBody = new UserTaskReportRequest(userIds, from, to);
        log.debug("Fetching task reports for {} users.", userIds.size());

        return restClient.post()
                .uri("/api/v1/internal/tasks/user-reports")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(USER_TASK_REPORT_LIST_TYPE);
    }
}