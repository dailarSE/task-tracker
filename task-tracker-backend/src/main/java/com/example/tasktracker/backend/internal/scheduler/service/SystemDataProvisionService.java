package com.example.tasktracker.backend.internal.scheduler.service;

import com.example.tasktracker.backend.internal.scheduler.config.SchedulerSupportApiProperties;
import com.example.tasktracker.backend.internal.scheduler.dto.PageInfo;
import com.example.tasktracker.backend.internal.scheduler.dto.PaginatedUserIdsResponse;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReport;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReportRequest;
import com.example.tasktracker.backend.task.repository.TaskQueryRepository;
import com.example.tasktracker.backend.user.repository.UserQueryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Slice;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SystemDataProvisionService {

    private final UserQueryRepository userQueryRepository;
    private final TaskQueryRepository taskQueryRepository;
    private final SchedulerSupportApiProperties properties;
    private final ObjectMapper objectMapper;

    private record UserKeysetCursor(long lastId) {
    }

    /**
     * Получает агрегированные отчеты по задачам для указанного списка ID пользователей
     * за заданный временной интервал.
     * @param request DTO с параметрами запроса (ID пользователей, 'from', 'to'). Не должен быть null.
     * @return Список объектов {@link UserTaskReport}.
     */
    public List<UserTaskReport> getTaskReportsForUsers(@NonNull UserTaskReportRequest request) {
        if (request.getUserIds().isEmpty()) {
            return Collections.emptyList();
        }
        log.debug("Fetching task reports for {} user(s).", request.getUserIds().size());

        List<UserTaskReport> reports = taskQueryRepository.findTaskReportsForUsers(request.getUserIds(), request.getFrom(), request.getTo());
        log.info("Successfully fetched {} task reports.", reports.size());
        return reports;
    }

    /**
     * Получает пагинированный список ID пользователей для обработки.
     *
     * @param encodedCursor  Опциональный непрозрачный курсор (Base64) от предыдущего вызова.
     * @param requestedLimit Опциональный запрошенный лимит записей.
     * @return Объект с данными и информацией о следующей странице.
     */
    public PaginatedUserIdsResponse getUserIdsForProcessing(
            @Nullable String encodedCursor,
            @Nullable Integer requestedLimit
    ) {
        log.debug("Attempting to get user IDs for processing. Cursor is {}, requested limit is {}.",
                encodedCursor != null ? "present" : "absent", requestedLimit);

        final int finalLimit = Optional.ofNullable(requestedLimit)
                .filter(limit -> limit > 0)
                .map(limit -> Math.min(limit, properties.getUserProcessingIds().getMaxPageSize()))
                .orElse(properties.getUserProcessingIds().getDefaultPageSize());

        final long lastId = decodeCursor(encodedCursor);

        log.debug("Executing user ID query with lastId: {} and effective limit: {}.", lastId, finalLimit);
        Slice<Long> userIdsSlice = userQueryRepository.findUserIds(lastId, finalLimit);

        final boolean hasNext = userIdsSlice.hasNext();
        final String nextCursor = hasNext ? encodeCursor(userIdsSlice.getContent()) : null;
        final PageInfo pageInfo = new PageInfo(hasNext, nextCursor);
        final PaginatedUserIdsResponse response = new PaginatedUserIdsResponse(userIdsSlice.getContent(), pageInfo);

        log.info("Successfully provisioned {} user IDs. HasNextPage: {}.",
                response.getData().size(), response.getPageInfo().isHasNextPage());

        return response;
    }

    private long decodeCursor(@Nullable String encodedCursor) {
        if (!StringUtils.hasText(encodedCursor)) {
            log.trace("Cursor is absent, starting from the beginning (lastId=0).");
            return 0L;
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedCursor);
            UserKeysetCursor cursor = objectMapper.readValue(decodedBytes, UserKeysetCursor.class);
            return cursor.lastId();
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Failed to decode or parse keyset cursor: '{}'. Defaulting to initial state.", encodedCursor, e);
            return 0L;
        }
    }

    @Nullable
    private String encodeCursor(@NonNull List<Long> ids) {
        if (ids.isEmpty()) {
            log.trace("ID list is empty, no next cursor to encode.");
            return null;
        }

        try {
            Long lastIdInPage = ids.getLast();
            UserKeysetCursor cursor = new UserKeysetCursor(lastIdInPage);
            byte[] jsonBytes = objectMapper.writeValueAsBytes(cursor);
            return Base64.getEncoder().encodeToString(jsonBytes);
        } catch (JsonProcessingException e) {
            log.error("CRITICAL: Failed to serialize keyset cursor. This indicates a programming error.", e);
            throw new IllegalStateException("Failed to create a pagination cursor", e);
        }
    }
}