package com.example.tasktracker.scheduler.job.dailyreport.web;

import com.example.tasktracker.scheduler.job.JobStateKeyProvider;
import com.example.tasktracker.scheduler.job.dailyreport.config.DailyReportJobProperties;
import com.example.tasktracker.scheduler.job.dto.CursorPayload;
import com.example.tasktracker.scheduler.job.dto.JobExecutionState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Кастомный Actuator эндпоинт для мониторинга точного состояния джобы отчетов.
 * Доступен по адресу: GET /actuator/dailyreport или GET /actuator/dailyreport/{date}
 */
@Component
@Endpoint(id = "dailyreport")
@RequiredArgsConstructor
@Slf4j
public class DailyReportStateEndpoint {

    private final StringRedisTemplate redisTemplate;
    private final JobStateKeyProvider keyProvider;
    private final DailyReportJobProperties jobProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final TypeReference<JobExecutionState<CursorPayload>> stateTypeReference = new TypeReference<>() {};

    /**
     * Читает состояние джобы. Если дата не передана, по умолчанию берется "вчера"
     */
    @ReadOperation
    public Map<String, Object> getJobState() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        return fetchStateForDate(yesterday);
    }

    /**
     * Читает состояние джобы за конкретную дату, переданную в пути (например, /actuator/dailyreport/2025-07-15)
     */
    @ReadOperation
    public Map<String, Object> getJobStateForDate(@Selector String date) {
        try {
            LocalDate parsedDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            return fetchStateForDate(parsedDate);
        } catch (DateTimeParseException e) {
            return buildErrorResponse(date, "INVALID_DATE_FORMAT", "Expected format: YYYY-MM-DD");
        }
    }

    private Map<String, Object> fetchStateForDate(LocalDate date) {
        String jobName = jobProperties.getJobName();
        String hashKey = keyProvider.getHashKey(jobName);
        String hashField = keyProvider.getHashField(date);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobName", jobName);
        response.put("reportDate", date.toString());

        try {
            String rawJson = (String) redisTemplate.opsForHash().get(hashKey, hashField);

            if (rawJson == null) {
                response.put("status", "NOT_STARTED");
                response.put("details", "No execution state found in database for this date.");
                return response;
            }

            JobExecutionState<CursorPayload> state = objectMapper.readValue(rawJson, stateTypeReference);

            response.put("status", state.status().name());
            response.put("schemaVersion", state.schemaVersion());

            if (state.errorMessage() != null) {
                response.put("errorMessage", state.errorMessage());
            }

            if (state.payload() != null) {
                response.put("lastCursor", state.payload().lastCursor());
            } else {
                response.put("lastCursor", null);
            }

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis is unreachable during Actuator dailyreport call.", e);
            response.put("status", "INFRASTRUCTURE_ERROR");
            response.put("details", "Redis storage is currently unavailable.");
        } catch (Exception e) {
            log.error("Failed to parse dailyreport job state for date {}", date, e);
            response.put("status", "CORRUPTED_DATA");
            response.put("details", "Failed to deserialize state JSON. Check schema versions.");
        }

        return response;
    }

    private Map<String, Object> buildErrorResponse(String inputDate, String error, String details) {
        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("inputDate", inputDate);
        errorResponse.put("error", error);
        errorResponse.put("details", details);
        return errorResponse;
    }
}