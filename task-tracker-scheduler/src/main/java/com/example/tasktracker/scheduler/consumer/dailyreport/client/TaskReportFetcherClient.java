package com.example.tasktracker.scheduler.consumer.dailyreport.client;

import com.example.tasktracker.scheduler.client.ClientConfig;
import com.example.tasktracker.scheduler.client.retry.BackendApiRetryable;
import com.example.tasktracker.scheduler.consumer.dailyreport.client.dto.UserTaskReport;
import com.example.tasktracker.scheduler.consumer.dailyreport.client.dto.UserTaskReportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

/**
 * HTTP-клиент для взаимодействия с внутренним API сервиса task-tracker-backend,
 * специализирующийся на получении агрегированных отчетов по задачам.
 * <p>
 * Используется консьюмерами для обогащения данных.
 * </p>
 *
 * @see ClientConfig Конфигурация RestClient
 * @see BackendApiRetryable Аннотация, управляющая логикой ретраев
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskReportFetcherClient {

    private static final ParameterizedTypeReference<List<UserTaskReport>> USER_TASK_REPORT_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

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