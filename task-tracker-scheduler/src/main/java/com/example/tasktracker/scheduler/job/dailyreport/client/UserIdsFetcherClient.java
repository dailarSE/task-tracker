package com.example.tasktracker.scheduler.job.dailyreport.client;

import com.example.tasktracker.scheduler.client.ClientConfig;
import com.example.tasktracker.scheduler.client.retry.BackendApiRetryable;
import com.example.tasktracker.scheduler.job.dailyreport.client.dto.PaginatedUserIdsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * HTTP-клиент для взаимодействия с внутренним API сервиса task-tracker-backend,
 * специализирующийся на получении данных, необходимых для работы джобы-продюсера.
 * <p>
 * Этот компонент инкапсулирует логику пагинированной выборки ID пользователей.
 * </p>
 *
 * @see ClientConfig Конфигурация RestClient
 * @see BackendApiRetryable Аннотация, управляющая логикой ретраев
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserIdsFetcherClient {

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
}