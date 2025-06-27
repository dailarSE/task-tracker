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
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackendApiClient {

    private final RestClient restClient;

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

    @BackendApiRetryable
    public List<UserTaskReport> fetchTaskReports(List<Long> userIds, Instant from, Instant to) {
        var requestBody = new UserTaskReportRequest(userIds, from, to);
        String url = "/api/v1/internal/tasks/user-reports";
        log.debug("Fetching task reports for {} users.", userIds.size());

        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}