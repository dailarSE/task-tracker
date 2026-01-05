package com.example.tasktracker.scheduler.job;

import com.example.tasktracker.scheduler.job.dto.JobExecutionState;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Tags;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
@Slf4j
public class RedisJobStateRepository implements JobStateRepository {

    private final JobStateKeyProvider keyProvider;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsReporter metrics;

    public RedisJobStateRepository(StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   JobStateKeyProvider keyProvider,
                                   MetricsReporter metrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyProvider = keyProvider;
        this.metrics = metrics;
    }

    @Override
    public <T> Optional<JobExecutionState<T>> findState(
            @NonNull String jobName,
            @NonNull LocalDate date,
            @NonNull TypeReference<JobExecutionState<T>> typeReference) {
        final String hashKey = keyProvider.getHashKey(jobName);
        final String hashField = keyProvider.getHashField(date);
        try {
            String jsonState = (String) redisTemplate.opsForHash().get(hashKey, hashField);
            if (jsonState == null) {
                return Optional.empty();
            }

            // Логика миграции
            JsonNode rootNode = objectMapper.readTree(jsonState);
            int version = rootNode.has("schemaVersion") ? rootNode.get("schemaVersion").asInt() : 0;

            if (version < JobExecutionState.CURRENT_SCHEMA_VERSION) {
                log.warn("Old schema version {} detected for job '{}' on date {}. Attempting migration.", version, jobName, date);
                metrics.incrementCounter(Metric.JOB_STATE_MIGRATION_SUCCESS, Tags.of("job_name", jobName));
            }

            return Optional.of(objectMapper.treeToValue(rootNode, typeReference));

        } catch (JsonProcessingException e) {
            metrics.incrementCounter(Metric.JOB_STATE_DESERIALIZATION_ERROR, Tags.of("job_name", jobName));
            log.warn("Failed to parse JobExecutionState JSON from Redis. job: '{}', date: '{}'. Invalid JSON.", jobName, date, e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to read job state from Redis. job: '{}', date: '{}'.", jobName, date, e);
            throw new RuntimeException("Could not read job state from Redis", e);
        }
    }

    @Override
    public <T> void saveState(
            @NonNull String jobName,
            @NonNull LocalDate date,
            @NonNull JobExecutionState<T> state) {
        final String hashKey = keyProvider.getHashKey(jobName);
        final String hashField = keyProvider.getHashField(date);
        try {
            String jsonState = objectMapper.writeValueAsString(state);
            redisTemplate.opsForHash().put(hashKey, hashField, jsonState);
            log.debug("Saved job state to Redis. job: '{}', date: '{}', state: {}", jobName, date, state);
        } catch (JsonProcessingException e) {
            metrics.incrementCounter(Metric.JOB_STATE_SERIALIZATION_ERROR, Tags.of("job_name", jobName));
            log.error("CRITICAL: Failed to serialize JobExecutionState. Job: '{}', State: {}", jobName, state, e);
            throw new IllegalStateException("Failed to serialize job state for job: " + jobName, e);
        } catch (Exception e) {
            log.error("Failed to save job state to Redis for job '{}' on date {}.", jobName, date, e);
            throw new RuntimeException("Could not save job state to Redis", e);
        }
    }
}